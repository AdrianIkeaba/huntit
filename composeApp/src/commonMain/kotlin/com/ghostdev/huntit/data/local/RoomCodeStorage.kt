package com.ghostdev.huntit.data.local

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Class for storing and retrieving room codes between screens
 * This implementation keeps the room code only in memory for the current session
 * and does not persist it across app restarts
 */
class RoomCodeStorage(private val settings: Settings) {
    private val ROOM_CODE_KEY = "current_room_code"

    // In-memory cache to avoid frequent preference reads
    private val _currentRoomCode = MutableStateFlow("")
    val currentRoomCode: StateFlow<String> = _currentRoomCode.asStateFlow()

    init {
        // Initialize from preferences but don't persist empty values
        val savedCode = settings.getStringOrNull(ROOM_CODE_KEY) ?: ""
        if (savedCode.isNotEmpty()) {
            _currentRoomCode.value = savedCode
        }
    }

    fun setRoomCode(code: String) {
        _currentRoomCode.value = code
        // Only store if code is not empty
        if (code.isNotEmpty()) {
            settings.putString(ROOM_CODE_KEY, code)
        } else {
            settings.remove(ROOM_CODE_KEY)
        }
    }

    fun getCurrentRoomCode(): String {
        return _currentRoomCode.value
    }

    fun clearRoomCode() {
        _currentRoomCode.value = ""
        settings.remove(ROOM_CODE_KEY)
    }
}