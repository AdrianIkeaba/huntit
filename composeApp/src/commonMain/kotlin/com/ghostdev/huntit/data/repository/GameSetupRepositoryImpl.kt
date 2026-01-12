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
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

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
            val currentUser = client.auth.currentUserOrNull()
                ?: return Result.failure(
                    Exception("User session does not exist.\nLog out and log in again.")
                )

            val roomCode = (100_000..999_999).random().toString()

            val gameRoomDto = GameRoomDto(
                roomCode = roomCode,
                roomName = roomName,
                hostId = currentUser.id,
                theme = gameTheme,
                roundDuration = roundDuration.durationName,
                roundDurationSeconds = roundDuration.seconds,
                totalRounds = totalRounds,
                cooldownSeconds = cooldownSeconds,
                maxPlayers = maxPlayers,
                isPublic = isPublic,
                phaseEndsAt = null
            )

            // Insert the game room and get back the created record
            var createdRooms = client.postgrest["game_rooms"]
                .insert(gameRoomDto) { select() }
                .decodeList<GameRoomDto>()

            // If that doesn't work, try fetching it with a retry mechanism
            if (createdRooms.isEmpty()) {
                // Add a short delay to allow database propagation
                delay(500)

                // Try to fetch the inserted room to get its ID
                val fetchedRooms = client.postgrest["game_rooms"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("room_code", roomCode)
                        }
                    }
                    .decodeList<GameRoomDto>()

                if (fetchedRooms.isNotEmpty()) {
                    createdRooms = fetchedRooms
                }
            }

            if (createdRooms.isEmpty()) {
                return Result.failure(Exception("Room created but couldn't fetch details. Room code: $roomCode"))
            }

            val createdRoom = createdRooms.first()
            val roomId = createdRoom.id ?: return Result.failure(Exception("Room ID is missing"))

            // Add the host as a participant
            val participantDto = GameParticipantDto(
                roomId = roomId,
                userId = currentUser.id,
                isHost = true,
                joinOrder = 1,
                isPlaying = true  // Set is_playing to true when creating a game
            )

            try {
                client.postgrest["game_participants"]
                    .insert(participantDto)
            } catch (e: Exception) {
                println("Warning: Created room but failed to add host as participant: ${e.message}")
            }

            return Result.success(roomCode)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun joinGameRoom(roomCode: String): Result<String> {
        try {
            val currentUser = client.auth.currentUserOrNull()
                ?: return Result.failure(
                    Exception("User session does not exist.\nLog out and log in again.")
                )

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

            val gameRoom = gameRooms.first()

            if (gameRoom.status != GameStatus.LOBBY) {
                return Result.failure(Exception("Game has already started."))
            }

            val roomId = gameRoom.id
                ?: return Result.failure(Exception("Game room ID is missing."))

            val participants = client.postgrest["game_participants"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("room_id", roomId)
                    }
                }
                .decodeList<GameParticipantDto>()

            // Check if room is full (only if maxPlayers is set)
            if (gameRoom.maxPlayers != null && gameRoom.maxPlayers > 0 && participants.size >= gameRoom.maxPlayers) {
                return Result.failure(Exception("Game room is full."))
            }

            // Check if user is already in the room
            val existingParticipant = participants.find { it.userId == currentUser.id }
            if (existingParticipant != null) {
                // Update is_playing to true if they're rejoining
                if (!existingParticipant.isPlaying) {
                    client.postgrest["game_participants"]
                        .update({
                            set("is_playing", true)
                        }) {
                            filter {
                                eq("room_id", gameRoom.id)
                                eq("user_id", currentUser.id)
                            }
                        }
                }
                return Result.success(roomCode)
            }

            // Add user to participants
            val participantDto = GameParticipantDto(
                roomId = gameRoom.id,
                userId = currentUser.id,
                isHost = false,
                joinOrder = participants.size + 1,
                isPlaying = true  // Set is_playing to true when joining a game
            )

            client.postgrest["game_participants"].insert(participantDto)
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

            // Add debugging to track participations found
            println("DEBUG: Found ${participations.size} active participations for user ${currentUser.id}")
            
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

            // Log each participation for debugging
            participations.forEach { participation ->
                println("DEBUG: Participation found: roomId=${participation.roomId}, isHost=${participation.isHost}, isPlaying=${participation.isPlaying}")
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
                
            // Log rooms for debugging
            println("DEBUG: Found ${allParticipantRooms.size} game rooms for user participation")
            allParticipantRooms.forEach { room ->
                println("DEBUG: Room: id=${room.id}, code=${room.roomCode}, status=${room.status}")
            }

            // Sort active games: LOBBY first, then IN_PROGRESS, then FINISHED
            // For equal status, prioritize host rooms, then most recently updated
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
            println("DEBUG: Selected room: id=${topPriorityRoom.id}, code=${topPriorityRoom.roomCode}, status=${topPriorityRoom.status}, isPlaying=$isPlaying")
            
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
                println("Closing existing lobby channel and creating a new one")
                try {
                    lobbyChannel?.unsubscribe()
                } catch (e: Exception) {
                    println("Error unsubscribing from existing channel: ${e.message}")
                }
                lobbyChannel = null
            }

            println("Setting up new lobby channel for room $roomId")
            // Create a unique channel ID to avoid caching issues
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
            println("Subscribed to channel $channelId")

            // Short delay to ensure subscription is active
            delay(100)

            // Fetch initial data and emit
            fetchAndEmitParticipants(roomId)
            fetchAndEmitGameRoom(roomId)

            // Collect participant changes in a coroutine
            realtimeScope.launch {
                println("Starting participant changes flow for room $roomId")
                participantsChangeFlow.collect { action ->
                    println("Received participant change: $action")

                    // Re-fetch all participants when any change occurs
                    fetchAndEmitParticipants(roomId)
                }
            }

            // Collect game room changes in a coroutine
            realtimeScope.launch {
                println("Starting game room changes flow for room $roomId")
                gameRoomChangeFlow.collect { action ->
                    println("Received game room change: $action")

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

            println("Emitting participants update: ${participants.size} participants")
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
                println("Emitting game room update: Room ${gameRoom.id}, Status: ${gameRoom.status}, Phase: ${gameRoom.currentPhase}")
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
            // First, delete all participants
            client.postgrest["game_participants"]
                .delete {
                    filter {
                        eq("room_id", roomId)
                    }
                }

            // Then, delete the game room
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
            println("Repository: Removing participant $userId from room $roomId")
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
                
            // Ensure all requested roomIds are in the map, even with zero participants
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
