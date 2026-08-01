package com.ghostdev.huntit.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val resetEmailSent: Boolean = false
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value =
            _uiState.value.copy(email = email, errorMessage = null, successMessage = null)
    }

    fun onResetClick() {
        val email = _uiState.value.email.trim()

        // Validation
        if (email.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email")
            return
        }

        if (!isValidEmail(email)) {
            _uiState.value =
                _uiState.value.copy(errorMessage = "Please enter a valid email address")
            return
        }

        _uiState.value =
            _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)

        viewModelScope.launch {
            // Always ask the auth service directly and use a generic response.
            // A password-reset form must not reveal which emails are registered.
            authRepository.sendPasswordResetEmail(email).fold(
                onSuccess = { message ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resetEmailSent = true,
                        successMessage = message
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Unable to send a reset email right now. Please try again."
                    )
                }
            )
        }
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onSuccessShown() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun resetState() {
        _uiState.value = ForgotPasswordUiState()
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }
}
