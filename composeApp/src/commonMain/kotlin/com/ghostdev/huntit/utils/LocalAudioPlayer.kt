package com.ghostdev.huntit.utils

import androidx.compose.runtime.staticCompositionLocalOf


val LocalAudioPlayer = staticCompositionLocalOf<EnhancedAudioPlayer?> {
    null
}