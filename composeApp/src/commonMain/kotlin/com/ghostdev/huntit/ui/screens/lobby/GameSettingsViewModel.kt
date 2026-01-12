package com.ghostdev.huntit.ui.screens.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.data.repository.GameSetupRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameSettingsState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val roomName: String = "",
    val roundDuration: RoundDuration = RoundDuration.QUICK,
    val roomId: String? = null
)

class GameSettingsViewModel(
    private val gameSetupRepository: GameSetupRepository,
    private val supabaseClient: SupabaseClient,
    private val roomCodeStorage: RoomCodeStorage
) : ViewModel() {

    private val _state = MutableStateFlow(GameSettingsState(isLoading = true))
    val state: StateFlow<GameSettingsState> = _state.asStateFlow()

    init {
        loadGameSettings()
    }

    private fun loadGameSettings() {
        viewModelScope.launch {
            try {
                val roomCode = roomCodeStorage.getCurrentRoomCode()
                if (roomCode.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Room code not found"
                        )
                    }
                    return@launch
                }

                val roomResult = gameSetupRepository.fetchGameRoomDetails(roomCode)

                roomResult.fold(
                    onSuccess = { gameRoom ->
                        // Convert string duration to RoundDuration enum
                        val duration = when (gameRoom.roundDuration) {
                            "Quick (3 min)", "quick" -> RoundDuration.QUICK
                            "Standard (5 min)", "standard" -> RoundDuration.STANDARD
                            "Extended (7 min)", "marathon" -> RoundDuration.MARATHON
                            else -> RoundDuration.QUICK
                        }

                        _state.update {
                            it.copy(
                                isLoading = false,
                                roomName = gameRoom.roomName ?: "",
                                roundDuration = duration,
                                roomId = gameRoom.id
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load game settings"
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

    fun updateRoomName(name: String) {
        _state.update { it.copy(roomName = name) }
    }

    fun updateRoundDuration(duration: RoundDuration) {
        _state.update { it.copy(roundDuration = duration) }
    }

    fun saveSettings() {
        val roomId = state.value.roomId ?: run {
            _state.update { it.copy(error = "Room ID not found") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val currentDuration = state.value.roundDuration
            // Use the duration name for database storage (e.g., "quick", "standard", "marathon")
            val durationString = currentDuration.durationName
            val durationSeconds = currentDuration.seconds

            val result = gameSetupRepository.updateGameRoomSettings(
                roomId = roomId,
                roomName = state.value.roomName,
                roundDuration = durationString,
                roundDurationSeconds = durationSeconds
            )

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to save settings"
                        )
                    }
                }
            )
        }
    }

    fun deleteGame() {
        val roomId = state.value.roomId ?: run {
            _state.update { it.copy(error = "Room ID not found") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = gameSetupRepository.deleteGame(roomId)

            result.fold(
                onSuccess = {
                    // Clear the room code from storage on success
                    roomCodeStorage.clearRoomCode()
                    _state.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to delete game"
                        )
                    }
                }
            )
        }
    }
}