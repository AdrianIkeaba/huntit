package com.ghostdev.huntit.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import huntit.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
actual class AudioPlayer(context: Context) {
    private val mediaPlayer = ExoPlayer.Builder(context).build()

    init {
        mediaPlayer.prepare()
    }

    actual fun playSound(path: String) {
        val media = MediaItem.fromUri(Res.getUri(path))
        mediaPlayer.setMediaItem(media)
        mediaPlayer.prepare()
        mediaPlayer.play()
    }

    actual fun release() {
        mediaPlayer.release()
    }
}

actual fun createAudioPlayer(): AudioPlayer {
    // This will be called from Android-specific code
    error("Use createAudioPlayer(context) on Android")
}

fun createAudioPlayer(context: Context): AudioPlayer {
    return AudioPlayer(context)
}