package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameTheme
import com.ghostdev.huntit.data.model.RoundDuration
import kotlinx.coroutines.flow.Flow

interface GameSetupRepository {

    suspend fun createGameRoom(
        roomName: String,
        roundDuration: RoundDuration,
        gameTheme: GameTheme,
        maxPlayers: Int?,
        totalRounds: Int,
        cooldownSeconds: Int = 30,
        isPublic: Boolean = false
    ): Result<String>


    suspend fun joinGameRoom(
        roomCode: String
    ): Result<String>


    suspend fun fetchGameRoomDetails(roomCode: String): Result<GameRoomDto>

    /**
     * Data class to represent active game information with playing status
     */
    data class ActiveGameInfo(
        val gameRoom: GameRoomDto,
        val isPlaying: Boolean
    )

    /**
     * Check if the current user is participating in any active game
     * @return Result with ActiveGameInfo if user is in an active game, or failure if not
     */
    suspend fun checkActiveGameParticipation(): Result<ActiveGameInfo>

    /**
     * Subscribe to real-time updates for participants in a game room.
     * Emits a list of participants whenever there are changes (inserts, updates, deletes).
     * @param roomId The ID of the game room to subscribe to
     * @return Flow emitting lists of GameParticipantDto
     */
    fun subscribeToParticipants(roomId: String): Flow<List<GameParticipantDto>>

    /**
     * Subscribe to real-time updates for a game room.
     * Emits updates whenever the game room details change.
     * @param roomId The ID of the game room to subscribe to
     * @return Flow emitting GameRoomDto updates
     */
    fun subscribeToGameRoom(roomId: String): Flow<GameRoomDto>

    /**
     * Unsubscribe from all real-time channels for the lobby
     */
    suspend fun unsubscribeFromLobby()

    /**
     * Update game room settings
     * @param roomId The ID of the game room to update
     * @param roomName The new name for the game room
     * @param roundDuration The new round duration string (e.g., "quick", "standard", "marathon")
     * @param roundDurationSeconds The new round duration in seconds
     * @return Result with Unit on success or exception on failure
     */
    suspend fun updateGameRoomSettings(
        roomId: String,
        roomName: String,
        roundDuration: String,
        roundDurationSeconds: Int
    ): Result<Unit>

    /**
     * Delete a game room and all its participants
     * @param roomId The ID of the game room to delete
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteGame(roomId: String): Result<Unit>

    /**
     * Remove a participant from a game room
     * @param roomId The ID of the game room
     * @param userId The ID of the user to remove
     * @return Result with Unit on success or exception on failure
     */
    suspend fun removeParticipant(roomId: String, userId: String): Result<Unit>

    /**
     * Fetch available public game rooms
     * @return Result with a list of GameRoomDto for public games in LOBBY state
     */
    suspend fun fetchPublicGames(): Result<List<GameRoomDto>>
    
    /**
     * Fetch participants count for a list of game rooms
     * @param roomIds List of room IDs to fetch participant counts for
     * @return Map of room ID to participant count
     */
    suspend fun fetchParticipantCountsForRooms(roomIds: List<String>): Result<Map<String, Int>>
}