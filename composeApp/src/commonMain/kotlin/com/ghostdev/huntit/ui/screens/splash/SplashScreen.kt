package com.ghostdev.huntit.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.ghostdev.huntit.ui.theme.BackgroundColor
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.hunt_it_logo_full
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SplashScreen(
    innerPadding: PaddingValues,
    navigateToOnboarding: () -> Unit,
    navigateToSignIn: () -> Unit, // Add new parameter for direct SignIn navigation
    navigateToUserName: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToLobby: (String) -> Unit,
    navigateToGame: (String) -> Unit,
    navigateToGameOver: (String) -> Unit,
    showGameEndedMessage: () -> Unit,
    viewModel: SplashViewModel = koinViewModel()
) {
    val destination by viewModel.destination.collectAsState()

    LaunchedEffect(destination) {
        destination?.let {
            when (it) {
                // First time users see the full onboarding flow
                SplashDestination.Onboarding -> navigateToOnboarding()

                // Returning users skip directly to sign-in
                SplashDestination.SignIn -> {
                    println("DEBUG: Navigating directly to SignIn screen")
                    // Now we have a direct navigation function for the SignIn destination
                    navigateToSignIn()
                }
                SplashDestination.UserName -> navigateToUserName()
                SplashDestination.Home -> navigateToHome()
                is SplashDestination.Lobby -> navigateToLobby(it.roomCode)
                is SplashDestination.Game -> navigateToGame(it.roomCode)
                is SplashDestination.GameOver -> navigateToGameOver(it.roomCode)
                SplashDestination.ShowEndedGameNotification -> {
                    showGameEndedMessage()
                    navigateToHome()
                }
            }
        }
    }

    SplashComponent(innerPadding = innerPadding)
}

@Composable
fun SplashComponent(
    innerPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier
                .scale(1f),
            painter = painterResource(Res.drawable.hunt_it_logo_full),
            contentDescription = "Hunt It Logo"
        )
    }
}