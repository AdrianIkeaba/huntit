package com.ghostdev.huntit.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SafetyRepositoryImpl(
    private val client: SupabaseClient
) : SafetyRepository {

    @Serializable
    private data class SafetyActionResponse(
        val success: Boolean,
        val error: String? = null
    )

    override suspend fun reportPlayer(
        reportedUserId: String,
        roomId: String,
        reason: ReportReason,
        details: String?
    ): Result<Unit> = runAction("report_player") {
        put("p_reported_user_id", reportedUserId)
        put("p_room_id", roomId)
        put("p_reason", reason.apiValue)
        details?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("p_details", it)
        }
    }

    override suspend fun blockPlayer(userId: String): Result<Unit> =
        runAction("block_player") {
            put("p_blocked_user_id", userId)
        }

    override suspend fun unblockPlayer(userId: String): Result<Unit> =
        runAction("unblock_player") {
            put("p_blocked_user_id", userId)
        }

    private suspend fun runAction(
        function: String,
        parameters: JsonObjectBuilder.() -> Unit
    ): Result<Unit> {
        return try {
            val response = client.postgrest.rpc(
                function = function,
                parameters = buildJsonObject(parameters)
            ).decodeAs<SafetyActionResponse>()

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Safety action failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
