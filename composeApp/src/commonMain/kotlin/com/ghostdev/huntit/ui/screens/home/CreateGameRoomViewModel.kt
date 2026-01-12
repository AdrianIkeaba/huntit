package com.ghostdev.huntit.ui.screens.home

import com.ghostdev.huntit.data.model.GameTheme
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.data.repository.GameSetupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.utils.toUserFriendlyError

data class CreateGameRoomState(
    val roomName: String = "",
    val roundDuration: RoundDuration = RoundDuration.QUICK,
    val gameTheme: GameTheme = GameTheme.OUTDOORS_NATURE,
    val maxPlayers: Int? = null,
    val totalRounds: Int = 3,
    val cooldownSeconds: Int = 30,
    val isPublic: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdRoomCode: String? = null
)

class CreateGameRoomViewModel(
    private val gameSetupRepository: GameSetupRepository,
    private val roomCodeStorage: RoomCodeStorage
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGameRoomState())
    val state: StateFlow<CreateGameRoomState> = _state.asStateFlow()

    fun updateRoomName(name: String) {
        _state.update { it.copy(roomName = name) }
    }

    fun updateRoundDuration(duration: RoundDuration) {
        _state.update { it.copy(roundDuration = duration) }
    }

    fun updateGameTheme(theme: GameTheme) {
        _state.update { it.copy(gameTheme = theme) }
    }

    fun updateMaxPlayers(maxPlayers: Int?) {
        _state.update { it.copy(maxPlayers = maxPlayers) }
    }

    fun updateTotalRounds(rounds: Int) {
        _state.update { it.copy(totalRounds = rounds) }
    }

    fun updateCooldownSeconds(seconds: Int) {
        _state.update { it.copy(cooldownSeconds = seconds) }
    }

    fun updateIsPublic(isPublic: Boolean) {
        _state.update { it.copy(isPublic = isPublic) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Reset the ViewModel state when leaving the screen
     * This prevents the form data from being persisted unnecessarily
     */
    fun resetState() {
        _state.value = CreateGameRoomState()
    }

    fun createRoom() {
        val currentState = _state.value

        if (currentState.roomName.isBlank()) {
            _state.update { it.copy(error = "Room name can't be empty") }
            return
        }

        // Ensure maxPlayers is either null (no limit) or at least 2
        if (currentState.maxPlayers != null && currentState.maxPlayers < 2) {
            _state.update { it.copy(error = "Maximum players must be at least 2") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = gameSetupRepository.createGameRoom(
                roomName = currentState.roomName,
                roundDuration = currentState.roundDuration,
                gameTheme = currentState.gameTheme,
                maxPlayers = currentState.maxPlayers,
                totalRounds = currentState.totalRounds,
                cooldownSeconds = currentState.cooldownSeconds,
                isPublic = currentState.isPublic
            )

            result.fold(
                onSuccess = { roomCode ->
                    // Store the room code for access in the lobby screen
                    roomCodeStorage.setRoomCode(roomCode)
                    _state.update { it.copy(isLoading = false, createdRoomCode = roomCode) }
                },
                onFailure = { error ->
                    // Store the raw error message without conversion
                    // The conversion will happen in the UI layer right before display
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to create room"
                        )
                    }
                }
            )
        }
    }
}