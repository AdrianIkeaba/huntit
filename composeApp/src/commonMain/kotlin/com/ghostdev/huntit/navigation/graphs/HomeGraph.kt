package com.ghostdev.huntit.navigation.graphs

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.ghostdev.huntit.navigation.NavDestinations
import com.ghostdev.huntit.ui.screens.history.PastGamesScreen
import com.ghostdev.huntit.ui.screens.home.CreateGameRoomScreen
import com.ghostdev.huntit.ui.screens.home.HomeScreen
import com.ghostdev.huntit.ui.screens.home.PublicGamesScreen
import com.ghostdev.huntit.utils.SnackbarManager

fun NavGraphBuilder.homeGraph(
    navController: NavController,
    innerPadding: PaddingValues
) {
    navigation<NavDestinations.HomeGraph>(
        startDestination = NavDestinations.HomeGraph.Home
    ) {
        composable<NavDestinations.HomeGraph.Home> {
            HomeScreen(
                innerPadding = innerPadding,
                navigateToCreateGameRoom = {
                    navController.navigate(NavDestinations.HomeGraph.CreateGameRoom) {
                        launchSingleTop = true
                    }
                },
                navigateToLobby = { roomCode ->
                    // Navigate to the lobby after storing the room code
                    navController.navigate(NavDestinations.LobbyGraph.Lobby) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.HomeGraph.Home) {
                            inclusive = true
                        }
                    }
                },
                navigateToSignIn = {
                    navController.navigate(NavDestinations.OnboardingGraph.SignIn) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.HomeGraph) { inclusive = true }
                    }
                },
                navigateToPastGames = {
                    navController.navigate(NavDestinations.HomeGraph.PastGames) {
                        launchSingleTop = true
                    }
                },
                navigateToPublicGames = {
                    navController.navigate(NavDestinations.HomeGraph.PublicGames) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<NavDestinations.HomeGraph.CreateGameRoom> {
            CreateGameRoomScreen(
                innerPadding = innerPadding,
                navigateBack = {
                    navController.popBackStack()
                },
                navigateToLobby = { roomCode ->
                    // Navigate to the lobby after storing the room code
                    navController.navigate(NavDestinations.LobbyGraph.Lobby) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.HomeGraph.Home) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable<NavDestinations.HomeGraph.PastGames> {
            PastGamesScreen(
                innerPadding = innerPadding,
                navigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<NavDestinations.HomeGraph.PublicGames> {
            PublicGamesScreen(
                innerPadding = innerPadding,
                navigateBack = {
                    navController.popBackStack()
                },
                navigateToLobby = { roomCode ->
                    navController.navigate(NavDestinations.LobbyGraph.Lobby) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.HomeGraph.Home) {
                            inclusive = true
                        }
                    }
                }
            )
        }

    }
}