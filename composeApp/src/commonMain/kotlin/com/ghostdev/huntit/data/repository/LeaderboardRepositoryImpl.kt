package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class LeaderboardRepositoryImpl(
    private val client: SupabaseClient
) : LeaderboardRepository {

    override suspend fun getTopPlayers(
        roomId: String,
        limit: Int?
    ): Result<List<GameParticipantDto>> {
        return try {
            val query = client.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                    }
                    if (limit != null) {
                        limit(limit.toLong())
                    }
                }

            val participants = query.decodeList<GameParticipantDto>()

            // Sort participants by score in descending order
            val sortedParticipants = participants.sortedByDescending { it.currentScore }

            // Apply limit if needed (already applied in the query, but just to be safe)
            val limitedParticipants =
                limit?.let { sortedParticipants.take(it) } ?: sortedParticipants

            Result.success(limitedParticipants)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfiles(userIds: List<String>): Result<List<ProfileDto>> {
        if (userIds.isEmpty()) {
            return Result.success(emptyList())
        }

        return try {
            val profiles = client.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter {
                        isIn("id", userIds)
                    }
                }
                .decodeList<ProfileDto>()

            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}