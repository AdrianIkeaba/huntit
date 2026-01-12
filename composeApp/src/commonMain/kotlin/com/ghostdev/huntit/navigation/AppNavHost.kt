package com.ghostdev.huntit.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ghostdev.huntit.navigation.graphs.gameGraph
import com.ghostdev.huntit.navigation.graphs.gameOverGraph
import com.ghostdev.huntit.navigation.graphs.homeGraph
import com.ghostdev.huntit.navigation.graphs.lobbyGraph
import com.ghostdev.huntit.navigation.graphs.onboardingGraph
import com.ghostdev.huntit.navigation.graphs.splashGraph
import com.ghostdev.huntit.utils.LocalDeepLinkHandler
import kotlin.math.exp

@Composable
fun AppNavHost(
    innerPadding: PaddingValues = PaddingValues(),
    deepLinkAccessToken: String? = null
) {
    val navController = rememberNavController()
    val deepLinkHandler = LocalDeepLinkHandler.current

    val accessToken = deepLinkAccessToken ?: deepLinkHandler.accessToken.value
    val refreshToken = deepLinkHandler.refreshToken.value
    val expiresIn = deepLinkHandler.expiresIn.value

    LaunchedEffect(accessToken) {
        if (accessToken != null) {
            navController.navigate(NavDestinations.OnboardingGraph.NewPassword)
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavDestinations.SplashGraph,
    ) {
        splashGraph(navController, innerPadding)
        onboardingGraph(navController, innerPadding, accessToken, refreshToken, expiresIn)
        homeGraph(navController, innerPadding)
        lobbyGraph(navController, innerPadding)
        gameGraph(navController, innerPadding)
        gameOverGraph(navController, innerPadding)
    }
}