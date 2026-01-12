package com.ghostdev.huntit.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NewPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val passwordMatchError: Boolean = false,
    val passwordResetSuccess: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null
)

class NewPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewPasswordUiState())
    val uiState: StateFlow<NewPasswordUiState> = _uiState.asStateFlow()

    // Set the access token received from the deep link
    fun setTokens(accessToken: String?, refreshToken: String?, expiresIn: Long?) {
        _uiState.value = _uiState.value.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn
        )
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordMatchError = false,
            errorMessage = null
        )
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword = confirmPassword,
            passwordMatchError = _uiState.value.password != confirmPassword && confirmPassword.isNotEmpty(),
            errorMessage = null
        )
    }

    fun onResetPasswordClick() {
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword
        val accessToken = _uiState.value.accessToken
        val refreshToken = _uiState.value.refreshToken
        val expiresIn = _uiState.value.expiresIn

        // Validation
        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a new password")
            return
        }

        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(
                passwordMatchError = true,
                errorMessage = "Passwords don't match"
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            if (accessToken != null && refreshToken != null && expiresIn != null) {
                authRepository.resetPasswordWithTokens(
                    accessToken,
                    refreshToken,
                    expiresIn,
                    password
                ).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Password has been reset successfully",
                            passwordResetSuccess = true
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to reset password"
                        )
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Invalid reset link. Missing required tokens."
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
}