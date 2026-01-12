package com.ghostdev.huntit.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ghostdev.huntit.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Android-specific implementation of SoundSettingsRepository.
 * Handles actual audio playback using MediaPlayer.
 */
class AndroidSoundSettingsRepositoryImpl(
    private val preferencesManager: PreferencesManager,
    private val applicationContext: Context
) : SoundSettingsRepository {
    
    // StateFlows to observe changes to sound settings
    private val _backgroundMusicEnabled = MutableStateFlow(preferencesManager.isBackgroundMusicEnabled())
    private val _soundEffectsEnabled = MutableStateFlow(preferencesManager.isSoundEffectsEnabled())
    private val _musicVolume = MutableStateFlow(preferencesManager.getMusicVolume())
    private val _soundEffectsVolume = MutableStateFlow(preferencesManager.getSoundEffectsVolume())
    
    // MediaPlayer for background music
    private var backgroundMusicPlayer: MediaPlayer? = null
    
    // Resource ID for background music
    private var backgroundMusicResId: Int = 0 // Set this to your actual music resource ID
    
    init {
        // Load initial values from preferences
        _backgroundMusicEnabled.value = preferencesManager.isBackgroundMusicEnabled()
        _soundEffectsEnabled.value = preferencesManager.isSoundEffectsEnabled()
        _musicVolume.value = preferencesManager.getMusicVolume()
        _soundEffectsVolume.value = preferencesManager.getSoundEffectsVolume()
        
        // Set up background music (can be called in a lifecycle-aware component)
        setupBackgroundMusic()
    }
    
    private fun setupBackgroundMusic() {
        try {
            // Initialize MediaPlayer
            // This is just a placeholder; you'd need to replace with actual resource
            // backgroundMusicResId = R.raw.background_music
            
            // For testing, we won't initialize the actual player until we have a real resource
            /*
            backgroundMusicPlayer = MediaPlayer.create(applicationContext, backgroundMusicResId).apply {
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setVolume(_musicVolume.value, _musicVolume.value)
            }
            */
        } catch (e: Exception) {
            e.printStackTrace()
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
        
        // Update volume if player exists
        backgroundMusicPlayer?.setVolume(clampedVolume, clampedVolume)
    }
    
    override suspend fun applyBackgroundMusicSettings() {
        val isEnabled = _backgroundMusicEnabled.value
        
        if (backgroundMusicPlayer == null && isEnabled) {
            // Lazily initialize the player if it doesn't exist yet and music is enabled
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
            e.printStackTrace()
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
        
        // Apply sound effects settings
        // Sound effects are applied per-playback, so we don't need to do anything here
        // but we might want to ensure our AudioPlayer instances get the updated settings
    }
    
    // Clean up resources when no longer needed
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