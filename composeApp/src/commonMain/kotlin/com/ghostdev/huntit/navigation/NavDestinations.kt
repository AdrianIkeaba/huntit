package com.ghostdev.huntit.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class NavDestinations {

    @Serializable
    data object SplashGraph {
        @Serializable
        data object Splash
    }

    @Serializable
    data object OnboardingGraph {

        @Serializable
        data object Onboarding

        @Serializable
        data object SignIn

        @Serializable
        data object ResetPassword

        @Serializable
        data object NewPassword

        @Serializable
        data object UserName
    }

    @Serializable
    data object HomeGraph {

        @Serializable
        data object Home

        @Serializable
        data object CreateGameRoom

        @Serializable
        data object PastGames

        @Serializable
        data object PublicGames
    }

    @Serializable
    data object LobbyGraph {

        @Serializable
        data object Lobby

        @Serializable
        data object GameSettings

    }

    @Serializable
    data object GameGraph {

        @Serializable
        data object Game

        @Serializable
        data object Photo

        @Serializable
        data object Review
    }

    @Serializable
    data object GameOverGraph {

        @Serializable
        data object GameOver
    }


}