package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.User

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun setDisplayName(displayName: String): Result<String>
    suspend fun updateProfile(displayName: String, avatarId: Int): Result<String>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun sendPasswordResetEmail(email: String): Result<String>
    suspend fun resetPasswordWithTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        newPassword: String
    ): Result<Unit>
    suspend fun checkUserExists(email: String): Result<Boolean>

    suspend fun isLoggedIn(): Boolean
    suspend fun hasCompletedProfile(): Boolean
    suspend fun hasCompletedOnboarding(): Boolean
    suspend fun getAccessToken(): String?
    suspend fun logout()
    fun getCurrentUserId(): String?
    suspend fun getUserProfile(): Result<User>

    fun getLocalUserProfile(): User?
}