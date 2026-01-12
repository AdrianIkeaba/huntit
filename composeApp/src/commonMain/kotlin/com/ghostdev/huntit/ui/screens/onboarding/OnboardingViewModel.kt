package com.ghostdev.huntit.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _hasSeenOnboarding = MutableStateFlow(false)
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            _hasSeenOnboarding.value = preferencesManager.hasCompletedOnboarding()
        }
    }

    fun onOnboardingCompleted() {
        viewModelScope.launch {
            preferencesManager.setOnboardingCompleted(true)
            _hasSeenOnboarding.value = true
        }
    }
}