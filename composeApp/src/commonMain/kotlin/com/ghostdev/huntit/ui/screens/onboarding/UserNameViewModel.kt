package com.ghostdev.huntit.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserNameUiState(
    val name: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val shouldNavigateToHome: Boolean = false
)

class UserNameViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserNameUiState())
    val uiState: StateFlow<UserNameUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onDoneClick() {
        val name = _uiState.value.name.trim()

        // Validation
        if (name.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your name")
            return
        }

        if (name.length < 2) {
            _uiState.value =
                _uiState.value.copy(errorMessage = "Name must be at least 2 characters")
            return
        }

        if (name.length > 50) {
            _uiState.value = _uiState.value.copy(errorMessage = "Name is too long")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.setDisplayName(name)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    shouldNavigateToHome = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to save name"
                )
            }
        }
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onNavigationComplete() {
        _uiState.value = _uiState.value.copy(shouldNavigateToHome = false)
    }
}
