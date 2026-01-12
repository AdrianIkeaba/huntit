package com.ghostdev.huntit.ui.screens.game

import androidx.compose.ui.graphics.ImageBitmap
import com.ghostdev.huntit.data.model.GamePhase
import com.ghostdev.huntit.data.model.SubmissionState
import com.ghostdev.huntit.data.model.SubmissionStatus
import com.ghostdev.huntit.data.repository.SubmissionRepository
import com.ghostdev.huntit.data.repository.GameRepository
import com.ghostdev.huntit.data.repository.VerificationResult
import com.ghostdev.huntit.utils.ImageProcessor
import com.ghostdev.huntit.utils.toUserFriendlyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SubmissionViewModel : KoinComponent {
    private val submissionRepository: SubmissionRepository by inject()
    private val gameRepository: GameRepository by inject()
    private val imageProcessor: ImageProcessor = ImageProcessor()

    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _submissionState = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val submissionState: StateFlow<SubmissionState> = _submissionState.asStateFlow()

    // Game state information needed for submission
    private var roomId: String? = null
    private var userId: String? = null
    private var roundNumber: Int = 0
    private var challenge: String = ""
    private var theme: String = ""

    // Class to hold verification result data for review screen
    data class ReviewData(
        val isSuccess: Boolean,
        val challenge: String,
        val reason: String = "",
        val points: Int = 0
    )

    // Cached verification result
    private var _cachedReviewData: ReviewData? = null
    
    // Special flag for round ended error
    private var _roundEndedError = false
    
    // Public accessor for round ended error
    val isRoundEndedError: Boolean
        get() = _roundEndedError

    // Class to hold challenge data for display
    data class ChallengeData(
        val challenge: String,
        val timeRemaining: String,
        val phaseEndsAtMs: Long
    )

    // Cache for challenge and time data
    private var _cachedChallengeData: ChallengeData? = null

    // Public accessor for the cached review data
    val hasReviewData: Boolean
        get() = _cachedReviewData != null

    // Get cached review data
    fun getReviewData(): ReviewData? = _cachedReviewData

    // Clear cached review data
    fun clearReviewData() {
        _cachedReviewData = null
        _roundEndedError = false
        _submissionState.value = SubmissionState.Idle
    }

    // Cache challenge data for display in photo screen
    fun cacheChallengeData(challenge: String, timeRemaining: String, phaseEndsAtMs: Long) {
        _cachedChallengeData = ChallengeData(challenge, timeRemaining, phaseEndsAtMs)
    }

    // Get cached challenge text
    fun getCachedChallenge(): String {
        return _cachedChallengeData?.challenge ?: challenge
    }

    // Get cached time remaining
    fun getCachedTimeRemaining(): String {
        return _cachedChallengeData?.timeRemaining ?: "00:00"
    }
    
    // Get cached phase end time (in milliseconds)
    fun getCachedPhaseEndsAtMs(): Long {
        return _cachedChallengeData?.phaseEndsAtMs ?: 0L
    }

    // Clear cached challenge data
    fun clearCachedChallengeData() {
        _cachedChallengeData = null
    }

    /**
     * Initialize the ViewModel with game information
     */
    fun initialize(
        roomId: String,
        userId: String,
        roundNumber: Int,
        challenge: String,
        theme: String,
        timeRemaining: String = "00:00",
        phaseEndsAtMs: Long = 0
    ) {
        this.roomId = roomId
        this.userId = userId
        this.roundNumber = roundNumber
        this.challenge = challenge
        this.theme = theme

        // Always update the cache with the most recent data
        // This ensures we always have the latest time remaining
        println("SubmissionViewModel initializing with challenge: $challenge, time: $timeRemaining")
        cacheChallengeData(challenge, timeRemaining, phaseEndsAtMs)

        _submissionState.value = SubmissionState.Idle
    }

    /**
     * Validate whether submission is allowed based on current game state
     */
    suspend fun canSubmit(): Boolean {
        val userId = userId ?: return false
        val roomId = roomId ?: return false

        // 1. Check if game phase is round_active
        val room = gameRepository.getGameRoom(roomId).getOrNull()
        if (room == null || room.currentPhase != GamePhase.IN_PROGRESS) {
            return false
        }

        // 2. Check if round number matches current round
        if (room.currentRound != roundNumber) {
            return false
        }

        // 3. Check if user has already submitted for this round
        val submissions =
            submissionRepository.getUserSubmissions(roomId, userId).getOrNull() ?: return false
        val alreadySubmitted = submissions.any {
            it.roundNumber == roundNumber && it.status == SubmissionStatus.SUCCESS
        }

        return !alreadySubmitted
    }

    /**
     * Process and submit a captured photo
     */
    fun processSubmission(imageBitmap: ImageBitmap) {
        val roomId = roomId ?: run {
            _submissionState.value = SubmissionState.Error("Game information not available", false)
            return
        }
        val userId = userId ?: run {
            _submissionState.value = SubmissionState.Error("User information not available", false)
            return
        }

        _submissionState.value = SubmissionState.Processing()

        viewModelScope.launch {
            try {
                // 1. Convert ImageBitmap to ByteArray
                val imageBytes = imageProcessor.imageBitmapToByteArray(imageBitmap) ?: run {
                    _submissionState.value = SubmissionState.Error("Failed to process image", true)
                    return@launch
                }

                // 2. Compress the image - try with higher compression if too large
                var compressionAttempts = 0
                var compressedImage: ByteArray? = null

                while (compressionAttempts < 3 && (compressedImage == null || compressedImage.size > 800 * 1024)) {
                    // Increase compression with each attempt
                    val maxSize = when (compressionAttempts) {
                        0 -> 1024 * 1024  // 1MB
                        1 -> 800 * 1024   // 800KB
                        else -> 500 * 1024 // 500KB
                    }

                    compressedImage = imageProcessor.compressImage(imageBytes, maxSize)
                    compressionAttempts++

                    if (compressedImage != null) {
                        println("Compressed image size: ${compressedImage.size / 1024}KB (attempt $compressionAttempts)")
                    }
                }

                if (compressedImage == null) {
                    _submissionState.value = SubmissionState.Error("Failed to compress image", true)
                    return@launch
                }

                if (compressedImage.size > 1000 * 1024) {
                    _submissionState.value = SubmissionState.Error(
                        "Image too large (${compressedImage.size / 1024}KB). Maximum size is 1MB.",
                        true
                    )
                    return@launch
                }

                // 3. Generate Base64 for AI verification
                val base64Image = imageProcessor.imageToBase64(compressedImage) ?: run {
                    _submissionState.value = SubmissionState.Error("Failed to encode image", true)
                    return@launch
                }

                // 4. Upload image to Supabase Storage
                _submissionState.value = SubmissionState.Uploading()
                val uploadResult = submissionRepository.uploadImage(
                    userId = userId,
                    roomId = roomId,
                    roundNumber = roundNumber,
                    imageBytes = compressedImage
                )

                val imageUrl = uploadResult.getOrNull() ?: run {
                    _submissionState.value = SubmissionState.Error(
                        "Failed to upload image. Please check your connection.",
                        true,
                        uploadResult.exceptionOrNull() as? Exception
                    )
                    return@launch
                }

                // 5. Check if challenge is empty, and if so, try to load it
                if (challenge.isBlank()) {
                    // Try to get the challenge text for this round
                    println("Challenge text is empty! Attempting to load it")
                    val loadedChallenge =
                        gameRepository.getChallenge(roomId, roundNumber).getOrNull()?.challengeText
                    if (loadedChallenge != null) {
                        challenge = loadedChallenge
                        println("Successfully loaded challenge: $challenge")
                    } else {
                        _submissionState.value = SubmissionState.Error(
                            "Cannot verify photo: No challenge available for this round.",
                            false
                        )
                        return@launch
                    }
                }

                // Proceed with verification
                _submissionState.value = SubmissionState.Verifying()
                val verificationResult = submissionRepository.verifyPhoto(
                    imageBase64 = base64Image,
                    challengeText = challenge,
                    theme = theme,
                    roomId = roomId,
                    roundNumber = roundNumber
                )

                // Check for the specific round mismatch error
                if (verificationResult.isFailure) {
                    val error = verificationResult.exceptionOrNull()
                    val errorMessage = error?.message ?: ""
                    
                    // Check if this is the round mismatch error
                    if (errorMessage.contains("Round mismatch") || 
                        errorMessage.contains("different round than the current game round")) {
                        println("Detected round mismatch error: $errorMessage")
                        
                        // Create special cached review data for round ended case
                        _cachedReviewData = ReviewData(
                            isSuccess = false,
                            challenge = challenge,
                            reason = "Round has already ended. You were too late!",
                            points = 0
                        )
                        
                        // Update state with a user-friendly message
                        _submissionState.value = SubmissionState.Failed(
                            reason = "Round has already ended. You were too late!",
                            challenge = challenge,
                            points = 0,
                            canRetry = false // Can't retry for expired round
                        )
                        
                        // Special state flag for round ended error
                        _roundEndedError = true
                        
                        return@launch
                    }
                    
                    // Handle other verification errors with user-friendly message
                    val exception = verificationResult.exceptionOrNull() as? Exception
                    _submissionState.value = SubmissionState.Error(
                        exception.toUserFriendlyError("Failed to verify photo. Please try again."),
                        true,
                        exception
                    )
                    return@launch
                }
                
                // If we got here, verification was successful
                val verification = verificationResult.getOrNull()!!

                // 6. Submit results to backend
                val submissionResult = submissionRepository.submitRound(
                    roomId = roomId,
                    userId = userId,
                    roundNumber = roundNumber,
                    imageUrl = imageUrl,
                    isSuccess = verification.isValid
                )

                // Check for round mismatch error in submission as well
                if (submissionResult.isFailure) {
                    val error = submissionResult.exceptionOrNull()
                    val errorMessage = error?.message ?: ""
                    
                    // Check if this is the round mismatch error
                    if (errorMessage.contains("Round mismatch") || 
                        errorMessage.contains("different round than the current game round")) {
                        println("Detected round mismatch error during submission: $errorMessage")
                        
                        // Create special cached review data for round ended case
                        _cachedReviewData = ReviewData(
                            isSuccess = false,
                            challenge = challenge,
                            reason = "Round has already ended. You were too late!",
                            points = 0
                        )
                        
                        // Update state with a user-friendly message
                        _submissionState.value = SubmissionState.Failed(
                            reason = "Round has already ended. You were too late!",
                            challenge = challenge,
                            points = 0,
                            canRetry = false // Can't retry for expired round
                        )
                        
                        // Special state flag for round ended error
                        _roundEndedError = true
                        
                        return@launch
                    }
                    
                    val exception = submissionResult.exceptionOrNull() as? Exception
                    _submissionState.value = SubmissionState.Error(
                        exception.toUserFriendlyError("Failed to submit result. Please try again."),
                        true,
                        exception
                    )
                    return@launch
                }
                
                val submission = submissionResult.getOrNull()!!

                // 7. Update state based on verification result
                // Print the verification result first for debugging
                println("Verification result: ${verification.isValid}, reason: ${verification.reason}")

                if (verification.isValid) {
                    // Cache the success result
                    _cachedReviewData = ReviewData(
                        isSuccess = true,
                        challenge = challenge,
                        points = submission.pointsEarned ?: 2,
                    )

                    println("Caching successful verification result for review")
                    _submissionState.value = SubmissionState.Success(
                        points = submission.pointsEarned ?: 2,
                        challenge = challenge,
                        message = "Photo matches the challenge!"
                    )
                } else {
                    // Cache the failure result
                    _cachedReviewData = ReviewData(
                        isSuccess = false,
                        challenge = challenge,
                        reason = verification.reason,
                        points = 0
                    )

                    println("Caching failed verification result for review: ${verification.reason}")
                    _submissionState.value = SubmissionState.Failed(
                        reason = verification.reason,
                        challenge = challenge,
                        points = 0
                    )
                }
                println("Current submission state after update: ${_submissionState.value}")

            } catch (e: Exception) {
                _submissionState.value = SubmissionState.Error(
                    e.toUserFriendlyError("Something went wrong. Please try again."),
                    true,
                    e
                )
            }
        }
    }

    /**
     * Get the submission state for review screen, preserving the last non-idle state
     * to avoid losing information during navigation
     */
    fun getReviewState(): SubmissionState {
        val currentState = _submissionState.value

        println("SubmissionViewModel getReviewState() - Current state: $currentState, Cached data: $_cachedReviewData")

        // If we have cached review data, convert it to state
        if (_cachedReviewData != null) {
            println("Using cached review data")
            return if (_cachedReviewData!!.isSuccess) {
                SubmissionState.Success(
                    points = _cachedReviewData!!.points,
                    challenge = _cachedReviewData!!.challenge,
                    message = "Photo matches the challenge!"
                )
            } else {
                SubmissionState.Failed(
                    reason = _cachedReviewData!!.reason,
                    challenge = _cachedReviewData!!.challenge,
                    points = _cachedReviewData!!.points
                )
            }
        }

        // Otherwise return current state
        return currentState
    }

    /**
     * Reset the submission state to Idle
     */
    fun resetState() {
        clearReviewData()
        // Don't clear cached challenge data - we want to keep it between submissions
    }

    /**
     * Skip the current round
     */
    fun skipRound() {
        val roomId = roomId ?: run {
            _submissionState.value = SubmissionState.Error("Game information not available", false)
            return
        }
        val userId = userId ?: run {
            _submissionState.value = SubmissionState.Error("User information not available", false)
            return
        }

        viewModelScope.launch {
            try {
                val result = submissionRepository.skipRound(roomId, userId, roundNumber)
                if (result.isSuccess) {
                    _submissionState.value = SubmissionState.Idle
                } else {
                    val exception = result.exceptionOrNull() as? Exception
                    _submissionState.value = SubmissionState.Error(
                        exception.toUserFriendlyError("Failed to skip round. Please try again."),
                        true,
                        exception
                    )
                }
            } catch (e: Exception) {
                _submissionState.value = SubmissionState.Error(
                    e.toUserFriendlyError("Something went wrong. Please try again."),
                    true,
                    e
                )
            }
        }
    }

    /**
     * Set the submission state to capturing
     */
    fun startCapturing() {
        _submissionState.value = SubmissionState.Capturing
    }

    /**
     * Clean up resources when ViewModel is no longer needed
     */
    fun clear() {
        viewModelScope.launch {
            // Cancel any ongoing tasks and clean up resources
        }
    }
}