package com.ghostdev.huntit.navigation.graphs

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.ghostdev.huntit.navigation.NavDestinations
import com.ghostdev.huntit.ui.screens.gameover.GameOverScreen

fun NavGraphBuilder.gameOverGraph(
    navController: NavController,
    innerPadding: PaddingValues
) {
    navigation<NavDestinations.GameOverGraph>(
        startDestination = NavDestinations.GameOverGraph.GameOver
    ) {
        composable<NavDestinations.GameOverGraph.GameOver> {
            GameOverScreen(
                innerPadding = innerPadding,
                navigateToHome = {
                    navController.navigate(NavDestinations.HomeGraph.Home) {
                        launchSingleTop = true
                        popUpTo(NavDestinations.HomeGraph) {
                            inclusive = false
                        }
                    }
                }
            )
        }


    }
}