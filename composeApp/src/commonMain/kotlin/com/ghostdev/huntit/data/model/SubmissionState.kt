package com.ghostdev.huntit.data.model

sealed class SubmissionState {
    data object Idle : SubmissionState()
    data object Capturing : SubmissionState()
    data class Processing(val progress: Float = 0f) : SubmissionState()
    data class Uploading(val progress: Float = 0f) : SubmissionState()
    data class Verifying(val progress: Float = 0f) : SubmissionState()
    data class Success(
        val points: Int,
        val challenge: String,
        val message: String = "Success! +$points points"
    ) :
        SubmissionState()

    data class Failed(
        val reason: String,
        val challenge: String,
        val points: Int? = null,
        val canRetry: Boolean = true
    ) : SubmissionState()
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val exception: Exception? = null
    ) : SubmissionState()
}