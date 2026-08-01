package com.ghostdev.huntit.data.repository

import com.ghostdev.huntit.data.model.GameParticipantDto
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameStatus
import com.ghostdev.huntit.data.model.GameTheme
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.data.repository.GameSetupRepository.ActiveGameInfo
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime

@Serializable
private data class JoinGameRoomResponse(
    val success: Boolean,
    @SerialName("room_id") val roomId: String? = null,
    val error: String? = null
)

@Serializable
private data class CreateGameRoomResponse(
    val success: Boolean,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("room_code") val roomCode: String? = null,
    val error: String? = null
)

class GameSetupRepositoryImpl(
    private val client: SupabaseClient
) : GameSetupRepository {

    // Real-time channel for lobby subscriptions
    private var lobbyChannel: RealtimeChannel? = null

    // SharedFlow emitters for real-time updates
    private val _participantsFlow = MutableSharedFlow<List<GameParticipantDto>>(replay = 1)
    private val _gameRoomFlow = MutableSharedFlow<GameRoomDto>(replay = 1)

    // Scope for collecting realtime changes
    private val realtimeScope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalTime::class)
    override suspend fun createGameRoom(
        roomName: String,
        roundDuration: RoundDuration,
        gameTheme: GameTheme,
        maxPlayers: Int?,
        totalRounds: Int,
        cooldownSeconds: Int,
        isPublic: Boolean
    ): Result<String> {
        try {
            if (client.auth.currentUserOrNull() == null) {
                return Result.failure(
                    Exception("User session does not exist.\nLog out and log in again.")
                )
            }

            val response = client.postgrest.rpc(
                function = "create_game_room",
                parameters = buildJsonObject {
                    put("p_room_name", roomName)
                    put("p_round_duration", roundDuration.durationName)
                    put("p_theme", gameTheme.name.lowercase())
                    if (maxPlayers == null) {
                        put("p_max_players", JsonNull)
                    } else {
                        put("p_max_players", maxPlayers)
                    }
                    put("p_total_rounds", totalRounds)
                    put("p_cooldown_seconds", cooldownSeconds)
                    put("p_is_public", isPublic)
                }
            ).decodeAs<CreateGameRoomResponse>()

            if (!response.success) {
                return Result.failure(
                    Exception(response.error ?: "Unable to create game room.")
                )
            }

            val roomCode = response.roomCode
                ?: return Result.failure(Exception("Room was created without a room code."))

            return Result.success(roomCode)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun joinGameRoom(roomCode: String): Result<String> {
        try {
            if (client.auth.currentUserOrNull() == null) {
                return Result.failure(
                    Exception("User session does not exist.\nLog out and log in again.")
                )
            }

            val response = client.postgrest.rpc(
                function = "join_game_room",
                parameters = buildJsonObject {
                    put("p_room_code", roomCode)
                }
            ).decodeAs<JoinGameRoomResponse>()

            if (!response.success) {
                return Result.failure(Exception(response.error ?: "Unable to join game room."))
            }

            return Result.success(roomCode)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun fetchGameRoomDetails(roomCode: String): Result<GameRoomDto> {
        try {
            val gameRooms = client.postgrest["game_rooms"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_code", roomCode)
                    }
                }
                .decodeList<GameRoomDto>()

            if (gameRooms.isEmpty()) {
                return Result.failure(Exception("Game room not found."))
            }

            return Result.success(gameRooms.first())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun checkActiveGameParticipation(): Result<ActiveGameInfo> {
        try {
            val currentUser = client.auth.currentUserOrNull()
                ?: return Result.failure(
                    Exception("User session does not exist.")
                )

            // Get all rooms where the user is a participant with explicit is_playing = true
            var participations = client.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("user_id", currentUser.id)
                        // Only consider participants who are actively playing (not spectators)
                        eq("is_playing", true)
                    }
                }
                .decodeList<GameParticipantDto>()

            
            if (participations.isEmpty()) {
                // Try one more time with a slight delay to ensure database consistency
                delay(300)
                val retryParticipations = client.postgrest["game_participants"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("user_id", currentUser.id)
                            eq("is_playing", true)
                        }
                    }
                    .decodeList<GameParticipantDto>()
                
                if (retryParticipations.isEmpty()) {
                    return Result.failure(Exception("User is not participating in any games."))
                } else {
                    // Use the retry results if we found participations
                    participations = retryParticipations
                }
            }


            // Get the room IDs for all participations, sorted with host rooms first
            val sortedParticipations = participations.sortedByDescending { it.isHost }
            val roomIds = sortedParticipations.map { it.roomId }
            val isPlayingMap = sortedParticipations.associate { it.roomId to it.isPlaying }
            val isHostMap = sortedParticipations.associate { it.roomId to it.isHost }

            // Build a more robust query for game rooms
            val allParticipantRooms = if (roomIds.isNotEmpty()) {
                client.postgrest["game_rooms"]
                    .select(columns = Columns.ALL) {
                        filter {
                            // Use OR conditions for each roomId
                            roomIds.forEach { roomId ->
                                or {
                                    eq("id", roomId)
                                }
                            }
                        }
                    }
                    .decodeList<GameRoomDto>()
            } else {
                emptyList()
            }

            // Sort active games: LOBBY first, then IN_PROGRESS, then FINISHED
            val activeGameRooms = allParticipantRooms
                .filter {
                    it.status == GameStatus.LOBBY || it.status == GameStatus.IN_PROGRESS || it.status == GameStatus.FINISHED
                }
                .sortedWith(
                    compareBy<GameRoomDto> { 
                        // Primary sort: Status priority (LOBBY first, then IN_PROGRESS, then FINISHED)
                        when (it.status) {
                            GameStatus.LOBBY -> 0
                            GameStatus.IN_PROGRESS -> 1
                            GameStatus.FINISHED -> 2
                            else -> 3
                        }
                    }.thenByDescending { 
                        // Secondary sort: Host status (host rooms first)
                        isHostMap[it.id] ?: false
                    }.thenByDescending {
                        // Tertiary sort: Most recently updated
                        it.updatedAt
                    }
                )

            if (activeGameRooms.isEmpty()) {
                return Result.failure(Exception("No active games found."))
            }

            // Return the highest priority game room with playing status
            val topPriorityRoom = activeGameRooms.first()
            val isPlaying = isPlayingMap[topPriorityRoom.id] ?: false // Default to false for safety
            
            return Result.success(ActiveGameInfo(topPriorityRoom, isPlaying))
        } catch (e: Exception) {
            println("ERROR in checkActiveGameParticipation: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun subscribeToParticipants(roomId: String): Flow<List<GameParticipantDto>> {
        return _participantsFlow.asSharedFlow()
            .onStart {
                setupLobbyChannel(roomId)
            }
            .onCompletion {
                // Channel cleanup will be handled by unsubscribeFromLobby()
            }
    }

    override fun subscribeToGameRoom(roomId: String): Flow<GameRoomDto> {
        return _gameRoomFlow.asSharedFlow()
            .onStart {
                setupLobbyChannel(roomId)
            }
            .onCompletion {
                // Channel cleanup will be handled by unsubscribeFromLobby()
            }
    }

    private suspend fun setupLobbyChannel(roomId: String) {
        try {
            // Close existing channel if it exists
            if (lobbyChannel != null) {
                try {
                    lobbyChannel?.unsubscribe()
                } catch (e: Exception) {
                    println("Error unsubscribing from existing channel: ${e.message}")
                }
                lobbyChannel = null
            }

            // Create a unique channel ID to avoid caching problems
            val channelId = "lobby_${roomId}_${(10000..99999).random()}"
            lobbyChannel = client.realtime.channel(channelId)

            val channel = lobbyChannel ?: return

            // Subscribe to participants table changes for this room
            val participantsChangeFlow = channel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "game_participants"
                filter("room_id", FilterOperator.EQ, roomId)
            }

            // Subscribe to game room changes
            val gameRoomChangeFlow = channel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "game_rooms"
                filter("id", FilterOperator.EQ, roomId)
            }

            // Subscribe to the channel first before fetching initial data
            channel.subscribe()

            // Short delay to ensure subscription is active
            delay(100)

            // Fetch initial data and emit
            fetchAndEmitParticipants(roomId)
            fetchAndEmitGameRoom(roomId)

            // Collect participant changes in a coroutine
            realtimeScope.launch {
                participantsChangeFlow.collect { _ ->
                    // Re-fetch all participants when any change occurs
                    fetchAndEmitParticipants(roomId)
                }
            }

            // Collect game room changes in a coroutine
            realtimeScope.launch {
                gameRoomChangeFlow.collect { _ ->

                    // Re-fetch game room when any change occurs and emit to subscribers
                    // The LobbyViewModel will check if the game has started
                    fetchAndEmitGameRoom(roomId)
                }
            }
        } catch (e: Exception) {
            println("Error setting up lobby channel: ${e.message}")
            e.printStackTrace()
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

            _participantsFlow.emit(participants)
        } catch (e: Exception) {
            println("Error fetching participants: ${e.message}")
        }
    }

    private suspend fun fetchAndEmitGameRoom(roomId: String) {
        try {
            val gameRooms = client.postgrest["game_rooms"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", roomId)
                    }
                }
                .decodeList<GameRoomDto>()

            if (gameRooms.isNotEmpty()) {
                val gameRoom = gameRooms.first()
                _gameRoomFlow.emit(gameRoom)
            }
        } catch (e: Exception) {
            println("Error fetching game room: ${e.message}")
        }
    }

    override suspend fun unsubscribeFromLobby() {
        try {
            lobbyChannel?.unsubscribe()
            lobbyChannel = null
        } catch (e: Exception) {
            println("Error unsubscribing from lobby: ${e.message}")
        }
    }

    override suspend fun updateGameRoomSettings(
        roomId: String,
        roomName: String,
        roundDuration: String,
        roundDurationSeconds: Int
    ): Result<Unit> {
        return try {
            client.postgrest["game_rooms"]
                .update({
                    set("room_name", roomName)
                    set("round_duration", roundDuration)
                    set("round_duration_seconds", roundDurationSeconds)
                }) {
                    filter {
                        eq("id", roomId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteGame(roomId: String): Result<Unit> {
        return try {
            // Related participants, challenges, and submissions are removed by
            // the database's ON DELETE CASCADE constraints.
            client.postgrest["game_rooms"]
                .delete {
                    filter {
                        eq("id", roomId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeParticipant(roomId: String, userId: String): Result<Unit> {
        return try {
            client.postgrest["game_participants"]
                .delete {
                    filter {
                        eq("room_id", roomId)
                        eq("user_id", userId)
                    }
                }

            // Force refresh the participants list immediately
            fetchAndEmitParticipants(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            println("Repository: Error removing participant: ${e.message}")
            Result.failure(e)
        }
    }
    
    @OptIn(ExperimentalTime::class)
    override suspend fun fetchPublicGames(): Result<List<GameRoomDto>> {
        return try {
            val publicGames = client.postgrest["game_rooms"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("is_public", true)
                        eq("status", GameStatus.LOBBY.toString().lowercase()) // Only show games in lobby state
                    }
                }
                .decodeList<GameRoomDto>()
                .sortedByDescending { it.createdAt } // Most recent first

            Result.success(publicGames)
        } catch (e: Exception) {
            println("Error fetching public games: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun fetchParticipantCountsForRooms(roomIds: List<String>): Result<Map<String, Int>> {
        if (roomIds.isEmpty()) {
            return Result.success(emptyMap())
        }
        
        return try {
            val participants = client.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        or {
                            roomIds.forEach { roomId ->
                                or {
                                    eq("room_id", roomId)
                                }
                            }
                        }
                        eq("is_playing", true) // Only count active participants
                    }
                }
                .decodeList<GameParticipantDto>()
                
            // Group participants by room ID and count them
            val participantCountMap = participants
                .groupBy { it.roomId }
                .mapValues { it.value.size }

            val resultMap = roomIds.associateWith { roomId ->
                participantCountMap[roomId] ?: 0
            }
            
            Result.success(resultMap)
        } catch (e: Exception) {
            println("Error fetching participant counts: ${e.message}")
            Result.failure(e)
        }
    }
}
