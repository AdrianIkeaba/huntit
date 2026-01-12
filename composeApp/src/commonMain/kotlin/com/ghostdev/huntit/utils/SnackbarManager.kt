package com.ghostdev.huntit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Data class that represents a snackbar message to be displayed
 */
data class SnackbarMessage(
    val message: String,
    val isError: Boolean = false
)

/**
 * A manager for snackbar messages to be displayed across the app
 */
class SnackbarManager {
    private val _currentMessage = mutableStateOf<SnackbarMessage?>(null)
    val currentMessage: MutableState<SnackbarMessage?> = _currentMessage

    fun showMessage(message: String, isError: Boolean = false) {
        _currentMessage.value = SnackbarMessage(message, isError)
    }

    fun clearMessage() {
        _currentMessage.value = null
    }

    companion object {
        // Singleton instance
        val instance = SnackbarManager()
    }
}

/**
 * Composable for accessing the SnackbarManager as a CompositionLocal
 */
@Composable
fun rememberSnackbarManager(): SnackbarManager {
    return remember { SnackbarManager.instance }
}