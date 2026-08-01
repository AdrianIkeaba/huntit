package com.ghostdev.huntit.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GameThemeTest {

    @Test
    fun fashionThemeKeepsItsOriginalDisplayNameAndBackendValue() {
        assertEquals("Fashion and Style", GameTheme.FASHION_STYLE.displayName)
        assertEquals("\"fashion_style\"", Json.encodeToString(GameTheme.FASHION_STYLE))
    }
}
