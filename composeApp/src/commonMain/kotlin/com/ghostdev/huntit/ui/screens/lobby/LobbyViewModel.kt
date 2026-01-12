package com.ghostdev.huntit.ui.screens.lobby

import androidx.compose.ui.platform.Clipboard
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GamePhase
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.data.model.ProfileDto
import com.ghostdev.huntit.data.repository.GameRepository
import com.ghostdev.huntit.data.repository.GameSetupRepository
import com.ghostdev.huntit.utils.toClipEntry
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LobbyState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val gameRoomDetails: GameRoomDto? = null,
    val participants: List<ParticipantUiModel> = emptyList(),
    val isHost: Boolean = false,
    val wasKicked: Boolean = false,
    val showKickedDialog: Boolean = false,
    val isStartingGame: Boolean = false,
    val gameStarted: Boolean = false,
    val voluntaryLeave: Boolean = false // Flag to indicate user voluntarily left
)

class LobbyViewModel(
    private val gameSetupRepository: GameSetupRepository,
    private val gameRepository: GameRepository,
    private val supabaseClient: SupabaseClient,
    private val roomCodeStorage: RoomCodeStorage
) : ViewModel() {

    private val _state = MutableStateFlow(LobbyState(isLoading = true))
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    // Jobs for managing real-time subscriptions and periodic checks
    private var participantsSubscriptionJob: Job? = null
    private var gameRoomSubscriptionJob: Job? = null
    private var participantCheckJob: Job? = null
    private var participantsRefreshJob: Job? = null

    // Cache for profiles to avoid re-fetching
    private val profilesCache = mutableMapOf<String, ProfileDto>()

    // Keep track of current user ID
    private val currentUserId = supabaseClient.auth.currentUserOrNull()?.id

    init {
        // Get the room code from storage and fetch game details automatically
        val roomCode = roomCodeStorage.getCurrentRoomCode()
        if (roomCode.isNotEmpty()) {
            _state.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                initializeLobby(roomCode)
            }
        }
    }

    /**
     * Initialize the lobby by fetching initial data and subscribing to real-time updates
     */
    private suspend fun initializeLobby(roomCode: String) {
        try {
            // Fetch game room details first
            val roomResult = gameSetupRepository.fetchGameRoomDetails(roomCode)

            roomResult.fold(
                onSuccess = { gameRoom ->
                    val roomId = gameRoom.id ?: run {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Game room ID is missing"
                            )
                        }
                        return
                    }

                    // Update state with game room details
                    _state.update {
                        it.copy(
                            gameRoomDetails = gameRoom,
                            isHost = supabaseClient.auth.currentUserOrNull()?.id == gameRoom.hostId
                        )
                    }

                    // Check if user is a valid participant before proceeding
                    val isParticipant = checkIfStillParticipant(roomId)
                    if (!isParticipant) {
                        _state.update {
                            it.copy(
                                wasKicked = true,
                                showKickedDialog = true,
                                isLoading = false
                            )
                        }
                        return
                    }

                    // Subscribe to real-time updates
                    subscribeToRealTimeUpdates(roomId)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to fetch room details"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Directly check if the current user is still a participant in the game
     */
    private suspend fun checkIfStillParticipant(roomId: String): Boolean {
        if (currentUserId == null) return false

        try {
            println("Directly checking if user $currentUserId is still a participant in room $roomId")
            val participants = supabaseClient.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                        eq("user_id", currentUserId)
                    }
                }
                .decodeList<GameParticipantDto>()

            val isParticipant = participants.isNotEmpty()
            println("Direct check result: User is${if (!isParticipant) " not" else ""} a participant")
            return isParticipant
        } catch (e: Exception) {
            println("Error checking participant status: ${e.message}")
            // In case of error, assume user is still a participant to avoid false kicks
            return true
        }
    }

    /**
     * Subscribe to real-time updates for participants and game room
     */
    private fun subscribeToRealTimeUpdates(roomId: String) {
        // Subscribe to participants changes
        participantsSubscriptionJob = viewModelScope.launch {
            gameSetupRepository.subscribeToParticipants(roomId)
                .catch { e ->
                    println("Error in participants subscription: ${e.message}")
                }
                .collect { participantDtos ->
                    // Important: First check if we need to show the kicked dialog
                    val currentUserStillParticipant =
                        participantDtos.any { it.userId == currentUserId }
                    val wasInGameBefore = _state.value.participants.any { it.id == currentUserId }

                    println("Participants update received: ${participantDtos.size} participants")
                    println("Current user $currentUserId still participant: $currentUserStillParticipant")
                    println("Was in game before: $wasInGameBefore")

                    if (!currentUserStillParticipant && currentUserId != null && wasInGameBefore) {
                        // Check if this was a voluntary leave or if the user was kicked
                        if (!_state.value.voluntaryLeave) {
                            // The current user was removed from the game by the host
                            println("*** User $currentUserId was kicked, showing dialog ***")
                            _state.update {
                                it.copy(
                                    wasKicked = true,
                                    showKickedDialog = true,
                                    // Still update the participants list to reflect actual state
                                    participants = emptyList()
                                )
                            }
                        } else {
                            println("*** User $currentUserId voluntarily left the game ***")
                        }
                    } else {
                        // Map DTOs to UI models with profile info
                        val participantUiModels = mapParticipantsToUiModels(participantDtos)
                        println("Updated participant list UI: ${participantUiModels.map { it.id }}")

                        _state.update {
                            it.copy(
                                isLoading = false,
                                participants = participantUiModels
                            )
                        }
                    }
                }
        }

        // Subscribe to game room changes
        gameRoomSubscriptionJob = viewModelScope.launch {
            gameSetupRepository.subscribeToGameRoom(roomId)
                .catch { e ->
                    println("Error in game room subscription: ${e.message}")
                }
                .collect { gameRoom ->
                    // Check if game status changed to IN_PROGRESS
                    if (gameRoom.status == GameStatus.IN_PROGRESS || gameRoom.currentPhase == GamePhase.IN_PROGRESS) {
                        println("Game has started! Notifying UI to navigate to game screen")
                        _state.update {
                            it.copy(
                                gameRoomDetails = gameRoom,
                                isHost = supabaseClient.auth.currentUserOrNull()?.id == gameRoom.hostId,
                                gameStarted = true
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                gameRoomDetails = gameRoom,
                                isHost = supabaseClient.auth.currentUserOrNull()?.id == gameRoom.hostId
                            )
                        }
                    }
                }
        }

        // Start a periodic check job to verify participant status directly
        // This acts as a fallback in case real-time events aren't working properly
        participantCheckJob = viewModelScope.launch {
            while (true) {
                delay(5.seconds)
                if (currentUserId != null) {
                    // Don't check if we're already kicked
                    if (!_state.value.wasKicked) {
                        val isStillParticipant = checkIfStillParticipant(roomId)
                        if (!isStillParticipant && !_state.value.voluntaryLeave) {
                            println("*** PERIODIC CHECK: User $currentUserId is no longer a participant, showing dialog ***")
                            _state.update {
                                it.copy(
                                    wasKicked = true,
                                    showKickedDialog = true,
                                    participants = emptyList()
                                )
                            }
                            break // Stop the periodic check once kicked
                        }
                    } else {
                        break // Already kicked, no need to keep checking
                    }
                }
            }
        }
        
        // Start a periodic refresh job for all participants
        // This ensures all users (including non-hosts) get participant updates
        // as a fallback when realtime DELETE events don't propagate properly
        participantsRefreshJob = viewModelScope.launch {
            while (true) {
                delay(3.seconds) // Refresh every 3 seconds
                if (!_state.value.wasKicked) {
                    try {
                        val updatedParticipants = fetchParticipants(roomId)
                        val currentParticipants = _state.value.participants
                        
                        // Only update if there's a change to avoid unnecessary recomposition
                        if (updatedParticipants.map { it.id }.toSet() != currentParticipants.map { it.id }.toSet()) {
                            println("Periodic refresh detected participant change: ${currentParticipants.size} -> ${updatedParticipants.size}")
                            _state.update {
                                it.copy(participants = updatedParticipants)
                            }
                        }
                    } catch (e: Exception) {
                        println("Error during periodic participant refresh: ${e.message}")
                    }
                } else {
                    break // Stop refreshing if kicked
                }
            }
        }
    }

    /**
     * Map participant DTOs to UI models by fetching profile information
     */
    private suspend fun mapParticipantsToUiModels(
        participantDtos: List<GameParticipantDto>
    ): List<ParticipantUiModel> {
        val userIds = participantDtos.map { it.userId }

        // Fetch profiles for users not in cache
        val uncachedUserIds = userIds.filter { it !in profilesCache }
        if (uncachedUserIds.isNotEmpty()) {
            try {
                val newProfiles = supabaseClient.postgrest["profiles"]
                    .select(columns = Columns.ALL) {
                        filter {
                            isIn("id", uncachedUserIds)
                        }
                    }
                    .decodeList<ProfileDto>()

                // Add to cache
                newProfiles.forEach { profile ->
                    profilesCache[profile.id] = profile
                }
            } catch (e: Exception) {
                println("Error fetching profiles: ${e.message}")
            }
        }

        // Map DTOs to UI models using cached profiles
        return participantDtos.map { participantDto ->
            val profile = profilesCache[participantDto.userId]
            ParticipantUiModel(
                id = participantDto.userId,
                name = profile?.displayName ?: "Unknown Player",
                avatarId = profile?.avatarId ?: 1,
                isHost = participantDto.isHost,
                joinOrder = participantDto.joinOrder
            )
        }.sortedBy { it.joinOrder }
    }

    fun fetchGameDetails(roomCode: String) {
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Fetch game room details
                val roomResult = gameSetupRepository.fetchGameRoomDetails(roomCode)

                roomResult.fold(
                    onSuccess = { gameRoom ->
                        // Room ID is required to fetch participants
                        val roomId = gameRoom.id ?: run {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Game room ID is missing"
                                )
                            }
                            return@fold
                        }

                        // Fetch participants from the game_participants table
                        try {
                            val participants = fetchParticipants(roomId)

                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    gameRoomDetails = gameRoom,
                                    participants = participants,
                                    // Set user as host if they are the room host
                                    isHost = supabaseClient.auth.currentUserOrNull()?.id == gameRoom.hostId
                                )
                            }
                        } catch (e: Exception) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Error fetching participants: ${e.message}"
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to fetch room details"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    private suspend fun fetchParticipants(roomId: String): List<ParticipantUiModel> {
        // Fetch participants from game_participants table
        val participantDtos = supabaseClient.postgrest["game_participants"]
            .select(columns = Columns.ALL) {
                filter {
                    eq("room_id", roomId)
                }
            }
            .decodeList<GameParticipantDto>()

        // Fetch user profiles to get names
        val userIds = participantDtos.map { it.userId }
        val profiles = if (userIds.isNotEmpty()) {
            // For simplicity, let's just fetch all profiles and filter client-side
            // In a production app, you might want to implement a more efficient query
            val allProfiles = supabaseClient.postgrest["profiles"]
                .select(columns = Columns.ALL)
                .decodeList<ProfileDto>()

            allProfiles.filter { it.id in userIds }
        } else {
            emptyList()
        }

        // Map the DTOs to UI models
        return participantDtos.map { participantDto ->
            val profile = profiles.find { it.id == participantDto.userId }
            ParticipantUiModel(
                id = participantDto.userId,
                name = profile?.displayName ?: "Unknown Player",
                avatarId = profile?.avatarId ?: 1,
                isHost = participantDto.isHost,
                joinOrder = participantDto.joinOrder
            )
        }.sortedBy { it.joinOrder }
    }

    fun copyRoomCode(
        clipboard: Clipboard
    ) {
        viewModelScope.launch {
            clipboard.setClipEntry("Join my Hunt.it game with this code: ${getCurrentRoomCode()}".toClipEntry())
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun getCurrentRoomCode(): String {
        return roomCodeStorage.getCurrentRoomCode()
    }

    fun clearRoomCode() {
        roomCodeStorage.clearRoomCode()
    }

    fun getCurrentUserId(): String? {
        return currentUserId
    }

    fun removeParticipant(participantId: String) {
        viewModelScope.launch {
            val roomId = _state.value.gameRoomDetails?.id ?: return@launch

            println("Removing participant $participantId from room $roomId")
            gameSetupRepository.removeParticipant(roomId, participantId).fold(
                onSuccess = {
                    println("Successfully removed participant $participantId from database")
                    // Optimistically update the UI while waiting for real-time subscription
                    val updatedParticipants =
                        _state.value.participants.filter { it.id != participantId }
                    _state.update {
                        it.copy(participants = updatedParticipants)
                    }
                    // The real-time subscription should eventually update with the correct state
                },
                onFailure = { error ->
                    println("Failed to remove participant: ${error.message}")
                    _state.update {
                        it.copy(error = "Failed to remove participant: ${error.message}")
                    }
                }
            )
        }
    }

    fun dismissKickedDialog() {
        _state.update {
            it.copy(showKickedDialog = false)
        }
    }

    fun isCurrentUserHost(): Boolean {
        return _state.value.isHost
    }

    /**
     * Start the game (HOST only)
     * This calls the backend to generate challenges and start the first phase
     */
    fun startGame() {
        if (!_state.value.isHost) return

        val roomId = _state.value.gameRoomDetails?.id ?: return

        _state.update { it.copy(isStartingGame = true, error = null) }

        viewModelScope.launch {
            val result = gameRepository.startGame(roomId)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isStartingGame = false,
                            gameStarted = true
                        )
                    }
                },
                onFailure = { error ->
                    // Provide a more user-friendly error message
                    val errorMessage = when {
                        error.message?.contains("2 players") == true || 
                        error.message?.contains("At least 2") == true -> 
                            "At least 2 players are needed to start a game. Share the room code to invite more players!"
                            
                        error.message?.contains("duplicate key") == true ->
                            "Game challenges already exist. Please restart the app and try again."
                            
                        error.message?.contains("not authenticated") == true ->
                            "Authentication error. Please try logging in again."
                            
                        else -> "Failed to start game: ${error.message ?: "Unknown error"}"
                    }
                    
                    _state.update {
                        it.copy(
                            isStartingGame = false,
                            error = errorMessage
                        )
                    }
                }
            )
        }
    }

    /**
     * Reset the game started flag after navigation is handled
     */
    fun onGameStartedHandled() {
        _state.update { it.copy(gameStarted = false) }
    }
    
    /**
     * Allow a non-host user to leave the game room
     */
    fun leaveGameRoom() {
        val currentUserId = getCurrentUserId() ?: return
        val roomId = _state.value.gameRoomDetails?.id ?: return
        
        // Mark this as a voluntary leave
        _state.update { it.copy(voluntaryLeave = true) }
        
        viewModelScope.launch {
            gameSetupRepository.removeParticipant(roomId, currentUserId).fold(
                onSuccess = {
                    println("Successfully left game room $roomId")
                    // Clear subscriptions
                    participantsSubscriptionJob?.cancel()
                    gameRoomSubscriptionJob?.cancel()
                    participantCheckJob?.cancel()
                    participantsRefreshJob?.cancel()
                    
                    // Unsubscribe from Supabase real-time channel
                    gameSetupRepository.unsubscribeFromLobby()
                },
                onFailure = { error ->
                    println("Failed to leave game room: ${error.message}")
                    _state.update {
                        it.copy(error = "Failed to leave game: ${error.message}")
                    }
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel subscription jobs
        participantsSubscriptionJob?.cancel()
        gameRoomSubscriptionJob?.cancel()
        participantCheckJob?.cancel()
        participantsRefreshJob?.cancel()

        // Unsubscribe from Supabase real-time channel
        viewModelScope.launch {
            gameSetupRepository.unsubscribeFromLobby()
        }
    }
}