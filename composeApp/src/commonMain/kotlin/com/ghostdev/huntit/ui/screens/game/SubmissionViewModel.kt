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


    private var roomId: String? = null
    private var userId: String? = null
    private var roundNumber: Int = 0
    private var challenge: String = ""
    private var theme: String = ""


    data class ReviewData(
        val isSuccess: Boolean,
        val challenge: String,
        val reason: String = "",
        val points: Int = 0
    )


    private var _cachedReviewData: ReviewData? = null
    

    private var _roundEndedError = false
    

    val isRoundEndedError: Boolean
        get() = _roundEndedError


    data class ChallengeData(
        val challenge: String,
        val timeRemaining: String,
        val phaseEndsAtMs: Long
    )


    private var _cachedChallengeData: ChallengeData? = null


    val hasReviewData: Boolean
        get() = _cachedReviewData != null


    fun getReviewData(): ReviewData? = _cachedReviewData


    fun clearReviewData() {
        _cachedReviewData = null
        _roundEndedError = false
        _submissionState.value = SubmissionState.Idle
    }


    fun cacheChallengeData(challenge: String, timeRemaining: String, phaseEndsAtMs: Long) {
        _cachedChallengeData = ChallengeData(challenge, timeRemaining, phaseEndsAtMs)
    }


    fun getCachedChallenge(): String {
        return _cachedChallengeData?.challenge ?: challenge
    }


    fun getCachedTimeRemaining(): String {
        return _cachedChallengeData?.timeRemaining ?: "00:00"
    }
    

    fun getCachedPhaseEndsAtMs(): Long {
        return _cachedChallengeData?.phaseEndsAtMs ?: 0L
    }


    fun clearCachedChallengeData() {
        _cachedChallengeData = null
    }


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


        cacheChallengeData(challenge, timeRemaining, phaseEndsAtMs)

        _submissionState.value = SubmissionState.Idle
    }


    suspend fun canSubmit(): Boolean {
        val userId = userId ?: return false
        val roomId = roomId ?: return false


        val room = gameRepository.getGameRoom(roomId).getOrNull()
        if (room == null || room.currentPhase != GamePhase.IN_PROGRESS) {
            return false
        }


        if (room.currentRound != roundNumber) {
            return false
        }


        val submissions =
            submissionRepository.getUserSubmissions(roomId, userId).getOrNull() ?: return false
        val alreadySubmitted = submissions.any {
            it.roundNumber == roundNumber && it.status == SubmissionStatus.SUCCESS
        }

        return !alreadySubmitted
    }


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

                val imageBytes = imageProcessor.imageBitmapToByteArray(imageBitmap) ?: run {
                    _submissionState.value = SubmissionState.Error("Failed to process image", true)
                    return@launch
                }


                var compressionAttempts = 0
                var compressedImage: ByteArray? = null

                while (compressionAttempts < 3 && (compressedImage == null || compressedImage.size > 800 * 1024)) {

                    val maxSize = when (compressionAttempts) {
                        0 -> 1024 * 1024  // 1MB
                        1 -> 800 * 1024   // 800KB
                        else -> 500 * 1024 // 500KB
                    }

                    compressedImage = imageProcessor.compressImage(imageBytes, maxSize)
                    compressionAttempts++

                    if (compressedImage != null) {
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


                val base64Image = imageProcessor.imageToBase64(compressedImage) ?: run {
                    _submissionState.value = SubmissionState.Error("Failed to encode image", true)
                    return@launch
                }


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


                if (challenge.isBlank()) {
                    val loadedChallenge =
                        gameRepository.getChallenge(roomId, roundNumber).getOrNull()?.challengeText
                    if (loadedChallenge != null) {
                        challenge = loadedChallenge
                    } else {
                        _submissionState.value = SubmissionState.Error(
                            "Cannot verify photo: No challenge available for this round.",
                            false
                        )
                        return@launch
                    }
                }


                _submissionState.value = SubmissionState.Verifying()
                val verificationResult = submissionRepository.verifyPhoto(
                    imageBase64 = base64Image,
                    challengeText = challenge,
                    theme = theme,
                    roomId = roomId,
                    roundNumber = roundNumber
                )


                if (verificationResult.isFailure) {
                    val error = verificationResult.exceptionOrNull()
                    val errorMessage = error?.message ?: ""
                    

                    if (errorMessage.contains("Round mismatch") || 
                        errorMessage.contains("different round than the current game round")) {
                        

                        _cachedReviewData = ReviewData(
                            isSuccess = false,
                            challenge = challenge,
                            reason = "Round has already ended. You were too late!",
                            points = 0
                        )
                        

                        _submissionState.value = SubmissionState.Failed(
                            reason = "Round has already ended. You were too late!",
                            challenge = challenge,
                            points = 0,
                            canRetry = false // Can't retry for expired round
                        )
                        

                        _roundEndedError = true
                        
                        return@launch
                    }
                    

                    val exception = verificationResult.exceptionOrNull() as? Exception
                    _submissionState.value = SubmissionState.Error(
                        exception.toUserFriendlyError("Failed to verify photo. Please try again."),
                        true,
                        exception
                    )
                    return@launch
                }
                

                val verification = verificationResult.getOrNull()!!


                val submissionResult = submissionRepository.submitRound(
                    roomId = roomId,
                    userId = userId,
                    roundNumber = roundNumber,
                    imageUrl = imageUrl,
                    isSuccess = verification.isValid
                )


                if (submissionResult.isFailure) {
                    val error = submissionResult.exceptionOrNull()
                    val errorMessage = error?.message ?: ""
                    

                    if (errorMessage.contains("Round mismatch") || 
                        errorMessage.contains("different round than the current game round")) {
                        

                        _cachedReviewData = ReviewData(
                            isSuccess = false,
                            challenge = challenge,
                            reason = "Round has already ended. You were too late!",
                            points = 0
                        )
                        

                        _submissionState.value = SubmissionState.Failed(
                            reason = "Round has already ended. You were too late!",
                            challenge = challenge,
                            points = 0,
                            canRetry = false // Can't retry for expired round
                        )
                        

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



                if (verification.isValid) {

                    _cachedReviewData = ReviewData(
                        isSuccess = true,
                        challenge = challenge,
                        points = submission.pointsEarned,
                    )

                    _submissionState.value = SubmissionState.Success(
                        points = submission.pointsEarned,
                        challenge = challenge,
                        message = "Photo matches the challenge!"
                    )
                } else {

                    _cachedReviewData = ReviewData(
                        isSuccess = false,
                        challenge = challenge,
                        reason = verification.reason,
                        points = 0
                    )

                    _submissionState.value = SubmissionState.Failed(
                        reason = verification.reason,
                        challenge = challenge,
                        points = 0
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


    fun getReviewState(): SubmissionState {
        val currentState = _submissionState.value


        if (_cachedReviewData != null) {
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


        return currentState
    }


    fun resetState() {
        clearReviewData()

    }


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


    fun startCapturing() {
        _submissionState.value = SubmissionState.Capturing
    }


    fun clear() {
        viewModelScope.launch {

        }
    }
}