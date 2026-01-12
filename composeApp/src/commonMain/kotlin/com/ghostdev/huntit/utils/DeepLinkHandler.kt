package com.ghostdev.huntit.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import kotlin.jvm.JvmName

/**
 * Handles deep links in a platform-independent way
 */
class DeepLinkHandler {
    val accessToken = mutableStateOf<String?>(null)
    val refreshToken = mutableStateOf<String?>(null)
    val expiresIn = mutableStateOf<Long?>(null)

    fun handleResetPasswordDeepLink(uri: String) {
        println("===== FULL URL =====")
        println(uri)
        println("====================")

        if (uri.startsWith("huntit://reset-password")) {
            val fragmentIndex = uri.indexOf('#')
            if (fragmentIndex != -1) {
                val fragment = uri.substring(fragmentIndex + 1)

                // Parse as query parameters
                val params = fragment.split("&").associate { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }

                accessToken.value = params["access_token"]
                refreshToken.value = params["refresh_token"]
                expiresIn.value = params["expires_in"]?.toLongOrNull()

                println("Extracted access_token: ${accessToken.value?.take(50)}...")
                println("Extracted refresh_token: ${refreshToken.value}")
                println("Extracted expires_in: ${expiresIn.value}")
            }
        }
    }

    fun clearDeepLinkData() {
        accessToken.value = null
        refreshToken.value = null
        expiresIn.value = null
    }

    companion object {
        val instance = DeepLinkHandler()
        @JvmName("getHandlerInstance")
        fun getInstance(): DeepLinkHandler = instance
    }
}

// CompositionLocal to provide access to the DeepLinkHandler
val LocalDeepLinkHandler = compositionLocalOf { DeepLinkHandler.instance }