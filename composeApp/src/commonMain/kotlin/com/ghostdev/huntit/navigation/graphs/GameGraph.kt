package com.ghostdev.huntit.navigation.graphs

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.ghostdev.huntit.navigation.NavDestinations
import com.ghostdev.huntit.ui.screens.game.GameScreen
import com.ghostdev.huntit.ui.screens.game.GameViewModel
import com.ghostdev.huntit.ui.screens.game.PhotoReviewScreen
import com.ghostdev.huntit.ui.screens.game.PhotoScreen
import com.ghostdev.huntit.ui.screens.game.SubmissionViewModel
import org.koin.compose.koinInject

fun NavGraphBuilder.gameGraph(
    navController: NavController,
    innerPadding: PaddingValues
) {
    navigation<NavDestinations.GameGraph>(
        startDestination = NavDestinations.GameGraph.Game
    ) {
        composable<NavDestinations.GameGraph.Game> {
            GameScreen(
                innerPadding = innerPadding,
                navigateToCamera = {
                    navController.navigate(NavDestinations.GameGraph.Photo) {
                        launchSingleTop = true
                    }
                },
                navigateToWinners = {
                    navController.navigate(NavDestinations.GameOverGraph.GameOver) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.GameGraph) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable<NavDestinations.GameGraph.Photo> {
            // Get ViewModels with proper scope
            val submissionViewModel: SubmissionViewModel = koinInject()
            val gameViewModel: GameViewModel = koinInject()

            // Get the actual challenge and time remaining from GameViewModel
            val gameState = gameViewModel.state.value

            // Get challenge and print it for debugging
            val challenge = gameState.currentChallenge


            // If challenge is blank, try to trigger loading it
            if (challenge.isBlank() && gameState.gameRoom?.id != null) {
                println("Challenge is empty, will try to load during submission")
            }

            // Format time remaining
            val timeRemainingMs = gameState.timeRemainingMs
            val timeRemaining = if (timeRemainingMs > 0) {
                val totalSeconds = (timeRemainingMs / 1000).coerceAtLeast(0)
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val minutesStr = if (minutes < 10) "0$minutes" else "$minutes"
                val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
                "$minutesStr:$secondsStr"
            } else {
                "00:00"
            }

            // Initialize submission ViewModel with game data
            val roomId = gameState.gameRoom?.id
            val userId = gameState.currentUserId
            if (roomId != null && userId.isNotEmpty()) {
                submissionViewModel.initialize(
                    roomId = roomId,
                    userId = userId,
                    roundNumber = gameState.currentRound,
                    challenge = challenge,
                    theme = gameState.theme,
                    timeRemaining = timeRemaining,
                    phaseEndsAtMs = gameState.timeRemainingMs
                )
            }

            PhotoScreen(
                innerPadding = innerPadding,
                viewModel = submissionViewModel,
                challenge = challenge,
                timeRemaining = timeRemaining,
                navigateBack = {
                    navController.navigate(NavDestinations.GameGraph.Game) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.GameGraph.Game) { inclusive = false }
                    }
                },
                navigateToResults = {
                    navController.navigate(NavDestinations.GameGraph.Review) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<NavDestinations.GameGraph.Review> {
            val submissionViewModel: SubmissionViewModel = koinInject()

            PhotoReviewScreen(
                innerPadding = innerPadding,
                viewModel = submissionViewModel,
                navigateBack = {
                    println("Navigate back to Photo from Review")
                    navController.navigate(NavDestinations.GameGraph.Photo) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.GameGraph.Photo) { inclusive = true }
                    }
                },
                navigateToGame = {
                    println("Navigate to Game from Review")
                    navController.navigate(NavDestinations.GameGraph.Game) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.GameGraph.Photo) { inclusive = true }
                    }
                },
                navigateToWinners = {
                    println("Navigate to Winners from Review (game ended while viewing results)")
                    navController.navigate(NavDestinations.GameOverGraph.GameOver) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.GameGraph) {
                            inclusive = true
                        }
                    }
                }
            )
        }


    }
}