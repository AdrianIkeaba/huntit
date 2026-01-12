package com.ghostdev.huntit.navigation.graphs

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.ghostdev.huntit.navigation.NavDestinations
import com.ghostdev.huntit.ui.screens.lobby.GameSettingsScreen
import com.ghostdev.huntit.ui.screens.lobby.LobbyScreen


fun NavGraphBuilder.lobbyGraph(
    navController: NavController,
    innerPadding: PaddingValues
) {
    navigation<NavDestinations.LobbyGraph>(
        startDestination = NavDestinations.LobbyGraph.Lobby
    ) {
        composable<NavDestinations.LobbyGraph.Lobby> {
            LobbyScreen(
                innerPadding = innerPadding,
                navigateToGame = {
                    navController.navigate(NavDestinations.GameGraph.Game) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.LobbyGraph) {
                            inclusive = true
                        }
                    }
                },
                navigateToSettings = {
                    navController.navigate(NavDestinations.LobbyGraph.GameSettings) {
                        launchSingleTop = true
                    }
                },
                navigateToHome = {
                    navController.navigate(NavDestinations.HomeGraph.Home) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.LobbyGraph) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable<NavDestinations.LobbyGraph.GameSettings> {
            GameSettingsScreen(
                innerPadding = innerPadding,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}