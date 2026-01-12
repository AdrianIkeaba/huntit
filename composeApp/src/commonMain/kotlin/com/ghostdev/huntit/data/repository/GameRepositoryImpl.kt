package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.GameChallengeDto
import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GamePhase
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.model.RoundSubmissionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.call.body
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class GameRepositoryImpl(
    private val client: SupabaseClient
) : GameRepository {

    // Real-time channel for game subscriptions
    private var gameChannel: RealtimeChannel? = null

    // SharedFlow emitters for real-time updates
    private val _gameRoomFlow = MutableSharedFlow<GameRoomDto>(replay = 1)
    private val _participantsFlow = MutableSharedFlow<List<GameParticipantDto>>(replay = 1)
    private val _submissionsFlow = MutableSharedFlow<List<RoundSubmissionDto>>(replay = 1)

    // Scope for collecting realtime changes
    private val realtimeScope = CoroutineScope(Dispatchers.Default)

    // Track current room for re-fetching
    private var currentRoomId: String? = null

    override suspend fun getGameRoom(roomId: String): Result<GameRoomDto> {
        return try {
            val rooms = client.postgrest["game_rooms"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", roomId)
                    }
                }
                .decodeList<GameRoomDto>()

            if (rooms.isEmpty()) {
                Result.failure(Exception("Game room not found"))
            } else {
                Result.success(rooms.first())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGameRoomByCode(roomCode: String): Result<GameRoomDto> {
        return try {
            val rooms = client.postgrest["game_rooms"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_code", roomCode)
                    }
                }
                .decodeList<GameRoomDto>()

            if (rooms.isEmpty()) {
                Result.failure(Exception("Game room not found"))
            } else {
                Result.success(rooms.first())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getParticipants(roomId: String): Result<List<GameParticipantDto>> {
        return try {
            val participants = client.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                    }
                }
                .decodeList<GameParticipantDto>()

            Result.success(participants)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getChallenges(roomId: String): Result<List<GameChallengeDto>> {
        return try {
            val challenges = client.postgrest["game_challenges"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                    }
                }
                .decodeList<GameChallengeDto>()

            Result.success(challenges.sortedBy { it.roundNumber })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getChallenge(roomId: String, roundNumber: Int): Result<GameChallengeDto> {
        return try {
            val challenges = client.postgrest["game_challenges"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                        eq("round_number", roundNumber)
                    }
                }
                .decodeList<GameChallengeDto>()

            if (challenges.isEmpty()) {
                Result.failure(Exception("Challenge not found for round $roundNumber"))
            } else {
                Result.success(challenges.first())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun advanceGamePhase(roomId: String): Result<Unit> {
        return try {
            client.postgrest.rpc(
                function = "advance_game_phase",
                parameters = buildJsonObject {
                    put("p_room_id", roomId)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            println("Error advancing game phase: ${e.message}")
            Result.failure(e)
        }
    }

    @Serializable
    private data class GenerateChallengesResponse(
        val success: Boolean,
        val error: String? = null
    )

    @Serializable
    private data class StartGameResponse(
        val success: Boolean,
        val error: String? = null
    )

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    override suspend fun startGame(roomId: String): Result<Unit> {
        return try {
            // Get current user ID
            val userId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("User not authenticated"))

            // CRITICAL FIX: First, check if there are at least 2 participants in the game
            val participantsResult = getParticipants(roomId)
            if (participantsResult.isFailure) {
                return Result.failure(
                    participantsResult.exceptionOrNull() ?: Exception("Failed to check participants")
                )
            }
            
            val participants = participantsResult.getOrNull() ?: emptyList()
            if (participants.size < 2) {
                return Result.failure(Exception("At least 2 players are required to start a game"))
            }
            
            // Next, get room details to know theme and total rounds
            val roomResult = getGameRoom(roomId)
            if (roomResult.isFailure) {
                return Result.failure(
                    roomResult.exceptionOrNull() ?: Exception("Failed to get room")
                )
            }
            val room = roomResult.getOrNull()!!

            // Generate challenges using edge function
            println("Generating challenges for room $roomId, theme: ${room.theme.name.lowercase()}, rounds: ${room.totalRounds}")

            val generateResponse = client.functions.invoke(
                function = "generate-challenges",
                body = buildJsonObject {
                    put("roomId", roomId)
                    put("theme", room.theme.name.lowercase())
                    put("totalRounds", room.totalRounds)
                }
            )

            val generateBody = generateResponse.body<String>()
            println("Generate challenges response: $generateBody")

            // Check if challenge generation succeeded
            val generateResult = json.decodeFromString<GenerateChallengesResponse>(generateBody)
            if (!generateResult.success) {
                return Result.failure(Exception("Failed to generate challenges: ${generateResult.error ?: "Unknown error"}"))
            }

            // Now start the game with the RPC function
            println("Starting game for room $roomId with user $userId")
            val startResult = client.postgrest.rpc(
                function = "start_game",
                parameters = buildJsonObject {
                    put("p_room_id", roomId)
                    put("p_user_id", userId)
                }
            ).decodeAs<StartGameResponse>()

            println("Start game response: $startResult")

            // Check if start succeeded
            if (!startResult.success) {
                return Result.failure(Exception(startResult.error ?: "Failed to start game"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            println("Error starting game: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun getServerTime(): Result<Instant> {
        return try {
            // RPC returns a timestamp string directly
            val response = client.postgrest.rpc("get_server_time")
                .decodeAs<String>()

            // Parse the ISO 8601 timestamp (remove any surrounding quotes)
            val timestamp = response.trim().removeSurrounding("\"")
            val instant = Instant.parse(timestamp)
            Result.success(instant)
        } catch (e: Exception) {
            println("Error getting server time: ${e.message}")
            // Fallback to local time if server time fails
            Result.success(Clock.System.now())
        }
    }

    override fun subscribeToGameRoom(roomId: String): Flow<GameRoomDto> {
        return _gameRoomFlow.asSharedFlow()
            .onStart {
                setupGameChannel(roomId)
            }
            .onCompletion {
                // Channel cleanup will be handled by unsubscribeFromGame()
            }
    }

    override fun subscribeToParticipants(roomId: String): Flow<List<GameParticipantDto>> {
        return _participantsFlow.asSharedFlow()
            .onStart {
                setupGameChannel(roomId)
            }
            .onCompletion {
                // Channel cleanup will be handled by unsubscribeFromGame()
            }
    }

    override fun subscribeToSubmissions(roomId: String): Flow<List<RoundSubmissionDto>> {
        return _submissionsFlow.asSharedFlow()
            .onStart {
                setupGameChannel(roomId)
            }
            .onCompletion {
                // Channel cleanup will be handled by unsubscribeFromGame()
            }
    }

    private suspend fun setupGameChannel(roomId: String) {
        // Skip if already set up for this room
        if (currentRoomId == roomId && gameChannel != null) {
            return
        }

        try {
            // Close existing channel if it exists
            if (gameChannel != null) {
                println("Closing existing game channel")
                try {
                    gameChannel?.unsubscribe()
                } catch (e: Exception) {
                    println("Error unsubscribing from existing channel: ${e.message}")
                }
                gameChannel = null
            }

            currentRoomId = roomId
            println("Setting up game channel for room $roomId")

            // Create a unique channel ID
            val channelId = "game_${roomId}_${(10000..99999).random()}"
            gameChannel = client.realtime.channel(channelId)

            val channel = gameChannel ?: return

            // Subscribe to game_rooms changes
            val gameRoomChangeFlow = channel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "game_rooms"
                filter("id", FilterOperator.EQ, roomId)
            }

            // Subscribe to game_participants changes
            val participantsChangeFlow = channel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "game_participants"
                filter("room_id", FilterOperator.EQ, roomId)
            }

            // Subscribe to round_submissions changes
            val submissionsChangeFlow = channel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "round_submissions"
                filter("room_id", FilterOperator.EQ, roomId)
            }

            // Subscribe to the channel first
            channel.subscribe()
            println("Subscribed to game channel $channelId")

            // Short delay to ensure subscription is active
            delay(100)

            // Fetch initial data and emit
            fetchAndEmitGameRoom(roomId)
            fetchAndEmitParticipants(roomId)
            fetchAndEmitSubmissions(roomId)

            // Collect game room changes
            realtimeScope.launch {
                println("Starting game room changes flow")
                gameRoomChangeFlow.collect { action ->
                    println("Received game room change: $action")
                    fetchAndEmitGameRoom(roomId)
                }
            }

            // Collect participants changes
            realtimeScope.launch {
                println("Starting participants changes flow")
                participantsChangeFlow.collect { action ->
                    println("Received participants change: $action")
                    fetchAndEmitParticipants(roomId)
                }
            }

            // Collect submissions changes
            realtimeScope.launch {
                println("Starting submissions changes flow")
                submissionsChangeFlow.collect { action ->
                    println("Received submissions change: $action")
                    fetchAndEmitSubmissions(roomId)
                }
            }

        } catch (e: Exception) {
            println("Error setting up game channel: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun fetchAndEmitGameRoom(roomId: String) {
        try {
            val rooms = client.postgrest["game_rooms"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", roomId)
                    }
                }
                .decodeList<GameRoomDto>()

            if (rooms.isNotEmpty()) {
                println("Emitting game room update: ${rooms.first()}")
                _gameRoomFlow.emit(rooms.first())
            }
        } catch (e: Exception) {
            println("Error fetching game room: ${e.message}")
        }
    }

    private suspend fun fetchAndEmitParticipants(roomId: String) {
        try {
            val participants = client.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                    }
                }
                .decodeList<GameParticipantDto>()

            println("Emitting participants update: ${participants.size} participants")
            _participantsFlow.emit(participants)
        } catch (e: Exception) {
            println("Error fetching participants: ${e.message}")
        }
    }

    private suspend fun fetchAndEmitSubmissions(roomId: String) {
        try {
            val submissions = client.postgrest["round_submissions"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                    }
                }
                .decodeList<RoundSubmissionDto>()

            println("Emitting submissions update: ${submissions.size} submissions")
            _submissionsFlow.emit(submissions)
        } catch (e: Exception) {
            println("Error fetching submissions: ${e.message}")
        }
    }

    override suspend fun unsubscribeFromGame() {
        try {
            gameChannel?.unsubscribe()
            gameChannel = null
            currentRoomId = null
        } catch (e: Exception) {
            println("Error unsubscribing from game: ${e.message}")
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

    override suspend fun setUserPlaying(
        roomId: String,
        userId: String,
        isPlaying: Boolean
    ): Result<Unit> {
        return try {
            // Update is_playing field for the user in this game
            client.postgrest["game_participants"]
                .update({
                    set("is_playing", isPlaying)
                }) {
                    filter {
                        eq("room_id", roomId)
                        eq("user_id", userId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            println("Error updating playing status: ${e.message}")
            Result.failure(e)
        }
    }
}
