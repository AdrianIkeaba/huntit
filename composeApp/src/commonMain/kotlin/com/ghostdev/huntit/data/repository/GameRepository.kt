package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.GameChallengeDto
import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.RoundSubmissionDto
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface GameRepository {

    /**
     * Get the current game room by ID
     */
    suspend fun getGameRoom(roomId: String): Result<GameRoomDto>

    /**
     * Get the current game room by room code
     */
    suspend fun getGameRoomByCode(roomCode: String): Result<GameRoomDto>

    /**
     * Get all participants in a game room
     */
    suspend fun getParticipants(roomId: String): Result<List<GameParticipantDto>>

    /**
     * Get all challenges for a game room
     */
    suspend fun getChallenges(roomId: String): Result<List<GameChallengeDto>>

    /**
     * Get a specific challenge for a round
     */
    suspend fun getChallenge(roomId: String, roundNumber: Int): Result<GameChallengeDto>

    /**
     * Advance the game phase (HOST only)
     * This will call the backend RPC function to transition phases
     */
    suspend fun advanceGamePhase(roomId: String): Result<Unit>

    /**
     * Start the game (HOST only)
     * This will generate challenges and start the first phase
     */
    suspend fun startGame(roomId: String): Result<Unit>

    /**
     * Get server time for accurate timer synchronization
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getServerTime(): Result<Instant>

    /**
     * Subscribe to real-time updates for a game room
     */
    fun subscribeToGameRoom(roomId: String): Flow<GameRoomDto>

    /**
     * Subscribe to real-time updates for participants in a game room
     */
    fun subscribeToParticipants(roomId: String): Flow<List<GameParticipantDto>>

    /**
     * Subscribe to real-time updates for round submissions in a game room
     */
    fun subscribeToSubmissions(roomId: String): Flow<List<RoundSubmissionDto>>

    /**
     * Unsubscribe from all game real-time channels
     */
    suspend fun unsubscribeFromGame()

    /**
     * Get user submissions for a game room
     */
    suspend fun getUserSubmissions(roomId: String, userId: String): Result<List<RoundSubmissionDto>>

    /**
     * Set a user's playing status in a game
     */
    suspend fun setUserPlaying(roomId: String, userId: String, isPlaying: Boolean): Result<Unit>
}
