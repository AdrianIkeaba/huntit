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
    data object SignIn : SplashDestination()  // Add SignIn destination
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
                    println("DEBUG ONBOARDING: $hasCompletedOnboarding")

                    if (!hasCompletedOnboarding) {
                        // First time users see the onboarding screens
                        println("DEBUG: First time user, showing onboarding")
                        _destination.value = SplashDestination.Onboarding
                    } else {
                        // Returning users go straight to sign-in
                        println("DEBUG: Returning user, going to sign in")
                        _destination.value = SplashDestination.SignIn
                    }
                } else {
                    // Check if user has completed profile
                    val hasCompletedProfile = authRepository.hasCompletedProfile()
                    if (!hasCompletedProfile) {
                        _destination.value = SplashDestination.UserName
                        return@launch
                    }

                    // User is logged in with a complete profile, check for active games
                    // Try up to 3 times to check for active game participation
                    var activeGameInfo: ActiveGameInfo? = null
                    var navigated = false
                    
                    for (attempt in 1..3) {
                        println("DEBUG: Checking for active games (attempt $attempt)...")
                        
                        val activeGameResult = gameSetupRepository.checkActiveGameParticipation()
                        
                        if (activeGameResult.isSuccess) {
                            activeGameInfo = activeGameResult.getOrNull()
                            if (activeGameInfo != null) {
                                println("DEBUG: Found active game: ${activeGameInfo.gameRoom.roomCode}, status: ${activeGameInfo.gameRoom.status}")
                                navigated = true
                                break
                            }
                        } else {
                            println("DEBUG: No active game found (attempt $attempt): ${activeGameResult.exceptionOrNull()?.message}")
                        }
                        
                        if (attempt < 3) {
                            delay(300) // Short delay between attempts
                        }
                    }
                    
                    // Process the active game if found
                    if (navigated && activeGameInfo != null) {
                        val roomCode = activeGameInfo.gameRoom.roomCode
                        val isPlaying = activeGameInfo.isPlaying
                        
                        println("DEBUG: Processing active game - roomCode: $roomCode, isPlaying: $isPlaying, status: ${activeGameInfo.gameRoom.status}")

                        // If user is not playing, navigate to Home regardless of game status
                        if (!isPlaying) {
                            println("DEBUG: User is not playing, going to Home")
                            _destination.value = SplashDestination.Home
                            return@launch
                        }

                        // Determine where to navigate based on game status
                        when (activeGameInfo.gameRoom.status) {
                            GameStatus.LOBBY -> {
                                // Game is in lobby, direct user there
                                println("DEBUG: Game is in LOBBY, navigating to Lobby screen")
                                _destination.value = SplashDestination.Lobby(roomCode)
                            }

                            GameStatus.IN_PROGRESS -> {
                                // Game is in progress, direct user to game screen
                                println("DEBUG: Game is IN_PROGRESS, navigating to Game screen")
                                _destination.value = SplashDestination.Game(roomCode)
                            }

                            GameStatus.FINISHED -> {
                                // Game has ended, navigate to GameOverScreen to show leaderboard
                                println("DEBUG: Game is FINISHED, navigating to GameOver screen")
                                _destination.value = SplashDestination.GameOver(roomCode)
                            }

                            else -> {
                                println("DEBUG: Game has unknown status, navigating to Home")
                                _destination.value = SplashDestination.Home
                            }
                        }
                    } else {
                        // No active games found after retries
                        println("DEBUG: No active games found after all attempts, navigating to Home")
                        _destination.value = SplashDestination.Home
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Error in splash navigation flow: ${e.message}")
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