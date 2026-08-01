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
     * @return Result with the private storage object path
     */
    suspend fun uploadImage(
        userId: String,
        roomId: String,
        roundNumber: Int,
        imageBytes: ByteArray
    ): Result<String>

    /**
     * Verify the uploaded photo and atomically commit the server-owned score.
     *
     * The server downloads the exact private object at [imagePath], derives the
     * challenge and theme from the database, and is the only authority allowed
     * to finalize the submission.
     */
    suspend fun verifyAndSubmitPhoto(
        roomId: String,
        roundNumber: Int,
        imagePath: String
    ): Result<VerificationResult>

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
    val pointsEarned: Int = 0
)
