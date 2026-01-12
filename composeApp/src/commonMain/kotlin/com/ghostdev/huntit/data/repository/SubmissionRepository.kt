package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.RoundSubmissionDto

/**
 * Repository for handling round submissions including photo upload and verification
 */
interface SubmissionRepository {

    /**
     * Upload an image to storage
     * @param userId The user's ID
     * @param roomId The game room ID
     * @param roundNumber The round number
     * @param imageBytes The image data as bytes
     * @return Result with the public URL of the uploaded image
     */
    suspend fun uploadImage(
        userId: String,
        roomId: String,
        roundNumber: Int,
        imageBytes: ByteArray
    ): Result<String>

    /**
     * Verify a photo against a challenge using AI
     * @param imageBase64 The image encoded as base64 string
     * @param challengeText The challenge text to verify against
     * @param theme The game theme for context
     * @param roomId The game room ID (optional, for server-side time validation)
     * @param roundNumber The round number (optional, for server-side time validation)
     * @return Result with verification result (isValid, reason)
     */
    suspend fun verifyPhoto(
        imageBase64: String,
        challengeText: String,
        theme: String,
        roomId: String? = null,
        roundNumber: Int? = null
    ): Result<VerificationResult>

    /**
     * Submit a round result
     * @param roomId The game room ID
     * @param userId The user's ID
     * @param roundNumber The round number
     * @param imageUrl The URL of the uploaded image (null if skipped)
     * @param isSuccess Whether the submission was successful
     * @return Result with the created submission
     */
    suspend fun submitRound(
        roomId: String,
        userId: String,
        roundNumber: Int,
        imageUrl: String?,
        isSuccess: Boolean
    ): Result<RoundSubmissionDto>

    /**
     * Skip a round
     * @param roomId The game room ID
     * @param userId The user's ID
     * @param roundNumber The round number
     * @return Result with the created submission
     */
    suspend fun skipRound(
        roomId: String,
        userId: String,
        roundNumber: Int
    ): Result<RoundSubmissionDto>

    /**
     * Get all submissions for a user in a game room
     * @param roomId The game room ID
     * @param userId The user's ID
     * @return Result with list of submissions
     */
    suspend fun getUserSubmissions(
        roomId: String,
        userId: String
    ): Result<List<RoundSubmissionDto>>
}

/**
 * Result of photo verification
 */
data class VerificationResult(
    val isValid: Boolean,
    val reason: String,
    val confidence: Float = 0f
)
