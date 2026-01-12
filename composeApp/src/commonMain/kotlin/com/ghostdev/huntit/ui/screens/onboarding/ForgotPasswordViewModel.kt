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
            // First, check if the user exists with this email
            val userExistsResult = authRepository.checkUserExists(email)
            
            if (userExistsResult.isSuccess && userExistsResult.getOrNull() == true) {
                // User exists, proceed with password reset
                val result = authRepository.sendPasswordResetEmail(email)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resetEmailSent = true,
                        successMessage = result.getOrNull()
                            ?: "Password reset email sent. Check your inbox for instructions."
                    )
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Failed to send reset email"

                    // If it specifically says the account doesn't exist, use our custom message
                    val displayError = if (errorMessage.contains("No account found") ||
                        errorMessage.contains("User not found")
                    ) {
                        "No account found with this email. Please sign up first."
                    } else {
                        errorMessage
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = displayError
                    )
                }
            } else if (userExistsResult.isSuccess && userExistsResult.getOrNull() == false) {
                // User does not exist
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No account found with this email. Please sign up first."
                )
            } else {
                // Error checking if user exists
                val errorMessage = userExistsResult.exceptionOrNull()?.message ?: "Failed to verify email"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = errorMessage
                )
            }
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
