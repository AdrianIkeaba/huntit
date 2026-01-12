package com.ghostdev.huntit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.ghostdev.huntit.di.dataModule
import com.ghostdev.huntit.di.supabaseModule
import com.ghostdev.huntit.di.submissionModule
import com.ghostdev.huntit.di.viewModelModule
import com.ghostdev.huntit.utils.LocalAudioPlayer
import com.ghostdev.huntit.utils.createEnhancedAudioPlayer
import com.ghostdev.huntit.utils.initializeCrashLogger
import org.koin.core.context.startKoin
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

fun MainViewController() = ComposeUIViewController {
    // Initialize crash logger first thing before any other code executes
    // This ensures we catch any early initialization errors
    NSLog("📱 Initializing Kotlin crash logger")
    initializeCrashLogger()
    NSLog("📱 Crash logger initialized successfully")
    
    try {
        startKoin {
            modules(
                supabaseModule, 
                dataModule, 
                viewModelModule, 
                submissionModule
                // Add any platform-specific modules here if needed in the future
            )
        }
        NSLog("📱 Koin initialization successful")
    } catch (e: Exception) {
        NSLog("⚠️ Error during Koin initialization: ${e.message}")
        throw e // Re-throw to let the crash logger catch it
    }

    // Safely create audio player with error logging
    val audioPlayer = remember { 
        try {
            NSLog("📱 Creating audio player")
            createEnhancedAudioPlayer().also {
                NSLog("📱 Audio player created successfully")
            }
        } catch (e: Exception) {
            NSLog("⚠️ Error creating audio player: ${e.message}")
            throw e // Re-throw to let the crash logger catch it
        }
    }

    // Handle iOS lifecycle
    DisposableEffect(Unit) {
        val notificationCenter = NSNotificationCenter.defaultCenter

        val backgroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null
        ) { _ ->
            audioPlayer.pauseBackgroundMusic()
        }

        val foregroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = null
        ) { _ ->
            audioPlayer.resumeBackgroundMusic()
        }

        onDispose {
            notificationCenter.removeObserver(backgroundObserver)
            notificationCenter.removeObserver(foregroundObserver)
            audioPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        audioPlayer.playBackgroundMusic("files/background_music.mp3")
    }

    CompositionLocalProvider(LocalAudioPlayer provides audioPlayer) {
        App()
    }
}