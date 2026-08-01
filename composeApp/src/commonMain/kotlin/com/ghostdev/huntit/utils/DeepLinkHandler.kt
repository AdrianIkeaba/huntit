package com.ghostdev.huntit.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import kotlin.jvm.JvmName

data class PasswordResetRequest(
    val recoveryCode: String? = null,
    val errorMessage: String? = null
)

/**
 * Handles deep links in a platform-independent way
 */
class DeepLinkHandler {
    val passwordResetRequest = mutableStateOf<PasswordResetRequest?>(null)

    fun handleResetPasswordDeepLink(uri: String) {
        val target = uri.substringBefore('#')
        val schemeSeparator = target.indexOf("://")
        if (schemeSeparator <= 0) return

        val scheme = target.substring(0, schemeSeparator)
        val remainder = target.substring(schemeSeparator + 3)
        val authority = remainder.substringBefore('/').substringBefore('?')

        if (!scheme.equals(RESET_SCHEME, ignoreCase = true) ||
            !authority.equals(RESET_HOST, ignoreCase = true)
        ) {
            return
        }

        // Recovery links must use PKCE and carry only a short-lived authorization
        // code. Legacy implicit-flow links contain reusable session credentials in
        // the fragment and are deliberately rejected.
        if (uri.substringAfter('#', missingDelimiterValue = "")
                .contains("access_token=", ignoreCase = true)
        ) {
            passwordResetRequest.value = invalidOrExpiredRequest()
            return
        }

        val parameters = target.substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .mapNotNull { parameter ->
                val parts = parameter.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

        if (parameters.containsKey("error") || parameters.containsKey("error_code")) {
            passwordResetRequest.value = invalidOrExpiredRequest()
            return
        }

        val recoveryCode = parameters["code"]
            ?.takeIf { it.length in MIN_CODE_LENGTH..MAX_CODE_LENGTH }
            ?.takeIf { code -> code.all { it.isLetterOrDigit() || it in CODE_PUNCTUATION } }

        passwordResetRequest.value = if (recoveryCode != null) {
            PasswordResetRequest(recoveryCode = recoveryCode)
        } else {
            invalidOrExpiredRequest()
        }
    }

    fun clearDeepLinkData() {
        passwordResetRequest.value = null
    }

    companion object {
        private const val RESET_SCHEME = "huntit"
        private const val RESET_HOST = "reset-password"
        private const val MIN_CODE_LENGTH = 16
        private const val MAX_CODE_LENGTH = 2048
        private val CODE_PUNCTUATION = setOf('-', '_', '.', '~')

        private fun invalidOrExpiredRequest() = PasswordResetRequest(
            errorMessage = "This reset link is invalid or expired. Request a new one."
        )

        val instance = DeepLinkHandler()

        @JvmName("getHandlerInstance")
        fun getInstance(): DeepLinkHandler = instance
    }
}

// CompositionLocal to provide access to the DeepLinkHandler
val LocalDeepLinkHandler = compositionLocalOf { DeepLinkHandler.instance }
