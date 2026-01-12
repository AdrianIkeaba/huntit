package com.ghostdev.huntit.utils

import androidx.compose.ui.platform.toClipEntry


actual fun String.toClipEntry() =
    android.content.ClipData.newPlainText(this, this).toClipEntry()
