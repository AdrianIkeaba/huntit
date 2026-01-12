package com.ghostdev.huntit.ui.screens.gameover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.data.model.ProfileDto
import com.ghostdev.huntit.data.repository.GameRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents a leaderboard entry for the game over screen
 */
data class LeaderboardEntry(
    val participant: ParticipantUiModel,
    val points: Int,
    val rank: Int
)

/**
 * UI state for the game over screen
 */
data class GameOverUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val showFullLeaderboard: Boolean = false,
    val roomId: String? = null,
    val roomName: String? = null
)

class GameOverViewModel(
    private val gameRepository: GameRepository,
    private val supabaseClient: SupabaseClient,
    private val roomCodeStorage: RoomCodeStorage
) : ViewModel() {

    private val _state = MutableStateFlow(GameOverUiState())
    val state: StateFlow<GameOverUiState> = _state.asStateFlow()

    // Cache for profiles
    private val profilesCache = mutableMapOf<String, ProfileDto>()

    init {
        loadGameData()
    }

    private fun loadGameData() {
        _state.update { it.copy(isLoading = true, error = null) }

        val roomCode = roomCodeStorage.getCurrentRoomCode()
        if (roomCode.isEmpty()) {
            _state.update { it.copy(isLoading = false, error = "No active game room") }
            return
        }

        viewModelScope.launch {
            try {
                // Fetch game room details
                val roomResult = gameRepository.getGameRoomByCode(roomCode)
                if (roomResult.isFailure) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = roomResult.exceptionOrNull()?.message
                                ?: "Failed to load game room"
                        )
                    }
                    return@launch
                }

                val gameRoom = roomResult.getOrNull()!!
                val roomId = gameRoom.id ?: run {
                    _state.update { it.copy(isLoading = false, error = "Invalid game room ID") }
                    return@launch
                }

                // Update state with room data
                _state.update {
                    it.copy(
                        roomId = roomId,
                        roomName = gameRoom.roomName
                    )
                }

                // Load participants (for leaderboard)
                loadLeaderboard(roomId)
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

    private suspend fun loadLeaderboard(roomId: String) {
        try {
            val participantsResult = gameRepository.getParticipants(roomId)
            if (participantsResult.isSuccess) {
                val participants = participantsResult.getOrNull() ?: emptyList()
                val leaderboard = mapParticipantsToLeaderboard(participants)
                _state.update {
                    it.copy(
                        leaderboard = leaderboard,
                        isLoading = false
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = participantsResult.exceptionOrNull()?.message
                            ?: "Failed to load participants"
                    )
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load leaderboard"
                )
            }
        }
    }

    private suspend fun mapParticipantsToLeaderboard(participants: List<GameParticipantDto>): List<LeaderboardEntry> {
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
                // Log error but continue with cached profiles
                println("Error fetching profiles: ${e.message}")
            }
        }

        // Sort by score descending and create leaderboard entries
        return participants
            .sortedByDescending { it.currentScore }
            .mapIndexed { index, participant ->
                val profile = profilesCache[participant.userId]
                LeaderboardEntry(
                    participant = ParticipantUiModel(
                        id = participant.userId,
                        name = profile?.displayName ?: "Unknown Player",
                        avatarId = profile?.avatarId ?: 1,
                        isHost = participant.isHost,
                        joinOrder = participant.joinOrder
                    ),
                    points = participant.currentScore,
                    rank = index + 1
                )
            }
    }

    /**
     * Toggle between showing the top 3 players and the full leaderboard
     */
    fun toggleFullLeaderboard() {
        _state.update { it.copy(showFullLeaderboard = !it.showFullLeaderboard) }
    }

    /**
     * Refresh the leaderboard data
     */
    fun refreshLeaderboard() {
        val roomId = _state.value.roomId ?: return
        viewModelScope.launch {
            loadLeaderboard(roomId)
        }
    }

    /**
     * Clear any error message
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Get the current user ID from Supabase auth
     * Returns an empty string if the user is not authenticated
     */
    fun getCurrentUserId(): String {
        return supabaseClient.auth.currentUserOrNull()?.id ?: ""
    }
    
    /**
     * Exit the current game and navigate to home screen
     */
    fun exitGame(navigateToHome: () -> Unit) {
        val roomId = _state.value.roomId ?: return

        viewModelScope.launch {
            try {
                // Get current user ID from Supabase
                val userId = getCurrentUserId()
                if (userId.isEmpty()) return@launch

                // Set the user's playing status to false
                gameRepository.setUserPlaying(roomId, userId, false)

                // Unsubscribe from real-time updates
                gameRepository.unsubscribeFromGame()

                // Clear room code from storage
                roomCodeStorage.clearRoomCode()

                // Navigate back to home
                navigateToHome()
            } catch (e: Exception) {
                println("Error exiting game: ${e.message}")
                // Still navigate to home even if there's an error
                navigateToHome()
            }
        }
    }
}