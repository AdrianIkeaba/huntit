package com.ghostdev.huntit.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import huntit.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
actual class EnhancedAudioPlayer(context: Context) {
    private val soundPlayer = ExoPlayer.Builder(context).build()
    private val musicPlayer = ExoPlayer.Builder(context).build()

    init {
        soundPlayer.prepare()
        musicPlayer.prepare()
        musicPlayer.repeatMode = Player.REPEAT_MODE_ONE // Loop background music
    }

    actual fun playSound(path: String) {
        val media = MediaItem.fromUri(Res.getUri(path))
        soundPlayer.setMediaItem(media)
        soundPlayer.prepare()
        soundPlayer.play()
    }

    actual fun playBackgroundMusic(path: String) {
        val media = MediaItem.fromUri(Res.getUri(path))
        musicPlayer.setMediaItem(media)
        musicPlayer.prepare()
        musicPlayer.volume = 0.5f // Start at 50% volume
        musicPlayer.play()
    }

    actual fun pauseBackgroundMusic() {
        musicPlayer.pause()
    }

    actual fun resumeBackgroundMusic() {
        // Check if we have a media item before trying to play
        if (musicPlayer.mediaItemCount > 0) {
            // We have a media item, so we can resume
            musicPlayer.play()
        }
        // If there's no media item, playBackgroundMusic() should be called instead
    }

    actual fun stopBackgroundMusic() {
        musicPlayer.stop()
    }

    actual fun setVolume(volume: Float) {
        musicPlayer.volume = volume.coerceIn(0f, 1f)
    }
    
    actual fun setSoundEffectsVolume(volume: Float) {
        soundPlayer.volume = volume.coerceIn(0f, 1f)
    }

    actual fun release() {
        soundPlayer.release()
        musicPlayer.release()
    }
}

actual fun createEnhancedAudioPlayer(): EnhancedAudioPlayer {
    error("Use createEnhancedAudioPlayer(context) on Android")
}

fun createEnhancedAudioPlayer(context: Context): EnhancedAudioPlayer {
    return EnhancedAudioPlayer(context)
}