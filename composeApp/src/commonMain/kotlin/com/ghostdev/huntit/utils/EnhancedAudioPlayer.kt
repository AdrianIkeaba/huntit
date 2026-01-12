package com.ghostdev.huntit.utils

expect class EnhancedAudioPlayer {
    fun playSound(path: String)
    fun playBackgroundMusic(path: String)
    fun pauseBackgroundMusic()
    fun resumeBackgroundMusic()
    fun stopBackgroundMusic()
    fun setVolume(volume: Float) // Sets background music volume
    fun setSoundEffectsVolume(volume: Float) // Sets sound effects volume
    fun release()
}

expect fun createEnhancedAudioPlayer(): EnhancedAudioPlayer