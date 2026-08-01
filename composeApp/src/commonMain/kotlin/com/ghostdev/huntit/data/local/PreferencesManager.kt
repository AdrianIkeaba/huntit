package com.ghostdev.huntit.data.local

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

class PreferencesManager(private val settings: Settings) {

    fun clearLegacyAuthState() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_IS_LOGGED_IN)
    }

    fun saveUserId(userId: String) {
        settings[KEY_USER_ID] = userId
    }

    fun getUserId(): String? {
        return settings.getStringOrNull(KEY_USER_ID)
    }

    fun saveDisplayName(name: String) {
        settings[KEY_DISPLAY_NAME] = name
    }

    fun getDisplayName(): String {
        return settings.getStringOrNull(KEY_DISPLAY_NAME) ?: ""
    }

    fun saveAvatarId(id: Int) {
        settings[KEY_AVATAR_ID] = id
    }

    fun getAvatarId(): Int {
        return settings.getInt(KEY_AVATAR_ID, 1) // Default to avatar 1
    }

    fun saveEmail(email: String) {
        settings[KEY_EMAIL] = email
    }

    fun getEmail(): String {
        return settings.getStringOrNull(KEY_EMAIL) ?: ""
    }

    fun hasCompletedProfile(): Boolean {
        return settings.getBoolean(KEY_PROFILE_COMPLETED, false)
    }

    fun setProfileCompleted(completed: Boolean) {
        settings[KEY_PROFILE_COMPLETED] = completed
    }

    fun hasCompletedOnboarding(): Boolean {
        return settings.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        settings[KEY_ONBOARDING_COMPLETED] = completed
    }

    fun clearAll() {
        settings.clear()
    }

    fun saveBackgroundMusicEnabled(enabled: Boolean) {
        settings[KEY_BACKGROUND_MUSIC_ENABLED] = enabled
    }

    fun isBackgroundMusicEnabled(): Boolean {
        return settings.getBoolean(KEY_BACKGROUND_MUSIC_ENABLED, true) // Default to true
    }

    fun saveSoundEffectsEnabled(enabled: Boolean) {
        settings[KEY_SOUND_EFFECTS_ENABLED] = enabled
    }

    fun isSoundEffectsEnabled(): Boolean {
        return settings.getBoolean(KEY_SOUND_EFFECTS_ENABLED, true) // Default to true
    }

    fun saveMusicVolume(volume: Float) {
        settings[KEY_MUSIC_VOLUME] = volume
    }

    fun getMusicVolume(): Float {
        return settings.getFloat(KEY_MUSIC_VOLUME, 0.5f) // Default to 50%
    }
    
    fun saveSoundEffectsVolume(volume: Float) {
        settings[KEY_SOUND_EFFECTS_VOLUME] = volume
    }
    
    fun getSoundEffectsVolume(): Float {
        return settings.getFloat(KEY_SOUND_EFFECTS_VOLUME, 0.5f) // Default to 50%
    }

    companion object {
        // Removed in release order 4. Retained only so upgrades can delete tokens
        // written by older builds; Supabase Auth owns session persistence now.
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_ID = "avatar_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_PROFILE_COMPLETED = "profile_completed"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        
        // Sound settings keys
        private const val KEY_BACKGROUND_MUSIC_ENABLED = "background_music_enabled"
        private const val KEY_SOUND_EFFECTS_ENABLED = "sound_effects_enabled"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_SOUND_EFFECTS_VOLUME = "sound_effects_volume"
    }
}
