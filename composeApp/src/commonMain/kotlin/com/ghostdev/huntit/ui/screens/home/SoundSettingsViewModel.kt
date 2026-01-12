package com.ghostdev.huntit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.repository.SoundSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SoundSettingsViewModel(
    private val soundSettingsRepository: SoundSettingsRepository
) : ViewModel() {
    
    // State flows for the UI
    val backgroundMusicEnabled = soundSettingsRepository.getBackgroundMusicEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
        
    val soundEffectsEnabled = soundSettingsRepository.getSoundEffectsEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
        
    val musicVolume = soundSettingsRepository.getMusicVolume()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.5f
        )
        
    val soundEffectsVolume = soundSettingsRepository.getSoundEffectsVolume()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.5f
        )
    
    init {
        viewModelScope.launch {
            soundSettingsRepository.applyAllSoundSettings()
        }
    }
    
    // Actions
    fun setBackgroundMusicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundSettingsRepository.setBackgroundMusicEnabled(enabled)
        }
    }
    
    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundSettingsRepository.setSoundEffectsEnabled(enabled)
        }
    }
    
    fun setMusicVolume(volume: Float) {
        viewModelScope.launch {
            soundSettingsRepository.setMusicVolume(volume)
        }
    }
    
    fun setSoundEffectsVolume(volume: Float) {
        viewModelScope.launch {
            soundSettingsRepository.setSoundEffectsVolume(volume)
        }
    }

    fun applyAllSettings() {
        viewModelScope.launch {
            soundSettingsRepository.applyAllSoundSettings()
        }
    }

}