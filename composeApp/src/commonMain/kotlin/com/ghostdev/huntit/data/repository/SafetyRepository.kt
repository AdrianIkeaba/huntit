package com.ghostdev.huntit.data.repository

enum class ReportReason(val apiValue: String, val displayName: String) {
    HARASSMENT("harassment", "Harassment or bullying"),
    HATE_SPEECH("hate_speech", "Hate speech"),
    SEXUAL_CONTENT("sexual_content", "Sexual content"),
    DANGEROUS_BEHAVIOR("dangerous_behavior", "Dangerous behavior"),
    SPAM("spam", "Spam"),
    INAPPROPRIATE_NAME("inappropriate_name", "Inappropriate name"),
    OTHER("other", "Other")
}

interface SafetyRepository {
    suspend fun reportPlayer(
        reportedUserId: String,
        roomId: String,
        reason: ReportReason,
        details: String?
    ): Result<Unit>

    suspend fun blockPlayer(userId: String): Result<Unit>
    suspend fun unblockPlayer(userId: String): Result<Unit>
}
