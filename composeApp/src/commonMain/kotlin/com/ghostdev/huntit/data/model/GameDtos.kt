package com.ghostdev.huntit.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * DTO for game_rooms table
 */
@Serializable
data class GameRoomDto @OptIn(ExperimentalTime::class) constructor(
    val id: String? = null,
    @SerialName("room_code") val roomCode: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("host_id") val hostId: String,
    val theme: GameTheme,
    @SerialName("round_duration") val roundDuration: String,
    @SerialName("round_duration_seconds") val roundDurationSeconds: Int,
    @SerialName("total_rounds") val totalRounds: Int,
    @SerialName("cooldown_seconds") val cooldownSeconds: Int = 0,
    @SerialName("max_players") val maxPlayers: Int? = null,
    val status: GameStatus = GameStatus.LOBBY,
    @SerialName("current_round") val currentRound: Int = 0,
    @SerialName("current_phase") val currentPhase: GamePhase = GamePhase.LOBBY,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("phase_ends_at") val phaseEndsAt: Instant? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("updated_at") val updatedAt: Instant? = null
)

/**
 * DTO for game_participants table
 */
@Serializable
data class GameParticipantDto @OptIn(ExperimentalTime::class) constructor(
    val id: String? = null,
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("is_host") val isHost: Boolean = false,
    @SerialName("current_score") val currentScore: Int = 0,
    @SerialName("joined_at") val joinedAt: Instant? = null,
    @SerialName("join_order") val joinOrder: Int,
    @SerialName("is_playing") val isPlaying: Boolean = true
)

/**
 * DTO for game_challenges table
 */
@Serializable
data class GameChallengeDto @OptIn(ExperimentalTime::class) constructor(
    val id: String? = null,
    @SerialName("room_id") val roomId: String,
    @SerialName("round_number") val roundNumber: Int,
    @SerialName("challenge_text") val challengeText: String,
    @SerialName("created_at") val createdAt: Instant? = null
)

/**
 * Enum for game phase
 */
@Serializable
enum class GamePhase {
    @SerialName("lobby")
    LOBBY,

    @SerialName("cooldown")
    COOLDOWN,

    @SerialName("round_active")
    IN_PROGRESS,

    @SerialName("finished")
    FINISHED
}

/**
 * Enum for submission status
 */
@Serializable
enum class SubmissionStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("success")
    SUCCESS,

    @SerialName("failed")
    FAILED,

    @SerialName("skipped")
    SKIPPED
}

/**
 * DTO for round_submissions table
 */
@Serializable
data class RoundSubmissionDto @OptIn(ExperimentalTime::class) constructor(
    val id: String? = null,
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("round_number") val roundNumber: Int,
    val status: SubmissionStatus = SubmissionStatus.PENDING,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("points_earned") val pointsEarned: Int = 0,
    @SerialName("submitted_at") val submittedAt: Instant? = null,
    @SerialName("verified_at") val verifiedAt: Instant? = null
)

/**
ProfileDto with Instant-based timestamps
 */
@Serializable
data class ProfileDto @OptIn(ExperimentalTime::class) constructor(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: Int = 1,
    @SerialName("total_games_played") val totalGamesPlayed: Int = 0,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("updated_at") val updatedAt: Instant? = null
)