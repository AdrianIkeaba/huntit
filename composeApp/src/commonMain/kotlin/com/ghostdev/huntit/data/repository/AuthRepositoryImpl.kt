package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.local.PreferencesManager
import com.ghostdev.huntit.data.model.ProfileDto
import com.ghostdev.huntit.data.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthSessionMissingException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AuthRepositoryImpl(
    private val client: SupabaseClient,
    private val preferencesManager: PreferencesManager
) : AuthRepository {

    override suspend fun hasCompletedOnboarding(): Boolean {
        return preferencesManager.hasCompletedOnboarding()
    }
    override suspend fun signUp(email: String, password: String): Result<User> {
        var signUpSuccessful = false
        return try {
            // Try to sign up
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            signUpSuccessful = true

            val userId = client.auth.currentUserOrNull()?.id
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
                preferencesManager.saveAccessToken(accessToken)
                preferencesManager.saveUserId(userId)
                preferencesManager.saveEmail(email)
                preferencesManager.saveDisplayName("")  // Empty name for new users
                preferencesManager.saveAvatarId(avatarId)  // Save the avatar ID locally
                preferencesManager.saveIsLoggedIn(true)
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
            // If email already exists, this is not an error - the login should have handled it
            // So we should propagate this as a failure to let the ViewModel know to try login
            val message = when (e.errorCode) {
                // These indicate the account exists - let login handle it
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

            // Create updated profile
            val updatedProfile = if (avatarId >= 0) { // Changed from avatarId > 0 to include avatar ID 1
                // Update both name and avatar
                currentProfile.copy(displayName = displayName, avatarId = avatarId)
            } else {
                // Update only name
                currentProfile.copy(displayName = displayName)
            }

            // For avatar ID 1, use a different approach as there appears to be an issue with the database
            // possibly treating it as a default value
            val updateResult = if (avatarId == 1) {
                // Use a raw SQL approach via RPC to ensure the avatar is explicitly set
                try {
                    // First update non-avatar fields
                    val standardResult = client.postgrest["profiles"].update(updatedProfile) {
                        filter {
                            eq("id", userId)
                        }
                    }

                    // Then force the avatar update with explicit non-null flag
                    val forcedResult = client.postgrest["profiles"]
                        .update(mapOf("avatar_id" to 1)) {
                            filter {
                                eq("id", userId)
                            }
                        }
                    forcedResult
                } catch (e: Exception) {
                    // Fall back to regular update
                    client.postgrest["profiles"].update(updatedProfile) {
                        filter {
                            eq("id", userId)
                        }
                    }
                }
            } else {
                // Normal update for other avatar IDs
                client.postgrest["profiles"].update(updatedProfile) {
                    filter {
                        eq("id", userId)
                    }
                }
            }

            // Save changes locally
            preferencesManager.saveDisplayName(updatedProfile.displayName)
            if (avatarId >= 0) {
                preferencesManager.saveAvatarId(updatedProfile.avatarId)
            }
            preferencesManager.setProfileCompleted(true)

            // For avatar ID 1, verify the update worked by immediately checking the database
            if (avatarId == 1) {
                try {
                    val verifyProfiles = client.postgrest["profiles"]
                        .select(columns = Columns.ALL) {
                            filter {
                                eq("id", userId)
                            }
                        }.decodeList<ProfileDto>()

                    if (verifyProfiles.isNotEmpty()) {
                        val dbProfile = verifyProfiles.first()

                        // If verification shows avatar ID is not 1, force it again
                        if (dbProfile.avatarId != 1) {
                            client.postgrest["profiles"]
                                .update(mapOf("avatar_id" to 1)) {
                                    filter {
                                        eq("id", userId)
                                    }
                                }
                            // Make sure preferences match what we want
                            preferencesManager.saveAvatarId(1)
                        }
                    }
                } catch (e: Exception) {
                    // Silent fail - user can try again if needed
                }
            }

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
                // Profile doesn't exist - create it now
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
                preferencesManager.saveAccessToken(accessToken)
                preferencesManager.saveUserId(authUser.id)
                preferencesManager.saveEmail(profile.email)
                preferencesManager.saveDisplayName(profile.displayName)
                preferencesManager.saveAvatarId(profile.avatarId)
                preferencesManager.saveIsLoggedIn(true)
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

    override suspend fun checkUserExists(email: String): Result<Boolean> {
        return try {
            // Query all profiles and check in memory instead of using the filter
            // This avoids issues with how Supabase processes filter conditions
            val normalizedEmail = email.trim().lowercase()

            // Get all profiles and filter in memory
            val allProfiles = client.postgrest["profiles"]
                .select(columns = Columns.ALL)
                .decodeList<ProfileDto>()

            // Find matching profiles in memory using normalized comparison
            val matchingProfiles = allProfiles.filter {
                it.email.trim().lowercase() == normalizedEmail
            }

            // Only return true if we found at least one profile with this email
            Result.success(matchingProfiles.isNotEmpty())
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
            // Define a redirect URL for the app's deep link
            val redirectUrl = "huntit://reset-password"

            // Send reset email with custom redirect to our app
            client.auth.resetPasswordForEmail(email, redirectUrl)

            Result.success("Password reset email sent. Please check your inbox.")
        } catch (e: AuthRestException) {
            val message = when (e.errorCode) {
                AuthErrorCode.EmailAddressInvalid -> "Invalid email address."
                AuthErrorCode.OverRequestRateLimit -> "Too many attempts. Try again later."
                AuthErrorCode.UserNotFound -> "No account found with this email."
                else -> "Failed to send reset email: ${e.errorCode?.name ?: "unknown error"}"
            }
            Result.failure(Exception(message))
        } catch (e: HttpRequestException) {
            Result.failure(Exception("Network error. Please check your connection."))
        } catch (e: RestException) {
            Result.failure(Exception("Server error: ${e.message ?: "Unknown error"}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun resetPasswordWithTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        newPassword: String
    ): Result<Unit> {
        return try {
            // Create UserSession
            val userSession = UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                tokenType = "bearer"
            )

            client.auth.importSession(userSession, autoRefresh = false)

            // Update password
            client.auth.updateUser {
                password = newPassword
            }

            // Sign out
            client.auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to reset password"))
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        // Always return true if the user is marked as logged in locally
        // This ensures the user stays logged in even if session token expires temporarily
        val isLoggedIn = preferencesManager.isLoggedIn()
        println("DEBUG AUTH: isLoggedIn preference = $isLoggedIn")

        if (isLoggedIn) {
            // If we have a session already, just return true
            if (client.auth.currentSessionOrNull() != null) {
                return true
            }

            // Try to refresh the session if we don't have one but the user is marked as logged in
            try {
                // If refresh token is available, try refreshing the session
                val refreshToken = preferencesManager.getAccessToken()
                if (!refreshToken.isNullOrEmpty()) {
                    client.auth.refreshCurrentSession()
                    return true
                }
            } catch (e: Exception) {
                // Even if refresh fails, still consider user logged in
                // They'll be prompted to log in again if their session is truly invalid
                return true
            }

            // Keep the user logged in even if session refresh fails
            return true
        }

        println("DEBUG AUTH: User is definitely not logged in")
        return false
    }

    override suspend fun hasCompletedProfile(): Boolean {
        return preferencesManager.hasCompletedProfile()
    }

    override suspend fun getAccessToken(): String? {
        return client.auth.currentAccessTokenOrNull() ?: preferencesManager.getAccessToken()
    }

    override suspend fun logout() {
        try {
            // Properly sign out from Supabase
            client.auth.signOut()
        } catch (e: Exception) {
            // Ignore logout errors, will still clear local state
        } finally {
            // Always clear local storage on logout
            preferencesManager.clearAll()
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
            totalGamesPlayed = 0  // We don't store this locally
        )
    }

    override suspend fun getUserProfile(): Result<User> {
        return try {
            val userId = getCurrentUserId()
                ?: throw Exception("User not logged in")

            // Always prepare local user data as a backup
            val localUser = User(
                id = userId,
                email = preferencesManager.getEmail(),
                displayName = preferencesManager.getDisplayName(),
                avatarId = preferencesManager.getAvatarId(),
                totalGamesPlayed = 0 // We don't store this locally
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

                    // Special handling for avatarId = 1 because of database issues
                    val localAvatarId = preferencesManager.getAvatarId()
                    val preferredAvatarId = if (localAvatarId == 1 && profile.avatarId != 1) {
                        1 // Keep using 1 if that's what the user wants
                    } else {
                        profile.avatarId
                    }

                    // Save the latest data locally for future offline use
                    preferencesManager.saveEmail(profile.email)
                    preferencesManager.saveDisplayName(profile.displayName)
                    preferencesManager.saveAvatarId(preferredAvatarId)

                    return Result.success(
                        User(
                            id = profile.id,
                            email = profile.email,
                            displayName = profile.displayName,
                            avatarId = preferredAvatarId, // Use our preferred avatar ID
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