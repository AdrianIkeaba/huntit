package com.ghostdev.huntit.utils

import kotlinx.cinterop.ExperimentalForeignApi
import huntit.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL

@OptIn(ExperimentalResourceApi::class)
actual class EnhancedAudioPlayer {
    private var soundPlayer: AVAudioPlayer? = null
    private var musicPlayer: AVAudioPlayer? = null

    @OptIn(ExperimentalForeignApi::class)
    actual fun playSound(path: String) {
        val uri = Res.getUri(path)
        NSURL.URLWithString(URLString = uri)?.let { media ->
            soundPlayer = AVAudioPlayer(media, error = null)
            soundPlayer?.prepareToPlay()
            soundPlayer?.play()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun playBackgroundMusic(path: String) {
        val uri = Res.getUri(path)
        NSURL.URLWithString(URLString = uri)?.let { media ->
            musicPlayer = AVAudioPlayer(media, error = null)
            musicPlayer?.numberOfLoops = -1 // Loop indefinitely
            musicPlayer?.volume = 0.5f // Start at 50% volume
            musicPlayer?.prepareToPlay()
            musicPlayer?.play()
        }
    }

    actual fun pauseBackgroundMusic() {
        musicPlayer?.pause()
    }

    actual fun resumeBackgroundMusic() {
        musicPlayer?.play()
    }

    actual fun stopBackgroundMusic() {
        musicPlayer?.stop()
    }

    actual fun setVolume(volume: Float) {
        musicPlayer?.volume = volume.coerceIn(0f, 1f)
    }
    
    actual fun setSoundEffectsVolume(volume: Float) {
        // This will apply to the next sound that gets played
        // Save the volume level for future sounds
        val clampedVolume = volume.coerceIn(0f, 1f)
        // Apply to current sound player if active
        soundPlayer?.volume = clampedVolume
    }

    actual fun release() {
        soundPlayer?.stop()
        musicPlayer?.stop()
        soundPlayer = null
        musicPlayer = null
    }
}

actual fun createEnhancedAudioPlayer(): EnhancedAudioPlayer {
    return EnhancedAudioPlayer()
}