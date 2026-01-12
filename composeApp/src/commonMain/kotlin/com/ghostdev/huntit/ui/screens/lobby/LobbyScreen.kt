package com.ghostdev.huntit.ui.screens.lobby

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ghostdev.huntit.data.model.GameRoomDto
import com.ghostdev.huntit.data.model.GameTheme
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.QuickRulesDialog
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.screens.lobby.components.KickedAlertDialog
import com.ghostdev.huntit.ui.screens.lobby.components.LeaveRoomDialog
import com.ghostdev.huntit.ui.screens.lobby.components.ParticipantRow
import com.ghostdev.huntit.ui.screens.lobby.components.ParticipantsBottomSheet
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.patrickHandScFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.clock
import huntit.composeapp.generated.resources.copy
import huntit.composeapp.generated.resources.fashion
import huntit.composeapp.generated.resources.info
import huntit.composeapp.generated.resources.logout
import huntit.composeapp.generated.resources.fashion_icon
import huntit.composeapp.generated.resources.indoors
import huntit.composeapp.generated.resources.indoors_icon
import huntit.composeapp.generated.resources.outdoors
import huntit.composeapp.generated.resources.outdoors_icon
import huntit.composeapp.generated.resources.pop_culture_icon
import huntit.composeapp.generated.resources.school_icon
import huntit.composeapp.generated.resources.paint
import huntit.composeapp.generated.resources.play
import huntit.composeapp.generated.resources.pop_culture
import huntit.composeapp.generated.resources.school
import huntit.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

// Consistent Game Colors - matching other screens
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

