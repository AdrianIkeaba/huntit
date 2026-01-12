package com.ghostdev.huntit.navigation.graphs

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.navigation.NavDestinations
import com.ghostdev.huntit.ui.screens.splash.SplashScreen
import com.ghostdev.huntit.utils.SnackbarManager
import org.koin.compose.koinInject

fun NavGraphBuilder.splashGraph(
    navController: NavController,
    innerPadding: PaddingValues
) {
    navigation<NavDestinations.SplashGraph>(
        startDestination = NavDestinations.SplashGraph.Splash
    ) {
        composable<NavDestinations.SplashGraph.Splash> {
            val roomCodeStorage = koinInject<RoomCodeStorage>()

            SplashScreen(
                innerPadding = innerPadding,
                navigateToOnboarding = {
                    navController.navigate(NavDestinations.OnboardingGraph.Onboarding) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                navigateToSignIn = {
                    navController.navigate(NavDestinations.OnboardingGraph.SignIn) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                navigateToUserName = {
                    navController.navigate(NavDestinations.OnboardingGraph.UserName) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                navigateToHome = {
                    navController.navigate(NavDestinations.HomeGraph) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                navigateToLobby = { roomCode ->
                    // Store the room code before navigating
                    roomCodeStorage.setRoomCode(roomCode)
                    navController.navigate(NavDestinations.LobbyGraph.Lobby) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                navigateToGame = { roomCode ->
                    // Store the room code before navigating
                    roomCodeStorage.setRoomCode(roomCode)
                    navController.navigate(NavDestinations.GameGraph.Game) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                navigateToGameOver = { roomCode ->
                    // Store the room code before navigating to GameOverScreen
                    roomCodeStorage.setRoomCode(roomCode)
                    navController.navigate(NavDestinations.GameOverGraph.GameOver) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.SplashGraph) { inclusive = true }
                    }
                },
                showGameEndedMessage = {
                    // Store the game ended message in the SnackbarManager
                    SnackbarManager.instance.showMessage("Your previous game has ended.")
                }
            )
        }
    }
}