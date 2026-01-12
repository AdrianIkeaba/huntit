package com.ghostdev.huntit.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.model.GamePhase
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.repository.GameSetupRepository.ActiveGameInfo
import com.ghostdev.huntit.data.repository.AuthRepository
import com.ghostdev.huntit.data.repository.GameSetupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val shouldNavigateToUserName: Boolean = false,
    val shouldNavigateToHome: Boolean = false,
    val activeGameRoomCode: String? = null,
    val shouldNavigateToLobby: Boolean = false,
    val shouldNavigateToGame: Boolean = false,
    val shouldNavigateToLeaderboard: Boolean = false,
    val shouldShowGameEndedMessage: Boolean = false,
    val showCreateAccountDialog: Boolean = false,
    val loginError: String? = null
)

class SignInViewModel(
    private val authRepository: AuthRepository,
    private val gameSetupRepository: GameSetupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun onContinueClick() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

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

        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your password")
            return
        }

        if (password.length < 6) {
            _uiState.value =
                _uiState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            // First check if the user exists
            val userExistsResult = authRepository.checkUserExists(email)

            // If we couldn't determine if the user exists (e.g., due to network error),
            // proceed with the login attempt
            val userExists = userExistsResult.getOrDefault(true)

            if (!userExists) {
                // User doesn't exist, show dialog to create account
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showCreateAccountDialog = true
                )
                return@launch
            }

            // User exists, try to login
            val loginResult = authRepository.login(email, password)

            if (loginResult.isSuccess) {
                val user = loginResult.getOrNull()!!

                if (user.displayName.isEmpty()) {
                    // User needs to set display name first
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        shouldNavigateToUserName = true
                    )
                } else {
                    // Check if user is currently in any active game
                    checkActiveGameAndNavigate()
                }
            } else {
                // Login failed but user exists (likely password error)
                val errorMessage =
                    loginResult.exceptionOrNull()?.message ?: "Incorrect email or password."

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

    fun onNavigationComplete() {
        _uiState.value = _uiState.value.copy(
            shouldNavigateToUserName = false,
            shouldNavigateToHome = false,
            shouldNavigateToLobby = false,
            shouldNavigateToGame = false,
            shouldShowGameEndedMessage = false,
            activeGameRoomCode = null
        )
    }

    // Handle user's decision on the account creation dialog
    fun onDismissCreateAccountDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateAccountDialog = false
            // No error message needed when they dismiss the dialog
        )
    }

    // Handle user confirming they want to create an account
    fun onConfirmCreateAccount() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        _uiState.value = _uiState.value.copy(
            showCreateAccountDialog = false,
            isLoading = true
        )

        viewModelScope.launch {
            val signUpResult = authRepository.signUp(email, password)

            if (signUpResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    shouldNavigateToUserName = true
                )
            } else {
                val errorMessage =
                    signUpResult.exceptionOrNull()?.message ?: "Failed to create account."

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = errorMessage
                )
            }
        }
    }

    private fun checkActiveGameAndNavigate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val activeGameResult = gameSetupRepository.checkActiveGameParticipation()

                if (activeGameResult.isSuccess) {
                    val activeGameInfo = activeGameResult.getOrNull()!!
                    val roomCode = activeGameInfo.gameRoom.roomCode
                    val isPlaying = activeGameInfo.isPlaying

                    // If user is not playing, navigate to Home
                    if (!isPlaying) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            shouldNavigateToHome = true
                        )
                        return@launch
                    }

                    // Determine where to navigate based on game status
                    when (activeGameInfo.gameRoom.status) {
                        GameStatus.LOBBY -> {
                            // Game is in lobby, direct user there
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                activeGameRoomCode = roomCode,
                                shouldNavigateToLobby = true
                            )
                        }

                        GameStatus.IN_PROGRESS -> {
                            // Game is in progress, direct user to game screen
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                activeGameRoomCode = roomCode,
                                shouldNavigateToGame = true
                            )
                        }

                        GameStatus.FINISHED -> {
                            // Game has ended, go to leaderboard
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                shouldShowGameEndedMessage = false,
                                activeGameRoomCode = roomCode,
                                shouldNavigateToLeaderboard = true
                            )
                        }

                        else -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                shouldShowGameEndedMessage = false,
                                shouldNavigateToHome = true
                            )
                        }
                    }
                } else {
                    // No active games or error, go to home
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        shouldNavigateToHome = true
                    )
                }
            } catch (e: Exception) {
                // On error, go to home
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    shouldNavigateToHome = true
                )
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }
}
