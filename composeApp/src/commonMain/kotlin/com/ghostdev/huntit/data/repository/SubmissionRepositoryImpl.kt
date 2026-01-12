package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.RoundSubmissionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

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

            val publicUrl = bucket.publicUrl(fileName)
            Result.success(publicUrl)
        } catch (e: Exception) {
            println("Error uploading image: ${e.message}")
            Result.failure(e)
        }
    }

    @Serializable
    private data class VerifySubmissionResponse(
        val success: Boolean,
        val valid: Boolean = false,
        val reason: String = "",
        val error: String? = null
    )

    override suspend fun verifyPhoto(
        imageBase64: String,
        challengeText: String,
        theme: String,
        roomId: String?,
        roundNumber: Int?
    ): Result<VerificationResult> {
        return try {
            // Log important info for debugging
            println("Verifying photo for challenge: \"$challengeText\", theme: $theme")
            println("Image base64 length: ${imageBase64.length} chars")

            // Make sure the base64 string doesn't have any prefixes
            var cleanBase64 = imageBase64
            if (cleanBase64.contains("base64,")) {
                cleanBase64 = cleanBase64.substring(cleanBase64.indexOf("base64,") + 7)
            }

            // Check if base64 needs padding
            val remainder = cleanBase64.length % 4
            if (remainder > 0) {
                cleanBase64 += "=".repeat(4 - remainder)
            }

            // Log base64 details for debugging
            println("Base64 length after cleaning: ${cleanBase64.length}")
            println("First 20 chars: ${cleanBase64.take(20)}...")

            val response = client.functions.invoke(
                function = "verify-submission",
                body = buildJsonObject {
                    put("imageBase64", cleanBase64)
                    put("challenge", challengeText)
                    put("theme", theme)
                    // Add roomId and roundNumber if available (for server-side validation)
                    roomId?.let { put("roomId", it) }
                    roundNumber?.let { put("roundNumber", it) }
                }
            )

            val responseBody = response.body<String>()
            println("Verify submission response: $responseBody")

            try {
                val verifyResponse = json.decodeFromString<VerifySubmissionResponse>(responseBody)

                if (verifyResponse.success) {
                    Result.success(
                        VerificationResult(
                            isValid = verifyResponse.valid,
                            reason = verifyResponse.reason,
                            confidence = 0f
                        )
                    )
                } else {
                    // Log detailed error for debugging
                    println("Error verifying photo: ${verifyResponse.error}")
                    println("URL: https://actohkyaftjkgpjpgpil.supabase.co/functions/v1/verify-submission")
                    println("Http Method: POST")

                    Result.success(
                        VerificationResult(
                            isValid = false,
                            reason = verifyResponse.error ?: "Verification failed",
                            confidence = 0f
                        )
                    )
                }
            } catch (jsonEx: Exception) {
                println("Error parsing response JSON: ${jsonEx.message}")
                println("Raw response body: $responseBody")

                Result.success(
                    VerificationResult(
                        isValid = false,
                        reason = "Error processing verification response: ${jsonEx.message}",
                        confidence = 0f
                    )
                )
            }
        } catch (e: Exception) {
            // Log detailed network error
            println("Error verifying photo: ${e.message}")
            println("URL: https://actohkyaftjkgpjpgpil.supabase.co/functions/v1/verify-submission")

            try {
                val headers = e.toString()
                println("Headers: $headers")
            } catch (ex: Exception) { /* ignore */
            }

            println("Http Method: POST")

            // Return a default failure result if verification fails
            Result.success(
                VerificationResult(
                    isValid = false,
                    reason = "Unable to verify photo: ${e.message}",
                    confidence = 0f
                )
            )
        }
    }

    override suspend fun submitRound(
        roomId: String,
        userId: String,
        roundNumber: Int,
        imageUrl: String?,
        isSuccess: Boolean
    ): Result<RoundSubmissionDto> {
        return try {
            // Call RPC function to handle submission
            client.postgrest.rpc(
                function = "submit_round",
                parameters = buildJsonObject {
                    put("p_room_id", roomId)
                    put("p_user_id", userId)
                    put("p_round_number", roundNumber)
                    put("p_image_url", imageUrl)
                    put("p_is_success", isSuccess)
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
                Result.failure(Exception("Submission created but could not be fetched"))
            } else {
                Result.success(submissions.first())
            }
        } catch (e: Exception) {
            println("Error submitting round: ${e.message}")
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
            println("Error skipping round: ${e.message}")
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
            println("Error getting user submissions: ${e.message}")
            Result.failure(e)
        }
    }
}
