package com.ghostdev.huntit.di

import com.ghostdev.huntit.BuildKonfig
import com.ghostdev.huntit.data.local.PreferencesManager
import com.ghostdev.huntit.data.local.RoomCodeStorage
import com.ghostdev.huntit.data.repository.AuthRepository
import com.ghostdev.huntit.data.repository.AuthRepositoryImpl
import com.ghostdev.huntit.data.repository.GameRepository
import com.ghostdev.huntit.data.repository.GameRepositoryImpl
import com.ghostdev.huntit.data.repository.GameSetupRepository
import com.ghostdev.huntit.data.repository.GameSetupRepositoryImpl
import com.ghostdev.huntit.data.repository.LeaderboardRepository
import com.ghostdev.huntit.data.repository.LeaderboardRepositoryImpl
import com.ghostdev.huntit.data.repository.SoundSettingsRepository
import com.ghostdev.huntit.data.repository.DefaultSoundSettingsRepositoryImpl
import com.ghostdev.huntit.data.repository.SubmissionRepository
import com.ghostdev.huntit.data.repository.SubmissionRepositoryImpl
import com.ghostdev.huntit.ui.screens.game.GameViewModel
import com.ghostdev.huntit.ui.screens.game.SubmissionViewModel
import com.ghostdev.huntit.ui.screens.gameover.GameOverViewModel
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.screens.history.PastGamesViewModel
import com.ghostdev.huntit.ui.screens.home.CreateGameRoomViewModel
import com.ghostdev.huntit.ui.screens.home.JoinGameViewModel
import com.ghostdev.huntit.ui.screens.home.PublicGamesViewModel
import com.ghostdev.huntit.ui.screens.home.SoundSettingsViewModel
import com.ghostdev.huntit.ui.screens.lobby.LobbyViewModel
import com.ghostdev.huntit.ui.screens.lobby.GameSettingsViewModel
import com.ghostdev.huntit.ui.screens.onboarding.ForgotPasswordViewModel
import com.ghostdev.huntit.ui.screens.onboarding.NewPasswordViewModel
import com.ghostdev.huntit.ui.screens.onboarding.OnboardingViewModel
import com.ghostdev.huntit.ui.screens.onboarding.SignInViewModel
import com.ghostdev.huntit.ui.screens.onboarding.UserNameViewModel
import com.ghostdev.huntit.ui.screens.splash.SplashViewModel
import com.ghostdev.huntit.utils.ImageProcessor
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val supabaseModule = module {
    single { createSupabaseClient() }
}

val dataModule = module {
    single { Settings() }
    singleOf(::PreferencesManager)
    singleOf(::RoomCodeStorage)
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::GameSetupRepositoryImpl) bind GameSetupRepository::class
    singleOf(::GameRepositoryImpl) bind GameRepository::class
    singleOf(::SubmissionRepositoryImpl) bind SubmissionRepository::class
    singleOf(::LeaderboardRepositoryImpl) bind LeaderboardRepository::class
    singleOf(::DefaultSoundSettingsRepositoryImpl) bind SoundSettingsRepository::class
}

val viewModelModule = module {
    viewModelOf(::SplashViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::UserNameViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::NewPasswordViewModel)
    factory { OnboardingViewModel(get()) }
    viewModelOf(::HomeViewModel)
    viewModelOf(::CreateGameRoomViewModel)
    viewModelOf(::JoinGameViewModel)
    viewModelOf(::PublicGamesViewModel)
    viewModelOf(::LobbyViewModel)
    viewModelOf(::GameSettingsViewModel)
    viewModelOf(::GameViewModel)
    viewModelOf(::GameOverViewModel)
    viewModelOf(::PastGamesViewModel)
    viewModelOf(::SoundSettingsViewModel)
}


fun createSupabaseClient(): SupabaseClient {
    return createSupabaseClient(
        supabaseUrl = BuildKonfig.SUPABASE_URL,
        supabaseKey = BuildKonfig.SUPABASE_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
        install(Realtime)
    }
}