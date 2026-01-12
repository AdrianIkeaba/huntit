package com.ghostdev.huntit.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.repository.GameSetupRepository.ActiveGameInfo
import com.ghostdev.huntit.data.repository.AuthRepository
import com.ghostdev.huntit.data.repository.GameSetupRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SplashDestination {
    data object Onboarding : SplashDestination()
    data object SignIn : SplashDestination()
    data object UserName : SplashDestination()
    data object Home : SplashDestination()
    data class Lobby(val roomCode: String) : SplashDestination()
    data class Game(val roomCode: String) : SplashDestination()
    data class GameOver(val roomCode: String) : SplashDestination()
    data object ShowEndedGameNotification : SplashDestination()
}

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val gameSetupRepository: GameSetupRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            delay(1500) // Splash screen delay

            try {
                val isLoggedIn = authRepository.isLoggedIn()

                if (!isLoggedIn) {
                    // For users not logged in, we'll check if they've completed onboarding
                    val hasCompletedOnboarding = authRepository.hasCompletedOnboarding()

                    if (!hasCompletedOnboarding) {
                        _destination.value = SplashDestination.Onboarding
                    } else {
                        _destination.value = SplashDestination.SignIn
                    }
                } else {
                    // Check if user has completed profile
                    val hasCompletedProfile = authRepository.hasCompletedProfile()
                    if (!hasCompletedProfile) {
                        _destination.value = SplashDestination.UserName
                        return@launch
                    }

                    var activeGameInfo: ActiveGameInfo? = null
                    var navigated = false
                    
                    for (attempt in 1..3) {
                        
                        val activeGameResult = gameSetupRepository.checkActiveGameParticipation()
                        
                        if (activeGameResult.isSuccess) {
                            activeGameInfo = activeGameResult.getOrNull()
                            if (activeGameInfo != null) {
                                navigated = true
                                break
                            }
                        } else {
                            println("DEBUG: No active game found (attempt $attempt): ${activeGameResult.exceptionOrNull()?.message}")
                        }
                        
                        if (attempt < 3) {
                            delay(300)
                        }
                    }
                    
                    // Process the active game if found
                    if (navigated && activeGameInfo != null) {
                        val roomCode = activeGameInfo.gameRoom.roomCode
                        val isPlaying = activeGameInfo.isPlaying

                        // If user is not playing, navigate to Home regardless of game status
                        if (!isPlaying) {
                            _destination.value = SplashDestination.Home
                            return@launch
                        }

                        // Determine where to navigate based on game status
                        when (activeGameInfo.gameRoom.status) {
                            GameStatus.LOBBY -> {
                                // Game is in lobby, direct user there
                                _destination.value = SplashDestination.Lobby(roomCode)
                            }

                            GameStatus.IN_PROGRESS -> {
                                // Game is in progress, direct user to game screen
                                _destination.value = SplashDestination.Game(roomCode)
                            }

                            GameStatus.FINISHED -> {
                                // Game has ended, navigate to GameOverScreen to show leaderboard
                                _destination.value = SplashDestination.GameOver(roomCode)
                            }

                            else -> {
                                _destination.value = SplashDestination.Home
                            }
                        }
                    } else {
                        // No active games found after retries
                        _destination.value = SplashDestination.Home
                    }
                }
            } catch (e: Exception) {
                // On error, decide based on onboarding status
                try {
                    val hasCompletedOnboarding = authRepository.hasCompletedOnboarding()
                    if (hasCompletedOnboarding) {
                        _destination.value = SplashDestination.SignIn
                    } else {
                        _destination.value = SplashDestination.Onboarding
                    }
                } catch (innerEx: Exception) {
                    // Last resort fallback
                    _destination.value = SplashDestination.Onboarding
                }
            }
        }
    }
}