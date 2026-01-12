package com.ghostdev.huntit.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry


@OptIn(ExperimentalComposeUiApi::class)
actual fun String.toClipEntry() =
    ClipEntry.withPlainText(this)
