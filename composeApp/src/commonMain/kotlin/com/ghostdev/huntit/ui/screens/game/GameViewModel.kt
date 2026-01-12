package com.ghostdev.huntit.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GamePhase
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.data.model.ProfileDto
import com.ghostdev.huntit.data.model.RoundSubmissionDto
import com.ghostdev.huntit.data.model.SubmissionStatus
import com.ghostdev.huntit.data.repository.GameRepository
import com.ghostdev.huntit.data.repository.SubmissionRepository
import com.ghostdev.huntit.data.repository.VerificationResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents the status of a round indicator
 */
enum class RoundIndicatorStatus {
    PENDING,    // Round hasn't happened yet (grey)
    SUCCESS,    // User successfully submitted (green)
    FAILED,     // User failed or skipped (red)
    CURRENT     // Current round (highlighted)
}

/**
 * Represents a round indicator in the UI
 */
data class RoundIndicator(
    val roundNumber: Int,
    val status: RoundIndicatorStatus
)

/**
 * Represents the current submission state
 */
sealed class SubmissionState {
    data object Idle : SubmissionState()
    data object TakingPhoto : SubmissionState()
    data object Uploading : SubmissionState()
    data object Verifying : SubmissionState()
    data class Success(val points: Int, val message: String) : SubmissionState()
    data class Failed(val reason: String) : SubmissionState()
    data class Error(val message: String) : SubmissionState()
}

/**
 * Represents a leaderboard entry for the game screen
 */
data class GameLeaderboardEntry(
    val participant: ParticipantUiModel,
    val points: Int,
    val rank: Int
)

/**
 * UI state for the game screen
 */
data class GameUiState @OptIn(ExperimentalTime::class) constructor(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Game room data
    val gameRoom: GameRoomDto? = null,
    val roomName: String = "",
    val theme: String = "",
    val currentRound: Int = 1,
    val totalRounds: Int = 5,
    val currentPhase: GamePhase = GamePhase.LOBBY,

    // Timer
    val timeRemainingMs: Long = 0,
    val phaseEndsAt: Instant? = null,

    // Challenge
    val currentChallenge: String = "",
    val isChallengeLoading: Boolean = false,

    // Participants & Leaderboard
    val leaderboard: List<GameLeaderboardEntry> = emptyList(),

    // Round indicators
    val roundIndicators: List<RoundIndicator> = emptyList(),

    // Submission state
    val submissionState: SubmissionState = SubmissionState.Idle,
    val canSubmit: Boolean = false,
    val hasSubmittedCurrentRound: Boolean = false,

    // User info
    val isHost: Boolean = false,
    val currentUserId: String = "",

    // Navigation flags
    val shouldNavigateToWinners: Boolean = false,
    val shouldNavigateToPhoto: Boolean = false
)

