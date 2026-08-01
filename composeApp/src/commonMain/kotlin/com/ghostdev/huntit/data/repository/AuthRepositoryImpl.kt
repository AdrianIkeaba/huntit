package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.local.PreferencesManager
import com.ghostdev.huntit.data.model.ProfileDto
import com.ghostdev.huntit.data.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthSessionMissingException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

private const val PASSWORD_RESET_EMAIL_MESSAGE =
    "If an account exists for that email, a password reset link has been sent."

@OptIn(ExperimentalTime::class)
class AuthRepositoryImpl(
    private val client: SupabaseClient,
    private val preferencesManager: PreferencesManager
) : AuthRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DeleteAccountResponse(
        val success: Boolean,
        val error: String? = null
    )

    init {
        // Remove the redundant plaintext access token and login flag written by
        // older app versions. The auth SDK persists and refreshes its own session.
        preferencesManager.clearLegacyAuthState()
    }

    override suspend fun hasCompletedOnboarding(): Boolean {
        return preferencesManager.hasCompletedOnboarding()
    }

    override suspend fun signUp(email: String, password: String): Result<User> {
        return try {
            val signUpUser = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val userId = client.auth.currentUserOrNull()?.id ?: signUpUser?.id
                ?: throw Exception("User registration failed - user ID not found")

            // Create profile entry
            val avatarId = (1..7).random()
            val profileDto = ProfileDto(
                id = userId,
                email = email,
                displayName = "",
                avatarId = avatarId,
                totalGamesPlayed = 0
            )

            try {
                client.postgrest["profiles"].insert(profileDto)
            } catch (e: Exception) {
                try {
                    client.auth.signOut()
                } catch (signOutError: Exception) {
                    // Ignore sign out errors
                }
                throw Exception("Failed to create user profile: ${e.message}")
            }

            // Save auth state
            val accessToken = client.auth.currentAccessTokenOrNull()
            if (accessToken != null) {
                preferencesManager.saveUserId(userId)
                preferencesManager.saveEmail(email)
                preferencesManager.saveDisplayName("")  // Empty name for new users
                preferencesManager.saveAvatarId(avatarId)  // Save the avatar ID locally
                preferencesManager.setProfileCompleted(false)
            }

            val user = User(
                id = userId,
                email = email,
                displayName = "",
                avatarId = avatarId,
                totalGamesPlayed = 0
            )

            Result.success(user)
        } catch (e: AuthWeakPasswordException) {
            Result.failure(Exception("Password too weak — ${e.message ?: "does not meet strength requirements."}"))
        } catch (e: AuthRestException) {
            val message = when (e.errorCode) {
                AuthErrorCode.EmailExists,
                AuthErrorCode.UserAlreadyExists -> "ACCOUNT_EXISTS"

                AuthErrorCode.EmailAddressInvalid -> "Invalid email address."
                AuthErrorCode.SignupDisabled,
                AuthErrorCode.EmailProviderDisabled -> "Sign-ups are currently disabled."

                AuthErrorCode.OverRequestRateLimit -> "Too many attempts. Try again later."
                AuthErrorCode.EmailNotConfirmed -> "Email not confirmed. Please verify your email."
                AuthErrorCode.UserBanned -> "Your account is banned. Contact support."
                AuthErrorCode.WeakPassword -> "Password too weak. Please choose a stronger password."
                else -> "Signup failed: ${e.errorCode?.name ?: "unknown error"}"
            }
            Result.failure(Exception(message))
        } catch (e: AuthSessionMissingException) {
            Result.failure(Exception("Session missing. Please log in again."))
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: RestException) {
            Result.failure(Exception("Server error: ${e.message ?: "Unknown error"}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun setDisplayName(displayName: String): Result<String> {
        return updateProfile(displayName, -1) // -1 means don't update avatar
    }

    override suspend fun updateProfile(displayName: String, avatarId: Int): Result<String> {
        return try {
            val userId = getCurrentUserId()
                ?: throw Exception("User not logged in")

            // Fetch current profile first
            val profiles = client.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }.decodeList<ProfileDto>()

            if (profiles.isEmpty()) {
                throw Exception("Profile not found")
            }

            val currentProfile = profiles.first()

            val updatedAvatarId = if (avatarId >= 0) avatarId else currentProfile.avatarId

            client.postgrest["profiles"]
                .update({
                    set("display_name", displayName)
                    if (avatarId >= 0) {
                        set("avatar_id", avatarId)
                    }
                }) {
                    filter {
                        eq("id", userId)
                    }
                }

            preferencesManager.saveDisplayName(displayName)
            preferencesManager.saveAvatarId(updatedAvatarId)
            preferencesManager.setProfileCompleted(true)

            Result.success("Profile updated successfully")
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: RestException) {
            Result.failure(Exception("Failed to update profile: ${e.message ?: "Unknown error"}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val authUser = client.auth.currentUserOrNull()
                ?: throw Exception("Login failed — no active user session found.")

            // Fetch user profile
            val profiles = client.postgrest["profiles"]
                .select(
                    columns = Columns.ALL
                ) {
                    filter {
                        eq("id", authUser.id)
                    }
                }.decodeList<ProfileDto>()

            val profile = if (profiles.isEmpty()) {
                val avatarId = (1..8).random()
                val newProfile = ProfileDto(
                    id = authUser.id,
                    email = authUser.email ?: email,
                    displayName = "",
                    avatarId = avatarId,
                    totalGamesPlayed = 0
                )

                try {
                    client.postgrest["profiles"].insert(newProfile)
                    newProfile
                } catch (e: Exception) {
                    throw Exception("Failed to create missing profile: ${e.message}")
                }
            } else {
                profiles.first()
            }

            // Save auth state
            val accessToken = client.auth.currentAccessTokenOrNull()
            if (accessToken != null) {
                preferencesManager.saveUserId(authUser.id)
                preferencesManager.saveEmail(profile.email)
                preferencesManager.saveDisplayName(profile.displayName)
                preferencesManager.saveAvatarId(profile.avatarId)
                preferencesManager.setProfileCompleted(profile.displayName.isNotEmpty())
            }

            val user = User(
                id = profile.id,
                email = profile.email,
                displayName = profile.displayName,
                avatarId = profile.avatarId,
                totalGamesPlayed = profile.totalGamesPlayed
            )

            Result.success(user)
        } catch (e: AuthWeakPasswordException) {
            Result.failure(Exception("Password does not meet security requirements."))
        } catch (e: AuthRestException) {
            val message = when (e.errorCode) {
                AuthErrorCode.InvalidCredentials -> "Incorrect email or password."
                AuthErrorCode.EmailNotConfirmed -> "Email not confirmed. Please check your inbox."
                AuthErrorCode.UserNotFound -> "User not found. Please sign up first."
                AuthErrorCode.UserBanned -> "Your account has been banned. Contact support."
                AuthErrorCode.OverRequestRateLimit,
                AuthErrorCode.SessionExpired,
                AuthErrorCode.SessionNotFound -> "Your session has expired. Please log in again."

                else -> "Login failed: ${e.errorCode?.name ?: "unknown error"}"
            }
            Result.failure(Exception(message))
        } catch (e: AuthSessionMissingException) {
            Result.failure(Exception("Session missing or expired. Please log in again."))
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: RestException) {
            Result.failure(Exception("Server error: ${e.message ?: "Unknown error"}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<String> {
        return try {
            val redirectUrl = "huntit://reset-password"

            // Auth is configured for PKCE, so the redirect carries a one-time code
            // instead of reusable access and refresh tokens.
            client.auth.resetPasswordForEmail(email, redirectUrl)

            Result.success(PASSWORD_RESET_EMAIL_MESSAGE)
        } catch (e: AuthRestException) {
            val message = when (e.errorCode) {
                AuthErrorCode.EmailAddressInvalid -> "Invalid email address."
                AuthErrorCode.OverRequestRateLimit -> "Too many attempts. Try again later."
                // Do not disclose whether the email belongs to an account.
                AuthErrorCode.UserNotFound -> PASSWORD_RESET_EMAIL_MESSAGE
                else -> "Unable to send a reset email right now. Please try again."
            }
            if (e.errorCode == AuthErrorCode.UserNotFound) {
                Result.success(message)
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: RestException) {
            Result.failure(Exception("Unable to send a reset email right now. Please try again."))
        } catch (e: Exception) {
            Result.failure(Exception("Unable to send a reset email right now. Please try again."))
        }
    }

    override suspend fun resetPasswordWithCode(
        recoveryCode: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            client.auth.exchangeCodeForSession(recoveryCode)
            client.auth.updateUser {
                password = newPassword
            }
            client.auth.signOut()
            preferencesManager.clearLegacyAuthState()

            Result.success(Unit)
        } catch (e: AuthWeakPasswordException) {
            Result.failure(Exception("Password does not meet security requirements."))
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: Exception) {
            // Invalid, expired, already-used, and device-mismatched PKCE links all
            // receive the same response so auth details are not exposed.
            Result.failure(Exception("This reset link is invalid or expired. Request a new one."))
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return try {
            client.auth.awaitInitialization()
            client.auth.currentSessionOrNull() != null
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun confirmEligibility(): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
                ?: return Result.failure(Exception("User not logged in"))
            val confirmedAt = Clock.System.now().toString()

            client.postgrest["profiles"].update({
                set("age_confirmed_at", confirmedAt)
                set("terms_accepted_at", confirmedAt)
            }) {
                filter {
                    eq("id", userId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                Exception("Could not save your eligibility confirmation. Please try again.")
            )
        }
    }

    override suspend fun hasCompletedProfile(): Boolean {
        return preferencesManager.hasCompletedProfile()
    }

    override suspend fun logout() {
        try {
            // Properly sign out from Supabase
            client.auth.signOut()
        } catch (e: Exception) {
        } finally {
            // Clear local storage on logout
            preferencesManager.clearAll()
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val response = client.functions.invoke(function = "delete-account")
            val responseBody = response.body<String>()
            val result = json.decodeFromString<DeleteAccountResponse>(responseBody)

            if (!result.success) {
                return Result.failure(
                    Exception(result.error ?: "Account deletion failed")
                )
            }

            try {
                client.auth.signOut()
            } catch (_: Exception) {
                // The Auth user has already been removed server-side.
            } finally {
                preferencesManager.clearAll()
            }

            Result.success(Unit)
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (_: Exception) {
            Result.failure(Exception("We could not delete your account. Please try again."))
        }
    }

    override fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id ?: preferencesManager.getUserId()
    }

    override fun getLocalUserProfile(): User? {
        val userId = getCurrentUserId() ?: return null

        val avatarId = preferencesManager.getAvatarId()

        return User(
            id = userId,
            email = preferencesManager.getEmail(),
            displayName = preferencesManager.getDisplayName(),
            avatarId = avatarId,
            totalGamesPlayed = 0
        )
    }

    override suspend fun getUserProfile(): Result<User> {
        return try {
            val userId = getCurrentUserId()
                ?: throw Exception("User not logged in")

            val localUser = User(
                id = userId,
                email = preferencesManager.getEmail(),
                displayName = preferencesManager.getDisplayName(),
                avatarId = preferencesManager.getAvatarId(),
                totalGamesPlayed = 0
            )

            try {
                // Try to get profile from server
                val profiles = client.postgrest["profiles"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("id", userId)
                        }
                    }.decodeList<ProfileDto>()

                if (profiles.isNotEmpty()) {
                    val profile = profiles.first()

                    // Save the latest data locally for future offline use
                    preferencesManager.saveEmail(profile.email)
                    preferencesManager.saveDisplayName(profile.displayName)
                    preferencesManager.saveAvatarId(profile.avatarId)

                    return Result.success(
                        User(
                            id = profile.id,
                            email = profile.email,
                            displayName = profile.displayName,
                            avatarId = profile.avatarId,
                            totalGamesPlayed = profile.totalGamesPlayed
                        )
                    )
                }
            } catch (e: Exception) {
                // If there's an error getting data from server, use local data
                // No need to do anything as we'll return localUser
            }

            // Return local data if server request failed or returned empty
            Result.success(localUser)
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
        }
    }
}
