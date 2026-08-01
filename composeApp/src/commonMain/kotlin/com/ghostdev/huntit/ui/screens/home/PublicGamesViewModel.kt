package com.ghostdev.huntit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameTheme
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.data.repository.GameSetupRepository
import com.ghostdev.huntit.data.repository.ReportReason
import com.ghostdev.huntit.data.repository.SafetyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublicGameUiModel(
    val id: String,
    val hostId: String,
    val roomCode: String,
    val roomName: String,
    val theme: GameTheme,
    val roundDurationSeconds: Int,
    val totalRounds: Int,
    val cooldownSeconds: Int,
    val participantsCount: Int,
    val maxPlayers: Int?
)

data class PublicGamesState(
    val isLoading: Boolean = true,
    val games: List<PublicGameUiModel> = emptyList(),
    val error: String? = null,
    val isJoining: Boolean = false,
    val showJoinDialog: Boolean = false,
    val selectedGame: PublicGameUiModel? = null,
    val joinSuccessRoomCode: String? = null,
    val actionMessage: String? = null
)

class PublicGamesViewModel(
    private val gameSetupRepository: GameSetupRepository,
    private val roomCodeStorage: RoomCodeStorage,
    private val safetyRepository: SafetyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PublicGamesState())
    val state: StateFlow<PublicGamesState> = _state.asStateFlow()

    init {
        loadPublicGames()
    }

    fun loadPublicGames() {
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Fetch public games
                val gamesResult = gameSetupRepository.fetchPublicGames()
                
                if (gamesResult.isSuccess) {
                    val gameRooms = gamesResult.getOrNull() ?: emptyList()
                    
                    // If we have games, fetch participant counts for each
                    if (gameRooms.isNotEmpty()) {
                        val roomIds = gameRooms.mapNotNull { it.id }
                        val participantCountsResult = gameSetupRepository.fetchParticipantCountsForRooms(roomIds)
                        
                        val participantCounts = participantCountsResult.getOrDefault(emptyMap())
                        
                        // Map GameRoomDto to PublicGameUiModel with participant counts
                        val publicGameModels = gameRooms.mapNotNull { gameRoom ->
                            gameRoom.id?.let { roomId ->
                                PublicGameUiModel(
                                    id = roomId,
                                    hostId = gameRoom.hostId,
                                    roomCode = gameRoom.roomCode,
                                    roomName = gameRoom.roomName,
                                    theme = gameRoom.theme,
                                    roundDurationSeconds = gameRoom.roundDurationSeconds,
                                    totalRounds = gameRoom.totalRounds,
                                    cooldownSeconds = gameRoom.cooldownSeconds,
                                    participantsCount = participantCounts[roomId] ?: 0,
                                    maxPlayers = gameRoom.maxPlayers
                                )
                            }
                        }
                        
                        _state.update {
                            it.copy(
                                isLoading = false,
                                games = publicGameModels,
                                error = if (publicGameModels.isEmpty()) "No public games available" else null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                games = emptyList(),
                                error = "No public games available"
                            )
                        }
                    }
                } else {
                    val error = gamesResult.exceptionOrNull()?.message ?: "Failed to load public games"
                    _state.update { it.copy(isLoading = false, error = error) }
                }
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

    fun selectGame(game: PublicGameUiModel) {
        _state.update { 
            it.copy(selectedGame = game, showJoinDialog = true) 
        }
    }

    fun dismissJoinDialog() {
        _state.update { 
            it.copy(showJoinDialog = false, selectedGame = null) 
        }
    }

    fun joinSelectedGame() {
        val selectedGame = _state.value.selectedGame ?: return
        
        _state.update { it.copy(isJoining = true) }
        
        viewModelScope.launch {
            try {
                val result = gameSetupRepository.joinGameRoom(selectedGame.roomCode)
                
                if (result.isSuccess) {
                    val roomCode = result.getOrNull()
                    // Store the room code for the lobby screen to access
                    roomCode?.let { roomCodeStorage.setRoomCode(it) }
                    _state.update { 
                        it.copy(
                            isJoining = false,
                            joinSuccessRoomCode = roomCode,
                            showJoinDialog = false
                        ) 
                    }
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Failed to join game"
                    _state.update { 
                        it.copy(
                            isJoining = false,
                            error = errorMessage,
                            showJoinDialog = false
                        ) 
                    }
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isJoining = false, 
                        error = e.message ?: "An unexpected error occurred",
                        showJoinDialog = false
                    ) 
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearActionMessage() {
        _state.update { it.copy(actionMessage = null) }
    }

    fun reportRoom(game: PublicGameUiModel, reason: ReportReason) {
        viewModelScope.launch {
            safetyRepository.reportPlayer(
                reportedUserId = game.hostId,
                roomId = game.id,
                reason = reason,
                details = "Public room: ${game.roomName}".take(500)
            ).fold(
                onSuccess = {
                    _state.update {
                        it.copy(actionMessage = "Room report submitted for review.")
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not report room")
                    }
                }
            )
        }
    }

    fun blockRoomHost(game: PublicGameUiModel) {
        viewModelScope.launch {
            safetyRepository.blockPlayer(game.hostId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            games = it.games.filterNot { room ->
                                room.hostId == game.hostId
                            },
                            actionMessage = "Host blocked. Their public rooms are now hidden."
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not block host")
                    }
                }
            )
        }
    }

    fun resetJoinSuccess() {
        _state.update { it.copy(joinSuccessRoomCode = null) }
    }
}
