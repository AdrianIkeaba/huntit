package com.ghostdev.huntit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ghostdev.huntit.di.androidPlatformModule
import com.ghostdev.huntit.di.dataModule
import com.ghostdev.huntit.di.supabaseModule
import com.ghostdev.huntit.di.submissionModule
import com.ghostdev.huntit.di.viewModelModule
import com.ghostdev.huntit.utils.DeepLinkHandler
import com.ghostdev.huntit.utils.LocalAudioPlayer
import com.ghostdev.huntit.utils.createEnhancedAudioPlayer
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

class MainActivity : ComponentActivity() {
    companion object {
        private lateinit var instance: MainActivity
        val deepLinkAccessToken = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        instance = this
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(
                    supabaseModule, 
                    dataModule, 
                    viewModelModule, 
                    submissionModule,
                    androidPlatformModule(applicationContext)
                )
            }
        }

        intent?.let { handleIntent(it) }

        setContent {
            val audioPlayer = remember { createEnhancedAudioPlayer(this) }
            val lifecycleOwner = LocalLifecycleOwner.current

            // Initialize sound settings
            LaunchedEffect(Unit) {
                val prefManager = get<com.ghostdev.huntit.data.local.PreferencesManager>(com.ghostdev.huntit.data.local.PreferencesManager::class.java)

                val bgMusicEnabledValue = prefManager.isBackgroundMusicEnabled()
                val soundEffectsEnabledValue = prefManager.isSoundEffectsEnabled()
                val musicVolumeValue = prefManager.getMusicVolume()
                val soundEffectsVolumeValue = prefManager.getSoundEffectsVolume()

                audioPlayer.setVolume(musicVolumeValue)
                audioPlayer.setSoundEffectsVolume(soundEffectsVolumeValue)

                if (bgMusicEnabledValue) {
                    audioPlayer.playBackgroundMusic("files/background_music.mp3")
                }

            }

            // Handle lifecycle events
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    val prefManager = get<com.ghostdev.huntit.data.local.PreferencesManager>(com.ghostdev.huntit.data.local.PreferencesManager::class.java)
                    
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            val isMusicEnabled = prefManager.isBackgroundMusicEnabled()
                            
                            // Only resume if background music is enabled
                            if (isMusicEnabled) {
                                audioPlayer.resumeBackgroundMusic()
                            }
                        }

                        Lifecycle.Event.ON_STOP -> {
                            // Always pause when app goes to background
                            audioPlayer.pauseBackgroundMusic()
                        }

                        else -> {}
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    audioPlayer.release()
                }
            }

            CompositionLocalProvider(LocalAudioPlayer provides audioPlayer) {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "huntit" && uri.host == "reset-password") {
                val fullUri = uri.toString()
                DeepLinkHandler.instance.handleResetPasswordDeepLink(fullUri)

                uri.fragment?.let { fragment ->
                    val accessTokenParam = "access_token="
                    if (fragment.contains(accessTokenParam)) {
                        val startIndex =
                            fragment.indexOf(accessTokenParam) + accessTokenParam.length
                        val endIndex =
                            fragment.indexOf("&", startIndex).takeIf { it >= 0 } ?: fragment.length
                        val accessToken = fragment.substring(startIndex, endIndex)

                        // Save the token to be used in the App
                        deepLinkAccessToken.value = accessToken
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}