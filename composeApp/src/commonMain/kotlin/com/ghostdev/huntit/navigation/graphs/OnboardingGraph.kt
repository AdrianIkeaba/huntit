package com.ghostdev.huntit.navigation.graphs

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.ghostdev.huntit.navigation.NavDestinations
import com.ghostdev.huntit.ui.screens.onboarding.NewPasswordScreen
import com.ghostdev.huntit.ui.screens.onboarding.OnboardingScreen
import com.ghostdev.huntit.ui.screens.onboarding.ForgotPasswordScreen
import com.ghostdev.huntit.ui.screens.onboarding.SignInScreen
import com.ghostdev.huntit.ui.screens.onboarding.UserNameScreen
import org.koin.compose.koinInject

fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    innerPadding: PaddingValues,
    deepLinkAccessToken: String? = null,
    deepLinkRefreshToken: String? = null,
    expiresIn: Long? = null
) {
    navigation<NavDestinations.OnboardingGraph>(
        // Each navigation branch starts with a specific route defined here
        // onboarding flows use Onboarding as the entry, sign-in uses SignIn
        startDestination = NavDestinations.OnboardingGraph.Onboarding
    ) {
        composable<NavDestinations.OnboardingGraph.Onboarding> {
            OnboardingScreen(
                navigateToSignIn = {
                    navController.navigate(NavDestinations.OnboardingGraph.SignIn) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        composable<NavDestinations.OnboardingGraph.SignIn> {
            // Inject RoomCodeStorage to store room codes for navigation
            val roomCodeStorage = koinInject<com.ghostdev.huntit.data.local.RoomCodeStorage>()
            // Access SnackbarManager singleton directly instead of injecting it

            SignInScreen(
                innerPadding = innerPadding,
                navigateToResetPassword = {
                    navController.navigate(NavDestinations.OnboardingGraph.ResetPassword) {
                        launchSingleTop = true
                    }
                },
                navigateToUserName = {
                    navController.navigate(NavDestinations.OnboardingGraph.UserName) {
                        launchSingleTop = true
                    }
                },
                navigateToHome = {
                    navController.navigate(NavDestinations.HomeGraph) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph) { inclusive = true }
                    }
                },
                navigateToLobby = { roomCode ->
                    // Store room code before navigating
                    roomCodeStorage.setRoomCode(roomCode)
                    navController.navigate(NavDestinations.LobbyGraph.Lobby) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph) { inclusive = true }
                    }
                },
                navigateToGame = { roomCode ->
                    // Store room code before navigating
                    roomCodeStorage.setRoomCode(roomCode)
                    navController.navigate(NavDestinations.GameGraph.Game) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph) { inclusive = true }
                    }
                },
                navigateToLeaderboard = { roomCode ->
                    // Store room code before navigating
                    roomCodeStorage.setRoomCode(roomCode)
                    navController.navigate(NavDestinations.GameOverGraph.GameOver) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph) { inclusive = true }
                    }
                },
                showGameEndedMessage = {
                    com.ghostdev.huntit.utils.SnackbarManager.instance.showMessage("Your previous game has ended.")
                }
            )
        }

        composable<NavDestinations.OnboardingGraph.ResetPassword> {
            ForgotPasswordScreen(
                innerPadding = innerPadding,
                navigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<NavDestinations.OnboardingGraph.NewPassword> {
            NewPasswordScreen(
                innerPadding = innerPadding,
                accessToken = deepLinkAccessToken,
                refreshToken = deepLinkRefreshToken,
                expiresIn = expiresIn,
                navigateToLogin = {
                    navController.navigate(NavDestinations.OnboardingGraph.SignIn) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph) { inclusive = true }
                    }
                }
            )
        }

        composable<NavDestinations.OnboardingGraph.UserName> {
            UserNameScreen(
                innerPadding = innerPadding,
                navigateToHome = {
                    navController.navigate(NavDestinations.HomeGraph) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.OnboardingGraph.SignIn) { inclusive = true }
                    }
                }
            )
        }

    }
}