@OptIn(ExperimentalTime::class)
class GameViewModel(
    private val gameRepository: GameRepository,
    private val submissionRepository: SubmissionRepository,
    private val supabaseClient: SupabaseClient,
    private val roomCodeStorage: RoomCodeStorage
) : ViewModel() {

    companion object {
        // Buffer time to account for network latency when receiving phase updates
        // This compensates for the delay between server setting phase_ends_at and client receiving it
        private const val NETWORK_LATENCY_BUFFER_MS = 2000L // 2 seconds buffer
    }

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    // Timer job for countdown
    private var timerJob: Job? = null

    // Subscription jobs
    private var gameRoomSubscriptionJob: Job? = null
    private var participantsSubscriptionJob: Job? = null
    private var submissionsSubscriptionJob: Job? = null

    // Cache for profiles
    private val profilesCache = mutableMapOf<String, ProfileDto>()
    
    // Track when we received phase update to calculate actual elapsed time
    private var phaseUpdateReceivedAt: Instant? = null

    // Current room ID
    private var currentRoomId: String? = null

    // Store captured image bytes for submission
    private var capturedImageBytes: ByteArray? = null

    init {
        initializeGame()
    }

    fun initializeGame() {
        val roomCode = roomCodeStorage.getCurrentRoomCode()
        if (roomCode.isEmpty()) {
            _state.update { it.copy(isLoading = false, error = "No active game room") }
            return
        }

        viewModelScope.launch {
            loadGameData(roomCode)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun loadGameData(roomCode: String) {
        _state.update { it.copy(isLoading = true, error = null) }

        try {
            // Get current user ID
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId == null) {
                _state.update { it.copy(isLoading = false, error = "User not authenticated") }
                return
            }

            // Fetch game room details
            val roomResult = gameRepository.getGameRoomByCode(roomCode)
            if (roomResult.isFailure) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = roomResult.exceptionOrNull()?.message ?: "Failed to load game room"
                    )
                }
                return
            }

            val gameRoom = roomResult.getOrNull()!!
            val roomId = gameRoom.id ?: run {
                _state.update { it.copy(isLoading = false, error = "Invalid game room ID") }
                return
            }

            currentRoomId = roomId

            // Update state with initial game room data
            _state.update { currentState ->
                currentState.copy(
                    gameRoom = gameRoom,
                    roomName = gameRoom.roomName,
                    theme = gameRoom.theme.displayName,
                    currentRound = gameRoom.currentRound,
                    totalRounds = gameRoom.totalRounds,
                    currentPhase = gameRoom.currentPhase,
                    phaseEndsAt = gameRoom.phaseEndsAt,
                    isHost = gameRoom.hostId == userId,
                    currentUserId = userId
                )
            }

            // Load participants
            loadParticipants(roomId, userId)

            // Load user submissions for round indicators
            loadUserSubmissions(roomId, userId, gameRoom.totalRounds, gameRoom.currentRound)

            // Load current challenge if in active phase
            if (gameRoom.currentPhase == GamePhase.IN_PROGRESS && gameRoom.currentRound > 0) {
                loadCurrentChallenge(roomId, gameRoom.currentRound)
            }

            // Start timer if phase has an end time
            if (gameRoom.phaseEndsAt != null) {
                startTimer(gameRoom.phaseEndsAt)
            }

            // Subscribe to real-time updates
            subscribeToRealTimeUpdates(roomId)

            // Mark loading as complete and set initialization flag
            _state.update { it.copy(isLoading = false) }
            
            // Set the initialization flag AFTER all data is loaded
            // This prevents the winners navigation check from running prematurely
            hasCompletedInitialization = true
            println("Game initialization complete - safe to check game status now")

        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    private suspend fun loadParticipants(roomId: String, currentUserId: String) {
        val participantsResult = gameRepository.getParticipants(roomId)
        if (participantsResult.isSuccess) {
            val participants = participantsResult.getOrNull() ?: emptyList()
            val leaderboard = mapParticipantsToLeaderboard(participants)
            _state.update { it.copy(leaderboard = leaderboard) }
        }
    }

    private suspend fun loadUserSubmissions(
        roomId: String,
        userId: String,
        totalRounds: Int,
        currentRound: Int
    ) {
        val submissionsResult = gameRepository.getUserSubmissions(roomId, userId)
        val submissions = submissionsResult.getOrNull() ?: emptyList()

        val roundIndicators = createRoundIndicators(totalRounds, currentRound, submissions)
        val hasSubmittedCurrentRound = submissions.any {
            it.roundNumber == currentRound &&
                    (it.status == SubmissionStatus.SUCCESS || it.status == SubmissionStatus.SKIPPED)
        }

        val currentPhase = _state.value.currentPhase
        val canSubmit = currentPhase == GamePhase.IN_PROGRESS && !hasSubmittedCurrentRound

        _state.update {
            it.copy(
                roundIndicators = roundIndicators,
                hasSubmittedCurrentRound = hasSubmittedCurrentRound,
                canSubmit = canSubmit
            )
        }
    }

    private fun createRoundIndicators(
        totalRounds: Int,
        currentRound: Int,
        submissions: List<RoundSubmissionDto>
    ): List<RoundIndicator> {
        return (1..totalRounds).map { roundNumber ->
            val submission = submissions.find { it.roundNumber == roundNumber }
            val status = when {
                roundNumber == currentRound -> RoundIndicatorStatus.CURRENT
                submission != null -> {
                    when (submission.status) {
                        SubmissionStatus.SUCCESS -> RoundIndicatorStatus.SUCCESS
                        SubmissionStatus.FAILED, SubmissionStatus.SKIPPED -> RoundIndicatorStatus.FAILED
                        else -> RoundIndicatorStatus.PENDING
                    }
                }

                roundNumber < currentRound -> RoundIndicatorStatus.FAILED // Missed round
                else -> RoundIndicatorStatus.PENDING
            }
            RoundIndicator(roundNumber = roundNumber, status = status)
        }
    }

    private suspend fun loadCurrentChallenge(roomId: String, roundNumber: Int) {
        _state.update { it.copy(isChallengeLoading = true) }

        val challengeResult = gameRepository.getChallenge(roomId, roundNumber)
        if (challengeResult.isSuccess) {
            val challenge = challengeResult.getOrNull()
            _state.update {
                it.copy(
                    currentChallenge = challenge?.challengeText ?: "",
                    isChallengeLoading = false
                )
            }
        } else {
            _state.update { it.copy(isChallengeLoading = false) }
        }
    }

    /**
     * Public method to load a challenge and return the challenge text directly
     * This is useful when the challenge text is needed immediately (e.g., for photo verification)
     */
    suspend fun loadChallenge(roomId: String, roundNumber: Int): String? {
        val challengeResult = gameRepository.getChallenge(roomId, roundNumber)
        if (challengeResult.isSuccess) {
            val challenge = challengeResult.getOrNull()
            val challengeText = challenge?.challengeText

            // Also update state
            _state.update {
                it.copy(currentChallenge = challengeText ?: "")
            }

            return challengeText
        }
        return null
    }

    // Store local offset to avoid constant server time fetches
    private var localTimeOffsetMs: Long = 0
    private var lastTimerSyncInstant: Instant? = null

    @OptIn(ExperimentalTime::class)
    private fun startTimer(phaseEndsAt: Instant) {
        timerJob?.cancel()

        // Initial sync with server time
        viewModelScope.launch {
            syncWithServerTime(phaseEndsAt)

            // Start the smooth countdown
            startSmoothCountdown(phaseEndsAt)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun syncWithServerTime(phaseEndsAt: Instant) {
        try {
            // Get server time for accuracy
            val serverTimeResult = gameRepository.getServerTime()
            val serverTime = serverTimeResult.getOrNull() ?: Clock.System.now()

            // Calculate initial remaining time with network latency buffer compensation
            // The buffer accounts for the delay between server setting phase_ends_at 
            // and the client receiving & processing the update
            val remaining = phaseEndsAt - serverTime
            val remainingMs = (remaining.inWholeMilliseconds + NETWORK_LATENCY_BUFFER_MS).coerceAtLeast(0L)

            // Store the local time offset (difference between local clock and server clock)
            localTimeOffsetMs =
                Clock.System.now().toEpochMilliseconds() - serverTime.toEpochMilliseconds()

            // Update last sync time
            lastTimerSyncInstant = Clock.System.now()
            
            println("Timer sync: phaseEndsAt=$phaseEndsAt, serverTime=$serverTime, remaining=${remainingMs}ms (includes ${NETWORK_LATENCY_BUFFER_MS}ms buffer)")

            // Update the state with initial time
            _state.update { it.copy(timeRemainingMs = remainingMs) }

        } catch (e: Exception) {
            println("Timer sync error: ${e.message}")
            // If sync fails, use local time as fallback with buffer
            val remaining = phaseEndsAt - Clock.System.now()
            val remainingMs = (remaining.inWholeMilliseconds + NETWORK_LATENCY_BUFFER_MS).coerceAtLeast(0L)
            _state.update { it.copy(timeRemainingMs = remainingMs) }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun startSmoothCountdown(phaseEndsAt: Instant) {
        timerJob?.cancel() // Cancel any existing timer job
        
        // Calculate adjusted phase end time with buffer compensation
        // This ensures the timer shows the full duration expected by the user
        val adjustedPhaseEndsAt = Instant.fromEpochMilliseconds(
            phaseEndsAt.toEpochMilliseconds() + NETWORK_LATENCY_BUFFER_MS
        )
        
        timerJob = viewModelScope.launch {
            var timeReachedZero = false
            
            while (isActive) {
                try {
                    // Calculate time remaining using local clock with offset adjustment
                    val now = Clock.System.now()
                    val adjustedNow =
                        Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - localTimeOffsetMs)
                    val remaining = adjustedPhaseEndsAt - adjustedNow
                    val remainingMs = remaining.inWholeMilliseconds.coerceAtLeast(0L)

                    if (remainingMs <= 0) {
                        _state.update { it.copy(timeRemainingMs = 0) }
                        
                        // Only try to advance phase once when timer first reaches zero
                        if (!timeReachedZero) {
                            timeReachedZero = true
                            println("Timer reached zero, checking if phase needs to advance")
                            advanceGamePhase()
                            
                            // Continue running the timer job in case we need to wait for server sync
                            // but slow the polling rate to avoid unnecessary load
                            delay(5000) // Check every 5 seconds after timer reaches zero
                            
                            // Also check if game state has advanced on server
                            val roomId = currentRoomId ?: break
                            val freshGameRoomResult = gameRepository.getGameRoom(roomId)
                            if (freshGameRoomResult.isSuccess) {
                                val freshGameRoom = freshGameRoomResult.getOrNull()
                                if (freshGameRoom != null) {
                                    val currentGameRoom = _state.value.gameRoom
                                    if (currentGameRoom != null && 
                                        (freshGameRoom.currentRound > currentGameRoom.currentRound || 
                                         freshGameRoom.currentPhase != currentGameRoom.currentPhase)) {
                                        // Game state has changed on server, update local state
                                        handleGameRoomUpdate(freshGameRoom)
                                        break // Exit the timer loop
                                    }
                                }
                            }
                        } else {
                            // Keep checking for phase changes periodically but at a reduced rate
                            delay(5000) // Check every 5 seconds after timer reaches zero
                        }
                    } else {
                        // Reset the zero flag if timer goes back above zero
                        if (timeReachedZero && remainingMs > 0) {
                            timeReachedZero = false
                        }
                        
                        _state.update { it.copy(timeRemainingMs = remainingMs) }
                        delay(1000) // Update every second
                    }
                } catch (e: Exception) {
                    println("Timer error: ${e.message}")
                    delay(1000)
                }
            }
        }
    }

    private fun subscribeToRealTimeUpdates(roomId: String) {
        // Subscribe to game room updates
        gameRoomSubscriptionJob = viewModelScope.launch {
            gameRepository.subscribeToGameRoom(roomId)
                .catch { e -> println("Game room subscription error: ${e.message}") }
                .collect { gameRoom ->
                    handleGameRoomUpdate(gameRoom)
                }
        }

        // Subscribe to participants updates
        participantsSubscriptionJob = viewModelScope.launch {
            gameRepository.subscribeToParticipants(roomId)
                .catch { e -> println("Participants subscription error: ${e.message}") }
                .collect { participants ->
                    handleParticipantsUpdate(participants)
                }
        }

        // Subscribe to submissions updates
        submissionsSubscriptionJob = viewModelScope.launch {
            gameRepository.subscribeToSubmissions(roomId)
                .catch { e -> println("Submissions subscription error: ${e.message}") }
                .collect { submissions ->
                    handleSubmissionsUpdate(submissions)
                }
        }
    }

    // Track initialization state to prevent premature navigation to winners
    private var hasCompletedInitialization = false
    
    @OptIn(ExperimentalTime::class)
    private suspend fun handleGameRoomUpdate(gameRoom: GameRoomDto) {
        val previousPhase = _state.value.currentPhase
        val previousRound = _state.value.currentRound

        // Update state with new game room data
        _state.update { currentState ->
            currentState.copy(
                gameRoom = gameRoom,
                currentRound = gameRoom.currentRound,
                currentPhase = gameRoom.currentPhase,
                phaseEndsAt = gameRoom.phaseEndsAt
            )
        }

        // Only check game finished status after initial load is complete
        // This prevents navigation to winners screen right after game start
        // due to a race condition between initialization and real-time updates
        if (hasCompletedInitialization) {
            // Check if game is finished
            if (gameRoom.status == GameStatus.FINISHED || gameRoom.currentPhase == GamePhase.FINISHED) {
                println("Game detected as finished, navigating to winners screen")
                _state.update { it.copy(shouldNavigateToWinners = true) }
                return
            }
        } else {
            println("Skipping winners navigation check during initialization")
        }

        // If phase changed, handle the transition
        if (gameRoom.currentPhase != previousPhase || gameRoom.currentRound != previousRound) {
            handlePhaseChange(gameRoom)

            // New phase/round always requires timer restart with server sync
            if (gameRoom.phaseEndsAt != null) {
                startTimer(gameRoom.phaseEndsAt)
            }
        } else if (gameRoom.phaseEndsAt != _state.value.phaseEndsAt) {
            // Phase end time changed but phase/round didn't - just restart the timer
            // without resetting the server sync time
            if (gameRoom.phaseEndsAt != null) {
                startTimer(gameRoom.phaseEndsAt)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun handlePhaseChange(gameRoom: GameRoomDto) {
        val roomId = gameRoom.id ?: return
        val userId = _state.value.currentUserId

        when (gameRoom.currentPhase) {
            GamePhase.IN_PROGRESS -> {
                // Load challenge for the new round
                if (gameRoom.currentRound > 0) {
                    loadCurrentChallenge(roomId, gameRoom.currentRound)
                }
                // Reload user submissions to update round indicators
                loadUserSubmissions(roomId, userId, gameRoom.totalRounds, gameRoom.currentRound)
            }

            GamePhase.COOLDOWN -> {
                // Clear challenge during cooldown
                _state.update {
                    it.copy(
                        currentChallenge = "",
                        canSubmit = false
                    )
                }
            }

            GamePhase.FINISHED -> {
                _state.update { it.copy(shouldNavigateToWinners = true) }
            }

            else -> {}
        }
    }

    private suspend fun handleParticipantsUpdate(participants: List<GameParticipantDto>) {
        val leaderboard = mapParticipantsToLeaderboard(participants)
        
        // Make sure to update the leaderboard while preserving the canSubmit status
        val currentPhase = _state.value.currentPhase
        val hasSubmittedCurrentRound = _state.value.hasSubmittedCurrentRound
        val canSubmit = currentPhase == GamePhase.IN_PROGRESS && !hasSubmittedCurrentRound
        
        _state.update { it.copy(
            leaderboard = leaderboard,
            canSubmit = canSubmit
        )}
    }

    private suspend fun handleSubmissionsUpdate(submissions: List<RoundSubmissionDto>) {
        val userId = _state.value.currentUserId
        val currentRound = _state.value.currentRound
        val totalRounds = _state.value.totalRounds

        // Filter submissions for current user
        val userSubmissions = submissions.filter { it.userId == userId }

        val roundIndicators = createRoundIndicators(totalRounds, currentRound, userSubmissions)
        val hasSubmittedCurrentRound = userSubmissions.any {
            it.roundNumber == currentRound &&
                    (it.status == SubmissionStatus.SUCCESS || it.status == SubmissionStatus.SKIPPED)
        }

        val currentPhase = _state.value.currentPhase
        val canSubmit = currentPhase == GamePhase.IN_PROGRESS && !hasSubmittedCurrentRound

        _state.update {
            it.copy(
                roundIndicators = roundIndicators,
                hasSubmittedCurrentRound = hasSubmittedCurrentRound,
                canSubmit = canSubmit
            )
        }
    }

    private suspend fun mapParticipantsToLeaderboard(
        participants: List<GameParticipantDto>
    ): List<GameLeaderboardEntry> {
        val userIds = participants.map { it.userId }

        // Fetch profiles for users not in cache
        val uncachedUserIds = userIds.filter { it !in profilesCache }
        if (uncachedUserIds.isNotEmpty()) {
            try {
                val profiles = supabaseClient.postgrest["profiles"]
                    .select(columns = Columns.ALL) {
                        filter { isIn("id", uncachedUserIds) }
                    }
                    .decodeList<ProfileDto>()

                profiles.forEach { profile ->
                    profilesCache[profile.id] = profile
                }
            } catch (e: Exception) {
                println("Error fetching profiles: ${e.message}")
            }
        }

        // Sort by score descending and create leaderboard entries
        return participants
            .sortedByDescending { it.currentScore }
            .mapIndexed { index, participant ->
                val profile = profilesCache[participant.userId]
                GameLeaderboardEntry(
                    participant = ParticipantUiModel(
                        id = participant.userId,
                        name = profile?.displayName ?: "Unknown",
                        avatarId = profile?.avatarId ?: 1,
                        isHost = participant.isHost,
                        joinOrder = participant.joinOrder
                    ),
                    points = participant.currentScore,
                    rank = index + 1
                )
            }
    }

    // ============ Lifecycle Events ============

    /**
     * Add a specialized method to force refresh all game data including scores
     * This is more aggressive than just handleGameRoomUpdate and ensures all data is fresh
     */
    private suspend fun forceRefreshAllGameData(roomId: String) {
        println("Force refreshing ALL game data including leaderboard and scores")
        
        try {
            // 1. Get fresh game room data first
            val gameRoomResult = gameRepository.getGameRoom(roomId)
            if (gameRoomResult.isSuccess) {
                val freshGameRoom = gameRoomResult.getOrNull()
                if (freshGameRoom != null) {
                    println("Got fresh game room data - round: ${freshGameRoom.currentRound}, phase: ${freshGameRoom.currentPhase}")
                    
                    // 2. Update game room state first
                    handleGameRoomUpdate(freshGameRoom)
                    
                    // 3. Force refresh participants data (leaderboard and scores)
                    println("Force refreshing participants and scores")
                    val participantsResult = gameRepository.getParticipants(roomId)
                    if (participantsResult.isSuccess) {
                        val participants = participantsResult.getOrNull() ?: emptyList()
                        println("Got ${participants.size} participants with updated scores")
                        
                        // Map to leaderboard and update state
                        val leaderboard = mapParticipantsToLeaderboard(participants)
                        _state.update { 
                            it.copy(
                                leaderboard = leaderboard
                            )
                        }
                        
                        // Print scores for debugging
                        leaderboard.forEach { entry ->
                            println("Participant: ${entry.participant.name}, Score: ${entry.points}")
                        }
                    } else {
                        println("Failed to refresh participants: ${participantsResult.exceptionOrNull()?.message}")
                    }
                    
                    // 4. Refresh user submissions
                    val userId = _state.value.currentUserId
                    loadUserSubmissions(roomId, userId, freshGameRoom.totalRounds, freshGameRoom.currentRound)
                    
                    // 5. Refresh challenge data if needed
                    if (freshGameRoom.currentPhase == GamePhase.IN_PROGRESS) {
                        loadCurrentChallenge(roomId, freshGameRoom.currentRound)
                    }
                    
                    // 6. Update timer state
                    val phaseEndsAt = freshGameRoom.phaseEndsAt
                    if (phaseEndsAt != null) {
                        syncWithServerTime(phaseEndsAt)
                        startSmoothCountdown(phaseEndsAt)
                    }
                    
                    println("Completed full game data refresh")
                } else {
                    println("Failed to refresh - game room data is null")
                }
            } else {
                println("Failed to refresh game data: ${gameRoomResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            println("Exception during game data refresh: ${e.message}")
        }
    }

    /**
     * Handle app going to background/foreground
     * Call this when app is paused/resumed
     */
    @OptIn(ExperimentalTime::class)
    fun handleAppLifecycleEvent(isInForeground: Boolean) {
        if (isInForeground) {
            // App came to foreground - sync with server and check for round advancement
            viewModelScope.launch {
                // Get the current room ID
                val roomId = currentRoomId ?: return@launch
                
                println("App returned to foreground - Performing complete data refresh...")
                
                // Use our new method to force refresh ALL game data
                forceRefreshAllGameData(roomId)
                
                // If the timer is at 0:00, check if we need to force an advance
                if (_state.value.timeRemainingMs <= 0 && _state.value.phaseEndsAt == null) {
                    println("Timer at zero after refresh, attempting to advance game phase")
                    advanceGamePhase()
                    
                    // Wait briefly and refresh again to catch the change
                    delay(500)
                    forceRefreshAllGameData(roomId)
                }
            }
        } else {
            // App went to background
            println("App went to background - saving current state")
            // Record the time we went to background to improve syncing when we return
            viewModelScope.launch {
                // Nothing to do for now, but in the future we could suspend subscriptions
                // or perform other optimizations
            }
        }
    }

    /**
     * Handle user navigating away from the screen
     * Call this when user leaves the game screen
     */
    fun handleUserLeftScreen() {
        // Cancel the timer job - we'll restart it when user comes back
        timerJob?.cancel()
        
        // Reset initialization flag when leaving screen
        // This ensures proper initialization check when returning
        hasCompletedInitialization = false
        println("Reset game initialization state on screen exit")
    }

    /**
     * Handle user coming back to the screen
     * Call this when user navigates back to the game screen
     */
    @OptIn(ExperimentalTime::class)
    fun handleUserReturnedToScreen() {
        println("User returned to GameScreen - performing full data refresh")
        
        viewModelScope.launch {
            try {
                // Get the current room ID
                val roomId = currentRoomId
                if (roomId == null) {
                    println("User returned to screen but roomId is null")
                    return@launch
                }
                
                // Use our comprehensive refresh method to update all game data including scores
                forceRefreshAllGameData(roomId)
                
                // If timer is at zero and there's no phase end time, might be stuck between phases
                if (_state.value.timeRemainingMs <= 0 && _state.value.phaseEndsAt == null) {
                    println("Timer at zero and no phase end time - may be stuck between phases")
                    
                    // Try to advance the game
                    advanceGamePhase()
                    
                    // Wait briefly and refresh again to catch the change
                    delay(500)
                    forceRefreshAllGameData(roomId)
                }
                
                // Set initialization flag after returning to screen
                hasCompletedInitialization = true
                println("Game initialization complete after returning to screen")
            } catch (e: Exception) {
                println("Exception in handleUserReturnedToScreen: ${e.message}")
            }
        }
    }

    // ============ Public Actions ============

    /**
     * Advance the game to the next phase (HOST only)
     */
    fun advanceGamePhase() {
        val roomId = currentRoomId ?: return

        viewModelScope.launch {
            val result = gameRepository.advanceGamePhase(roomId)
            if (result.isFailure) {
                println("Failed to advance phase: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Navigate to photo screen to take a photo
     */
    fun onSubmitClick() {
        println("onSubmitClick called - canSubmit: ${_state.value.canSubmit}, phase: ${_state.value.currentPhase}, hasSubmitted: ${_state.value.hasSubmittedCurrentRound}")
        
        // Force recalculate canSubmit to ensure it's current
        val currentPhase = _state.value.currentPhase
        val hasSubmittedCurrentRound = _state.value.hasSubmittedCurrentRound
        val calculatedCanSubmit = currentPhase == GamePhase.IN_PROGRESS && !hasSubmittedCurrentRound
        
        // Update state if canSubmit is incorrect
        if (_state.value.canSubmit != calculatedCanSubmit) {
            println("Submit warning: canSubmit was inconsistent, updating from ${_state.value.canSubmit} to $calculatedCanSubmit")
            _state.update { it.copy(canSubmit = calculatedCanSubmit) }
        }
        
        if (!calculatedCanSubmit) {
            println("Submit blocked: canSubmit is false (phase: $currentPhase, hasSubmitted: $hasSubmittedCurrentRound)")
            return
        }
        
        // Get current game state info
        val roomId = currentRoomId
        if (roomId == null) {
            println("Submit blocked: roomId is null")
            return
        }
        
        // Update state before retrieving other values to avoid race conditions
        _state.update {
            it.copy(
                submissionState = SubmissionState.TakingPhoto,
                shouldNavigateToPhoto = true
            )
        }
        println("GameViewModel: Set shouldNavigateToPhoto = true")
        
        val userId = _state.value.currentUserId
        val currentRound = _state.value.currentRound
        val currentChallenge = _state.value.currentChallenge
        val theme = _state.value.theme
        val timeRemainingMs = _state.value.timeRemainingMs
        val phaseEndsAt = _state.value.phaseEndsAt?.toEpochMilliseconds() ?: 0L
        
        // Use the actual time remaining without enforcing a minimum
        val timeFormatted = formatTimeRemaining(timeRemainingMs)
        
        println("Initializing submission with actual time: $timeFormatted (${timeRemainingMs}ms)")
        
        // Initialize the submission ViewModel - we'll do this through dependency injection in the UI layer
        try {
            val submissionViewModel = org.koin.mp.KoinPlatformTools.defaultContext().get().get<SubmissionViewModel>()
            
            // Initialize submission with all required data
            submissionViewModel.initialize(
                roomId = roomId,
                userId = userId,
                roundNumber = currentRound,
                challenge = currentChallenge,
                theme = theme,
                timeRemaining = timeFormatted,
                phaseEndsAtMs = phaseEndsAt
            )
        } catch (e: Exception) {
            println("Failed to initialize SubmissionViewModel: ${e.message}")
        }
        
        // Update state
        _state.update {
            it.copy(
                submissionState = SubmissionState.TakingPhoto,
                shouldNavigateToPhoto = true
            )
        }
    }

    /**
     * Called when photo navigation is handled
     */
    fun onPhotoNavigationHandled() {
        _state.update { it.copy(shouldNavigateToPhoto = false) }
    }

    /**
     * Store captured image bytes for later submission
     */
    fun setCapturedImage(imageBytes: ByteArray) {
        capturedImageBytes = imageBytes
    }

    /**
     * Get the captured image bytes
     */
    fun getCapturedImageBytes(): ByteArray? = capturedImageBytes

    /**
     * Submit the captured photo
     */
    fun submitPhoto() {
        val imageBytes = capturedImageBytes
        if (imageBytes == null) {
            _state.update {
                it.copy(submissionState = SubmissionState.Error("No photo captured"))
            }
            return
        }

        val roomId = currentRoomId ?: return
        val userId = _state.value.currentUserId
        val currentRound = _state.value.currentRound
        val challengeText = _state.value.currentChallenge
        val theme = _state.value.theme

        viewModelScope.launch {
            try {
                // Step 1: Upload image
                _state.update { it.copy(submissionState = SubmissionState.Uploading) }

                val uploadResult = submissionRepository.uploadImage(
                    userId = userId,
                    roomId = roomId,
                    roundNumber = currentRound,
                    imageBytes = imageBytes
                )

                if (uploadResult.isFailure) {
                    _state.update {
                        it.copy(
                            submissionState = SubmissionState.Error(
                                uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                            )
                        )
                    }
                    return@launch
                }

                val imageUrl = uploadResult.getOrNull()!!

                // Step 2: Verify photo
                _state.update { it.copy(submissionState = SubmissionState.Verifying) }

                // Convert image to base64 for verification
                val imageBase64 = imageBytes.encodeToBase64()

                val verifyResult = submissionRepository.verifyPhoto(
                    imageBase64 = imageBase64,
                    challengeText = challengeText,
                    theme = theme
                )

                val verification = verifyResult.getOrNull() ?: VerificationResult(
                    isValid = false,
                    reason = "Verification failed",
                    confidence = 0f
                )

                // Step 3: Submit round result
                val submitResult = submissionRepository.submitRound(
                    roomId = roomId,
                    userId = userId,
                    roundNumber = currentRound,
                    imageUrl = imageUrl,
                    isSuccess = verification.isValid
                )

                if (submitResult.isSuccess) {
                    if (verification.isValid) {
                        _state.update {
                            it.copy(
                                submissionState = SubmissionState.Success(
                                    points = 2,
                                    message = "Photo matches the challenge!"
                                ),
                                hasSubmittedCurrentRound = true,
                                canSubmit = false
                            )
                        }
                        
                        // Force refresh participants data to update scores immediately after successful submission
                        println("Submission successful - refreshing scores")
                        delay(500) // Give server time to process
                        loadParticipants(roomId, userId)
                        
                    } else {
                        _state.update {
                            it.copy(
                                submissionState = SubmissionState.Failed(
                                    reason = verification.reason
                                )
                            )
                        }
                        
                        // Even with failed verification, refresh scores
                        delay(500)
                        loadParticipants(roomId, userId)
                    }
                } else {
                    _state.update {
                        it.copy(
                            submissionState = SubmissionState.Error(
                                submitResult.exceptionOrNull()?.message ?: "Submission failed"
                            )
                        )
                    }
                }

                // Clear captured image
                capturedImageBytes = null

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        submissionState = SubmissionState.Error(
                            e.message ?: "An unexpected error occurred"
                        )
                    )
                }
            }
        }
    }

    /**
     * Skip the current round
     */
    fun skipRound() {
        val roomId = currentRoomId ?: return
        val userId = _state.value.currentUserId
        val currentRound = _state.value.currentRound

        viewModelScope.launch {
            val result = submissionRepository.skipRound(
                roomId = roomId,
                userId = userId,
                roundNumber = currentRound
            )

            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        submissionState = SubmissionState.Idle,
                        hasSubmittedCurrentRound = true,
                        canSubmit = false
                    )
                }
                
                // Force refresh participants data to update scores after skip
                println("Round skipped - refreshing scores")
                delay(500) // Give server time to process
                loadParticipants(roomId, userId)
                
            } else {
                _state.update {
                    it.copy(
                        submissionState = SubmissionState.Error(
                            result.exceptionOrNull()?.message ?: "Skip failed"
                        )
                    )
                }
            }
        }
    }

    /**
     * Reset submission state to idle
     */
    fun resetSubmissionState() {
        _state.update { it.copy(submissionState = SubmissionState.Idle) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Handle navigation to winners screen completed
     */
    fun onWinnersNavigationHandled() {
        _state.update { it.copy(shouldNavigateToWinners = false) }
    }

    /**
     * Get the current room code
     */
    fun getCurrentRoomCode(): String = roomCodeStorage.getCurrentRoomCode()
    
    /**
     * Get the current user ID
     */
    fun getCurrentUserId(): String = _state.value.currentUserId

    /**
     * Format time remaining for display (MM:SS)
     */
    fun formatTimeRemaining(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minutesStr = if (minutes < 10) "0$minutes" else "$minutes"
        val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
        return "$minutesStr:$secondsStr"
    }

    /**
     * Check if timer should show warning color (< 60 seconds)
     */
    fun isTimerWarning(timeMs: Long): TimerStatus {
        return if (timeMs <= 60_000) {
            if (_state.value.currentPhase == GamePhase.COOLDOWN) {
                TimerStatus.COOLDOWN
            } else {
                TimerStatus.WARNING
            }
        } else {
            TimerStatus.NORMAL
        }
    }



    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        gameRoomSubscriptionJob?.cancel()
        participantsSubscriptionJob?.cancel()
        submissionsSubscriptionJob?.cancel()
        
        // Reset initialization flag
        hasCompletedInitialization = false
        println("Reset game initialization state in onCleared")

        viewModelScope.launch {
            gameRepository.unsubscribeFromGame()
        }
    }
}

enum class TimerStatus {
    NORMAL,
    WARNING,
    COOLDOWN
}

// Extension function to encode ByteArray to Base64
private fun ByteArray.encodeToBase64(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0

        result.append(chars[b0 shr 2])
        result.append(chars[(b0 and 0x03 shl 4) or (b1 shr 4)])
        result.append(if (i + 1 < size) chars[(b1 and 0x0F shl 2) or (b2 shr 6)] else '=')
        result.append(if (i + 2 < size) chars[b2 and 0x3F] else '=')

        i += 3
    }
    return result.toString()
}
