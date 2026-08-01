package com.ghostdev.huntit.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeepLinkHandlerTest {

    @Test
    fun acceptsPkceRecoveryCodeFromExactResetDestination() {
        val handler = DeepLinkHandler()
        val code = "abcdefghijklmnopqrstuvwxyz_ABCDEF-1234567890"

        handler.handleResetPasswordDeepLink("huntit://reset-password?code=$code")

        assertEquals(code, handler.passwordResetRequest.value?.recoveryCode)
        assertNull(handler.passwordResetRequest.value?.errorMessage)
    }

    @Test
    fun ignoresLookalikeSchemeAndHost() {
        val handler = DeepLinkHandler()

        handler.handleResetPasswordDeepLink(
            "huntit://reset-password.attacker.example?code=abcdefghijklmnopqrstuvwxyz"
        )
        assertNull(handler.passwordResetRequest.value)

        handler.handleResetPasswordDeepLink(
            "huntit-attacker://reset-password?code=abcdefghijklmnopqrstuvwxyz"
        )
        assertNull(handler.passwordResetRequest.value)
    }

    @Test
    fun rejectsLegacyLinkContainingSessionCredentials() {
        val handler = DeepLinkHandler()

        handler.handleResetPasswordDeepLink(
            "huntit://reset-password#access_token=secret&refresh_token=also-secret"
        )

        val request = assertNotNull(handler.passwordResetRequest.value)
        assertNull(request.recoveryCode)
        assertEquals(
            "This reset link is invalid or expired. Request a new one.",
            request.errorMessage
        )
    }

    @Test
    fun reportsProviderErrorWithoutRetainingProviderDetails() {
        val handler = DeepLinkHandler()

        handler.handleResetPasswordDeepLink(
            "huntit://reset-password?error=access_denied&error_description=sensitive"
        )

        val request = assertNotNull(handler.passwordResetRequest.value)
        assertNull(request.recoveryCode)
        assertEquals(
            "This reset link is invalid or expired. Request a new one.",
            request.errorMessage
        )
    }

    @Test
    fun rejectsMalformedRecoveryCode() {
        val handler = DeepLinkHandler()

        handler.handleResetPasswordDeepLink(
            "huntit://reset-password?code=contains%2Fencoded%2Fcharacters"
        )

        val request = assertNotNull(handler.passwordResetRequest.value)
        assertNull(request.recoveryCode)
    }

    @Test
    fun clearsHandledRequest() {
        val handler = DeepLinkHandler()
        handler.handleResetPasswordDeepLink(
            "huntit://reset-password?code=abcdefghijklmnopqrstuvwxyz"
        )

        handler.clearDeepLinkData()

        assertNull(handler.passwordResetRequest.value)
    }
}
