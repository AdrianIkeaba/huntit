package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.ProfileDto

interface LeaderboardRepository {

    /**
     * Get the top players for a game room
     * @param roomId The ID of the game room
     * @param limit The maximum number of players to return (optional)
     * @return A Result containing a list of GameParticipantDto objects sorted by score
     */
    suspend fun getTopPlayers(roomId: String, limit: Int? = null): Result<List<GameParticipantDto>>

    /**
     * Get user profiles for a list of user IDs
     * @param userIds The list of user IDs to fetch profiles for
     * @return A Result containing a list of ProfileDto objects
     */
    suspend fun getUserProfiles(userIds: List<String>): Result<List<ProfileDto>>
}