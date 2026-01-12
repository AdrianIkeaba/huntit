package com.ghostdev.huntit.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for handling sound settings in the app.
 */
interface SoundSettingsRepository {
    /**
     * Gets whether background music is enabled as a Flow
     */
    fun getBackgroundMusicEnabled(): Flow<Boolean>
    
    /**
     * Sets whether background music is enabled
     */
    suspend fun setBackgroundMusicEnabled(enabled: Boolean)
    
    /**
     * Gets whether sound effects are enabled as a Flow
     */
    fun getSoundEffectsEnabled(): Flow<Boolean>
    
    /**
     * Sets whether sound effects are enabled
     */
    suspend fun setSoundEffectsEnabled(enabled: Boolean)
    
    /**
     * Gets the music volume as a Flow (0.0f to 1.0f)
     */
    fun getMusicVolume(): Flow<Float>
    
    /**
     * Sets the music volume (0.0f to 1.0f)
     */
    suspend fun setMusicVolume(volume: Float)
    
    /**
     * Gets the sound effects volume as a Flow (0.0f to 1.0f)
     */
    fun getSoundEffectsVolume(): Flow<Float>
    
    /**
     * Sets the sound effects volume (0.0f to 1.0f)
     */
    suspend fun setSoundEffectsVolume(volume: Float)
    
    /**
     * Plays or pauses background music based on current settings
     */
    suspend fun applyBackgroundMusicSettings()
    
    /**
     * Applies all sound settings (useful at app start)
     */
    suspend fun applyAllSoundSettings()
}