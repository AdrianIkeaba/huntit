package com.ghostdev.huntit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.repository.GameSetupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinGameState(
    val roomCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val joinSuccess: Boolean = false
)

class JoinGameViewModel(
    private val gameSetupRepository: GameSetupRepository,
    private val roomCodeStorage: RoomCodeStorage
) : ViewModel() {

    private val _state = MutableStateFlow(JoinGameState())
    val state: StateFlow<JoinGameState> = _state.asStateFlow()

    fun updateRoomCode(code: String) {
        _state.update { it.copy(roomCode = code) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Reset the form state when the dialog is dismissed
     */
    fun resetState() {
        _state.value = JoinGameState()
    }

    fun joinGameRoom() {
        val currentRoomCode = _state.value.roomCode

        if (currentRoomCode.isBlank() || currentRoomCode.length != 6) {
            _state.update { it.copy(error = "Please enter a valid 6-digit room code") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = gameSetupRepository.joinGameRoom(currentRoomCode)

            result.fold(
                onSuccess = { roomCode ->
                    // Store the room code for access in the lobby screen
                    roomCodeStorage.setRoomCode(roomCode)
                    _state.update { it.copy(isLoading = false, joinSuccess = true) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to join room"
                        )
                    }
                }
            )
        }
    }

    fun resetJoinSuccess() {
        _state.update { it.copy(joinSuccess = false) }
    }
}