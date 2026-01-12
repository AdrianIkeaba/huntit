package com.ghostdev.huntit.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.model.GameChallengeDto
import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.model.RoundSubmissionDto
import com.ghostdev.huntit.data.repository.LeaderboardRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

data class PastGamesUiState(
    val isLoading: Boolean = false,
    val games: List<GameHistory> = emptyList(),
    val selectedGameId: String? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val challenges: List<RoundChallenge> = emptyList(),
    val isLoadingLeaderboard: Boolean = false,
    val errorMessage: String? = null
)

data class RoundChallenge(
    val roundNumber: Int,
    val challengeText: String
)

data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val score: Int,
    val position: Int,
    val avatarId: Int
)

class PastGamesViewModel(
    private val supabaseClient: SupabaseClient,
    private val leaderboardRepository: LeaderboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PastGamesUiState(isLoading = true))
    val uiState: StateFlow<PastGamesUiState> = _uiState.asStateFlow()

    init {
        loadGameHistory()
    }

    @OptIn(ExperimentalTime::class)
    private fun loadGameHistory() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // Get current user ID
                val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
                if (currentUserId == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "You need to be logged in to view past games"
                        )
                    }
                    return@launch
                }

                // Get all game rooms where user participated
                val participants = supabaseClient.postgrest["game_participants"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("user_id", currentUserId)
                        }
                    }
                    .decodeList<GameParticipantDto>()

                if (participants.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            games = emptyList()
                        )
                    }
                    return@launch
                }

                // Get room IDs from participants
                val roomIds = participants.map { it.roomId }

                // Fetch game rooms
                val rooms = supabaseClient.postgrest["game_rooms"]
                    .select(columns = Columns.ALL) {
                        filter {
                            isIn("id", roomIds)
                            eq("status", "finished")
                        }
                    }
                    .decodeList<GameRoomDto>()

                // For each room, get the total number of participants
                val roomParticipantsCounts = mutableMapOf<String, Int>()
                for (roomId in roomIds) {
                    val participantsInRoom = supabaseClient.postgrest["game_participants"]
                        .select(columns = Columns.ALL) {
                            filter {
                                eq("room_id", roomId)
                            }
                        }
                        .decodeList<GameParticipantDto>()
                    roomParticipantsCounts[roomId] = participantsInRoom.size
                }

                // For each room, get the user's submissions to count items
                val roomSubmissions = mutableMapOf<String, List<RoundSubmissionDto>>()
                for (roomId in roomIds) {
                    val submissions = supabaseClient.postgrest["round_submissions"]
                        .select(columns = Columns.ALL) {
                            filter {
                                eq("room_id", roomId)
                                eq("user_id", currentUserId)
                            }
                        }
                        .decodeList<RoundSubmissionDto>()
                    roomSubmissions[roomId] = submissions
                }

                // Create GameHistory objects
                val gameHistories = rooms.map { room ->
                    // Find participant record for current user
                    val participant = participants.find { it.roomId == room.id }

                    // Get all participants for this room to determine position
                    val allRoomParticipants = supabaseClient.postgrest["game_participants"]
                        .select(columns = Columns.ALL) {
                            filter {
                                eq("room_id", room.id ?: "")
                            }
                        }
                        .decodeList<GameParticipantDto>()

                    // Sort by score and find position
                    val sortedParticipants =
                        allRoomParticipants.sortedByDescending { it.currentScore }
                    val position =
                        sortedParticipants.indexOfFirst { it.userId == currentUserId } + 1

                    // Get items found by counting successful submissions
                    val submissions = roomSubmissions[room.id] ?: emptyList()
                    val itemsFound =
                        submissions.count { it.status == com.ghostdev.huntit.data.model.SubmissionStatus.SUCCESS }

                    // Format date
                    val date = formatDate(room.createdAt)

                    GameHistory(
                        id = room.id ?: "",
                        roomCode = room.roomCode,
                        date = date,
                        score = participant?.currentScore ?: 0,
                        position = position,
                        totalPlayers = roomParticipantsCounts[room.id] ?: 0,
                        items = List(itemsFound) { "Item ${it + 1}" }  // Placeholder item names
                    )
                }

                // Sort games by date (newest first)
                val sortedGames = gameHistories.sortedByDescending { it.date }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        games = sortedGames,
                        errorMessage = null
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load game history"
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun formatDate(timestamp: Instant?): String {
        if (timestamp == null) return "Unknown Date"

        val dateTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = when (dateTime.monthNumber) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> "Unknown"
        }
        return "$month ${dateTime.dayOfMonth}, ${dateTime.year}"
    }

    fun loadLeaderboard(gameId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingLeaderboard = true, selectedGameId = gameId) }

                // Get all participants for this game
                val leaderboard =
                    leaderboardRepository.getTopPlayers(gameId).getOrNull() ?: emptyList()

                // Get user profiles to get display names
                val userIds = leaderboard.map { it.userId }
                val profiles =
                    leaderboardRepository.getUserProfiles(userIds).getOrNull() ?: emptyList()

                // Create leaderboard entries
                val entries = leaderboard.mapIndexed { index, participant ->
                    val profile = profiles.find { it.id == participant.userId }
                    LeaderboardEntry(
                        userId = participant.userId,
                        displayName = profile?.displayName ?: "Unknown Player",
                        score = participant.currentScore,
                        position = index + 1,
                        avatarId = profile?.avatarId ?: 1
                    )
                }

                // Load the challenges for this game
                val challenges = loadChallengesForGame(gameId)

                _uiState.update {
                    it.copy(
                        leaderboard = entries,
                        challenges = challenges,
                        isLoadingLeaderboard = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingLeaderboard = false,
                        errorMessage = e.message ?: "Failed to load leaderboard"
                    )
                }
            }
        }
    }
    
    private suspend fun loadChallengesForGame(gameId: String): List<RoundChallenge> {
        return try {
            val challenges = supabaseClient.postgrest["game_challenges"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", gameId)
                    }
                    // Sort by round number
                    order(column = "round_number", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<GameChallengeDto>()
            
            challenges.map { challenge ->
                RoundChallenge(
                    roundNumber = challenge.roundNumber,
                    challengeText = challenge.challengeText
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearSelectedGame() {
        _uiState.update {
            it.copy(
                selectedGameId = null,
                leaderboard = emptyList(),
                challenges = emptyList()
            )
        }
    }

    fun refreshGameHistory() {
        _uiState.update { it.copy(isLoading = true) }
        loadGameHistory()
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}