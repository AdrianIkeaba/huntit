package com.ghostdev.huntit.utils

expect class AudioPlayer {
    fun playSound(path: String)
    fun release()
}

expect fun createAudioPlayer(): AudioPlayer