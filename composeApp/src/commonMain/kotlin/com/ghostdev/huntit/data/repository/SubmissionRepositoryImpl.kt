package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.RoundSubmissionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SubmissionRepositoryImpl(
    private val client: SupabaseClient
) : SubmissionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalTime::class)
    override suspend fun uploadImage(
        userId: String,
        roomId: String,
        roundNumber: Int,
        imageBytes: ByteArray
    ): Result<String> {
        return try {
            val bucket = client.storage.from("submissions")
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val fileName = "${roomId}/${userId}/round_${roundNumber}_${timestamp}.jpg"

            bucket.upload(fileName, imageBytes) {
                upsert = true
            }

            // Store the private object path. Access is authorized by Storage RLS;
            // public URLs are intentionally not generated for player photos.
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class VerifySubmissionResponse(
        val success: Boolean,
        val valid: Boolean = false,
        val reason: String = "",
        val points: Int = 0,
        val error: String? = null
    )

    override suspend fun verifyAndSubmitPhoto(
        roomId: String,
        roundNumber: Int,
        imagePath: String
    ): Result<VerificationResult> {
        return try {
            val response = client.functions.invoke(
                function = "verify-submission",
                body = buildJsonObject {
                    put("roomId", roomId)
                    put("roundNumber", roundNumber)
                    put("imagePath", imagePath)
                }
            )

            val responseBody = response.body<String>()
            val verifyResponse = json.decodeFromString<VerifySubmissionResponse>(responseBody)

            if (verifyResponse.success) {
                Result.success(
                    VerificationResult(
                        isValid = verifyResponse.valid,
                        reason = verifyResponse.reason,
                        pointsEarned = verifyResponse.points
                    )
                )
            } else {
                Result.failure(
                    Exception(verifyResponse.error ?: "Verification failed")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun skipRound(
        roomId: String,
        userId: String,
        roundNumber: Int
    ): Result<RoundSubmissionDto> {
        return try {
            // Call RPC function to handle skip
            client.postgrest.rpc(
                function = "skip_round",
                parameters = buildJsonObject {
                    put("p_room_id", roomId)
                    put("p_user_id", userId)
                    put("p_round_number", roundNumber)
                }
            )

            // Fetch the created submission
            val submissions = client.postgrest["round_submissions"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                        eq("user_id", userId)
                        eq("round_number", roundNumber)
                    }
                }
                .decodeList<RoundSubmissionDto>()

            if (submissions.isEmpty()) {
                Result.failure(Exception("Skip submission created but could not be fetched"))
            } else {
                Result.success(submissions.first())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserSubmissions(
        roomId: String,
        userId: String
    ): Result<List<RoundSubmissionDto>> {
        return try {
            val submissions = client.postgrest["round_submissions"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                        eq("user_id", userId)
                    }
                }
                .decodeList<RoundSubmissionDto>()

            Result.success(submissions.sortedBy { it.roundNumber })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