// Helper function to format seconds to display format
private fun formatDurationSecondsLobby(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds == 60 -> "1 min"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    innerPadding: PaddingValues,
    navigateToGame: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToHome: () -> Unit = {},
    viewModel: LobbyViewModel = koinViewModel()
) {

    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboard.current

    // Handle game started - navigate to game screen
    androidx.compose.runtime.LaunchedEffect(state.gameStarted) {
        if (state.gameStarted) {
            viewModel.onGameStartedHandled()
            navigateToGame()
        }
    }

    // Handle kicked state
    if (state.showKickedDialog) {
        KickedAlertDialog(
            onDismiss = {
                viewModel.dismissKickedDialog()
                viewModel.clearRoomCode()
                navigateToHome()
            }
        )
    }

    LobbyComponent(
        innerPadding = innerPadding,
        roomCode = viewModel.getCurrentRoomCode(),
        gameRoomDetails = state.gameRoomDetails,
        participants = state.participants,
        isLoading = state.isLoading,
        isStartingGame = state.isStartingGame,
        error = state.error,
        isHost = state.isHost,
        currentUserId = viewModel.getCurrentUserId(),
        onCopyClick = { viewModel.copyRoomCode(clipboard) },
        onStartGameClick = { viewModel.startGame() },
        onSettingsClick = { navigateToSettings() },
        onLeaveClick = { 
            viewModel.leaveGameRoom()
            viewModel.clearRoomCode()
            navigateToHome() 
        },
        onRemoveParticipant = { participant ->
            viewModel.removeParticipant(participant.id)
        },
        onErrorShown = { viewModel.clearError() }  // Add this parameter
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun LobbyComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    roomCode: String = "ABC123",
    gameRoomDetails: GameRoomDto? = null,
    participants: List<ParticipantUiModel> = emptyList(),
    isLoading: Boolean = false,
    isStartingGame: Boolean = false,
    error: String? = null,
    isHost: Boolean = true,
    currentUserId: String? = null,
    onCopyClick: () -> Unit = {},
    onStartGameClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLeaveClick: () -> Unit = {},
    onRemoveParticipant: (ParticipantUiModel) -> Unit = {},
    onErrorShown: () -> Unit = {} // Add callback for when error is shown
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var showQuickRulesDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (showQuickRulesDialog) {
        QuickRulesDialog(onDismiss = { showQuickRulesDialog = false })
    }
    
    if (showLeaveConfirmDialog) {
        LeaveRoomDialog(
            onConfirm = {
                showLeaveConfirmDialog = false
                onLeaveClick()
            },
            onDismiss = { showLeaveConfirmDialog = false }
        )
    }

    // Display error as snackbar instead of error screen
    LaunchedEffect(error) {
        if (error != null) {
            // Check if this is a validation or known user-friendly error
            val isUserFriendlyError = error.contains("At least 2 players are needed") ||
                                     error.contains("Room does not exist") ||
                                     error.contains("Game is full")
            
            // Only convert technical errors to user-friendly message
            val displayError = if (isUserFriendlyError) {
                error // Use validation error as-is
            } else {
                error.toUserFriendlyError("Something went wrong with the game room.")
            }
            
            // Show the error message without "Error:" prefix for user-friendly errors
            snackbarHostState.showSnackbar(if (isUserFriendlyError) displayError else "Error: $displayError")
            
            // Call the callback to clear the error in the ViewModel
            onErrorShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .wrapContentSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                // Fixed: Add modifier for proper width constraints
                                modifier = Modifier.fillMaxWidth(0.7f) // Allow max 70% of parent width for the row
                            ) {
                                Text(
                                    text = "${gameRoomDetails?.roomName ?: "Game Room"} Lobby",
                                    style = TextStyle(
                                        color = GameBlack,
                                        fontFamily = testSohneFont(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    // Fixed: Added text truncation handling
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    // Fixed: Add weight to allow text to be truncated properly
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Public/Private Badge
                                val isPublic = gameRoomDetails?.isPublic ?: false
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isPublic) Color(0xFF90CAF9) else Color(
                                                0xFFE1BEE7
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            GameBlack.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPublic) "🌐" else "🔒",
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (isPublic) "Public" else "Private",
                                            style = TextStyle(
                                                color = GameBlack.copy(alpha = 0.7f),
                                                fontFamily = patrickHandFont(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Waiting for players to join...",
                                style = TextStyle(
                                    color = GameBlack.copy(alpha = 0.6f),
                                    fontFamily = patrickHandFont(),
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .wrapContentSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Settings icon with 3D effect (only for host)
                            if (isHost) {
                                Box(
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    // Shadow Layer
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .clip(CircleShape)
                                            .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                                    )

                                    // Main Layer
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .border(1.5.dp, GameBlack, CircleShape)
                                            .clip(CircleShape)
                                            .background(GameGrey, CircleShape)
                                            .clickable(onClick = onSettingsClick),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            modifier = Modifier.size(20.dp),
                                            painter = painterResource(Res.drawable.settings),
                                            contentDescription = "Settings",
                                            colorFilter = ColorFilter.tint(GameBlack)
                                        )
                                    }
                                }
                            } 
                            // Exit icon with 3D effect (only for non-hosts)
                            else {
                                Box(
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    // Shadow Layer
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .clip(CircleShape)
                                            .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                                    )

                                    // Main Layer
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .border(1.5.dp, GameBlack, CircleShape)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF39E9E), CircleShape) // Light red color for exit button
                                            .clickable { 
                                                showLeaveConfirmDialog = true 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            modifier = Modifier.size(20.dp),
                                            painter = painterResource(Res.drawable.logout),
                                            contentDescription = "Leave Game",
                                            colorFilter = ColorFilter.tint(GameBlack)
                                        )
                                    }
                                }
                            }

                            // Info icon with 3D effect
                            Box(
                                modifier = Modifier.size(36.dp)
                            ) {
                                // Shadow Layer
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .offset(x = 2.dp, y = 2.dp)
                                        .clip(CircleShape)
                                        .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                                )

                                // Main Layer
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .border(1.5.dp, GameBlack, CircleShape)
                                                                                    .clip(CircleShape)
                                            .background(GameGrey, CircleShape)
                                            .clickable { 
                                                showQuickRulesDialog = true 
                                            },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(Res.drawable.info),
                                        contentDescription = "Game Rules",
                                        colorFilter = ColorFilter.tint(GameBlack)
                                    )
                                }
                            }
                        }
                    }

                    // Participants Row
                    ParticipantRow(
                        participants = participants,
                        onClick = { showBottomSheet = true }
                    )

                    // Room Code Card with 3D effect
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Shadow Layer (bottom)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                                .offset(y = GameShadowHeight)
                                .background(GameBlack, RoundedCornerShape(20.dp))
                        )

                        // Content Layer (top)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                                .background(GameWhite, RoundedCornerShape(20.dp))
                                .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            // Background image based on game theme
                            val themeImage = when (gameRoomDetails?.theme) {
                                GameTheme.INDOORS_HOUSE -> Res.drawable.indoors
                                GameTheme.FASHION_STYLE -> Res.drawable.fashion
                                GameTheme.SCHOOL_STUDY -> Res.drawable.school
                                GameTheme.POP_CULTURE -> Res.drawable.pop_culture
                                else -> Res.drawable.outdoors // Default/fallback for OUTDOORS_NATURE
                            }
                            
                            Image(
                                modifier = Modifier.fillMaxSize(),
                                painter = painterResource(themeImage),
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                colorFilter = ColorFilter.tint(
                                    Color.White.copy(alpha = 0.5f),
                                    androidx.compose.ui.graphics.BlendMode.Lighten
                                )
                            )

                            // Room code content
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Room Code",
                                    style = TextStyle(
                                        fontFamily = testSohneFont(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GameBlack
                                    )
                                )

                                Text(
                                    text = roomCode,
                                    style = TextStyle(
                                        fontFamily = testSohneFont(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        letterSpacing = 4.sp,
                                        color = GameBlack
                                    ),
                                    // Fixed: Add max lines and overflow handling
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    // Fixed: Add modifier to constrain width and properly center
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )

                                // Copy button with 3D effect
                                GamifiedActionButton(
                                    text = "COPY",
                                    iconRes = Res.drawable.copy,
                                    bgColor = Color(0xFF2F80ED),
                                    onClick = onCopyClick,
                                    modifier = Modifier.width(130.dp)
                                )
                            }
                        }
                    }

                    // Game Details Card with 3D effect
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Shadow Layer (bottom)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = GameShadowHeight)
                                .background(GameBlack, RoundedCornerShape(20.dp))
                        )

                        // Content Layer (top)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFDF9F3), RoundedCornerShape(20.dp))
                                .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                                .padding(24.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(Res.drawable.clock),
                                        contentDescription = null,
                                        tint = GameBlack.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Duration: ${gameRoomDetails?.roundDuration ?: "Quick"} (${
                                            formatDurationSecondsLobby(
                                                gameRoomDetails?.roundDurationSeconds ?: 30
                                            )
                                        })",
                                        fontFamily = patrickHandFont(),
                                        fontSize = 16.sp,
                                        color = GameBlack.copy(alpha = 0.7f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(Res.drawable.paint),
                                        contentDescription = null,
                                        tint = GameBlack.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Theme: ${gameRoomDetails?.theme?.displayName ?: "Outdoors & Nature"}",
                                        fontFamily = patrickHandFont(),
                                        fontSize = 16.sp,
                                        color = GameBlack.copy(alpha = 0.7f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(Res.drawable.play),
                                        contentDescription = null,
                                        tint = GameBlack.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Rounds: ${gameRoomDetails?.totalRounds ?: 5}",
                                        fontFamily = patrickHandFont(),
                                        fontSize = 16.sp,
                                        color = GameBlack.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Start Game Button (only for host)
                    if (isHost) {
                        GamifiedActionButton(
                            text = if (isStartingGame) "STARTING..." else "START GAME",
                            iconRes = Res.drawable.play,
                            bgColor = MainGreen,
                            onClick = onStartGameClick,
                            isLoading = isStartingGame
                        )
                    } else {
                        // For non-hosts, show a "waiting for host" message
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "Waiting for host to start the game...",
                            style = TextStyle(
                                fontFamily = patrickHandFont(),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                color = GameBlack.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                // Show bottom sheet for participants list
                if (showBottomSheet) {
                    ParticipantsBottomSheet(
                        participants = participants,
                        sheetState = sheetState,
                        coroutineScope = scope,
                        isCurrentUserHost = isHost,
                        currentUserId = currentUserId,
                        onRemoveParticipant = onRemoveParticipant,
                        onDismiss = { showBottomSheet = false }
                    )
                }
            }
        }

        // Add the StyledSnackbarHost
        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.zIndex(10f) // Make sure it appears above all other content
        )
    }
}

@Composable
fun GamifiedActionButton(
    text: String,
    iconRes: DrawableResource? = null,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = com.ghostdev.huntit.utils.LocalAudioPlayer.current

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f), // Bouncy spring
        label = "ButtonOffset"
    )

    Box(
        modifier = modifier
            .height(58.dp) // Total height reserved
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple, using custom animation
                enabled = !isLoading,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            )
    ) {
        // Shadow Layer (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp) // Match button height
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(54.dp)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Icon if provided
                    if (iconRes != null) {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(if (bgColor == Color.White) GameBlack else Color.White)
                        )
                        Spacer(Modifier.size(12.dp))
                    }

                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (bgColor == Color.White) GameBlack else Color.White
                    )
                }
            }
        }
    }
}