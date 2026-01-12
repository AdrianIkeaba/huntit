package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementation of SoundSettingsRepository that uses PreferencesManager for storage.
 * This is the default implementation used for non-Android platforms.
 */
class DefaultSoundSettingsRepositoryImpl(
    private val preferencesManager: PreferencesManager
) : SoundSettingsRepository {
    
    // StateFlows to observe changes to sound settings
    private val _backgroundMusicEnabled = MutableStateFlow(preferencesManager.isBackgroundMusicEnabled())
    private val _soundEffectsEnabled = MutableStateFlow(preferencesManager.isSoundEffectsEnabled())
    private val _musicVolume = MutableStateFlow(preferencesManager.getMusicVolume())
    private val _soundEffectsVolume = MutableStateFlow(preferencesManager.getSoundEffectsVolume())
    
    init {
        // Load initial values from preferences
        _backgroundMusicEnabled.value = preferencesManager.isBackgroundMusicEnabled()
        _soundEffectsEnabled.value = preferencesManager.isSoundEffectsEnabled()
        _musicVolume.value = preferencesManager.getMusicVolume()
        _soundEffectsVolume.value = preferencesManager.getSoundEffectsVolume()
    }
    
    override fun getBackgroundMusicEnabled(): Flow<Boolean> = _backgroundMusicEnabled.asStateFlow()
    
    override suspend fun setBackgroundMusicEnabled(enabled: Boolean) {
        preferencesManager.saveBackgroundMusicEnabled(enabled)
        _backgroundMusicEnabled.update { enabled }
        applyBackgroundMusicSettings()
    }
    
    override fun getSoundEffectsEnabled(): Flow<Boolean> = _soundEffectsEnabled.asStateFlow()
    
    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        preferencesManager.saveSoundEffectsEnabled(enabled)
        _soundEffectsEnabled.update { enabled }
    }
    
    override fun getMusicVolume(): Flow<Float> = _musicVolume.asStateFlow()
    
    override suspend fun setMusicVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        preferencesManager.saveMusicVolume(clampedVolume)
        _musicVolume.update { clampedVolume }
        // Apply volume changes if music is enabled
        if (_backgroundMusicEnabled.value) {
            applyBackgroundMusicSettings()
        }
    }
    
    override suspend fun applyBackgroundMusicSettings() {
        // This is a platform-specific implementation that would handle
        // actual audio playback. In each platform-specific implementation,
        // this would interact with the respective audio playback system.
    }
    
    override fun getSoundEffectsVolume(): Flow<Float> = _soundEffectsVolume.asStateFlow()
    
    override suspend fun setSoundEffectsVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        preferencesManager.saveSoundEffectsVolume(clampedVolume)
        _soundEffectsVolume.update { clampedVolume }
    }
    
    override suspend fun applyAllSoundSettings() {
        applyBackgroundMusicSettings()
    }
}