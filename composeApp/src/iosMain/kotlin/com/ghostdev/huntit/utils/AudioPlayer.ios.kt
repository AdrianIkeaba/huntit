package com.ghostdev.huntit.utils

import kotlinx.cinterop.ExperimentalForeignApi
import huntit.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL

@OptIn(ExperimentalResourceApi::class)
actual class AudioPlayer {

    @OptIn(ExperimentalForeignApi::class)
    actual fun playSound(path: String) {
        val uri = Res.getUri(path)
        NSURL.URLWithString(URLString = uri)?.let { media ->
            val avAudioPlayer = AVAudioPlayer(media, error = null)
            avAudioPlayer.prepareToPlay()
            avAudioPlayer.play()
        }
    }

    actual fun release() {

    }
}

actual fun createAudioPlayer(): AudioPlayer {
    return AudioPlayer()
}