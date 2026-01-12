package com.ghostdev.huntit.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ghostdev.huntit.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


class AndroidSoundSettingsRepositoryImpl(
    private val preferencesManager: PreferencesManager,
    private val applicationContext: Context
) : SoundSettingsRepository {
    

    private val _backgroundMusicEnabled = MutableStateFlow(preferencesManager.isBackgroundMusicEnabled())
    private val _soundEffectsEnabled = MutableStateFlow(preferencesManager.isSoundEffectsEnabled())
    private val _musicVolume = MutableStateFlow(preferencesManager.getMusicVolume())
    private val _soundEffectsVolume = MutableStateFlow(preferencesManager.getSoundEffectsVolume())
    

    private var backgroundMusicPlayer: MediaPlayer? = null
    

    private var backgroundMusicResId: Int = 0
    
    init {

        _backgroundMusicEnabled.value = preferencesManager.isBackgroundMusicEnabled()
        _soundEffectsEnabled.value = preferencesManager.isSoundEffectsEnabled()
        _musicVolume.value = preferencesManager.getMusicVolume()
        _soundEffectsVolume.value = preferencesManager.getSoundEffectsVolume()
        
        setupBackgroundMusic()
    }
    
    private fun setupBackgroundMusic() {
        try {

        } catch (e: Exception) {

        }
    }
    
    override fun getBackgroundMusicEnabled() = _backgroundMusicEnabled
    
    override suspend fun setBackgroundMusicEnabled(enabled: Boolean) {
        preferencesManager.saveBackgroundMusicEnabled(enabled)
        _backgroundMusicEnabled.update { enabled }
        applyBackgroundMusicSettings()
    }
    
    override fun getSoundEffectsEnabled() = _soundEffectsEnabled
    
    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        preferencesManager.saveSoundEffectsEnabled(enabled)
        _soundEffectsEnabled.update { enabled }
    }
    
    override fun getMusicVolume() = _musicVolume
    
    override suspend fun setMusicVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        preferencesManager.saveMusicVolume(clampedVolume)
        _musicVolume.update { clampedVolume }
        

        backgroundMusicPlayer?.setVolume(clampedVolume, clampedVolume)
    }
    
    override suspend fun applyBackgroundMusicSettings() {
        val isEnabled = _backgroundMusicEnabled.value
        
        if (backgroundMusicPlayer == null && isEnabled) {

            setupBackgroundMusic()
        }
        
        try {
            if (isEnabled) {
                backgroundMusicPlayer?.let { player ->
                    if (!player.isPlaying) {
                        player.start()
                    }
                    player.setVolume(_musicVolume.value, _musicVolume.value)
                }
            } else {
                backgroundMusicPlayer?.pause()
            }
        } catch (e: Exception) {

        }
    }
    
    override fun getSoundEffectsVolume() = _soundEffectsVolume
    
    override suspend fun setSoundEffectsVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        preferencesManager.saveSoundEffectsVolume(clampedVolume)
        _soundEffectsVolume.update { clampedVolume }
    }
    
    override suspend fun applyAllSoundSettings() {
        applyBackgroundMusicSettings()
        

    }
    

    fun release() {
        backgroundMusicPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        backgroundMusicPlayer = null
    }
}