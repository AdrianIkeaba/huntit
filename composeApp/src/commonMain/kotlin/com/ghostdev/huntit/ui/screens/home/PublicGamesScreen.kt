package com.ghostdev.huntit.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.data.model.GameTheme
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.back
// Assuming refresh icon doesn't exist, let's use a different icon
import huntit.composeapp.generated.resources.add
import huntit.composeapp.generated.resources.refresh
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError


private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

private fun formatDurationSeconds(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds == 60 -> "1 min"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}

@Composable
fun PublicGamesScreen(
    innerPadding: PaddingValues,
    navigateBack: () -> Unit,
    navigateToLobby: (String) -> Unit,
    viewModel: PublicGamesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Handle navigation when successfully joining a game
    LaunchedEffect(state.joinSuccessRoomCode) {
        state.joinSuccessRoomCode?.let { roomCode ->
            viewModel.resetJoinSuccess()
            navigateToLobby(roomCode)
        }
    }
    
    // Handle error messages
    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            coroutineScope.launch {
                // Check if it's a validation or known user-friendly error
                val isUserFriendlyError = errorMessage.contains("No public games") || 
                                         errorMessage.contains("Failed to join") ||
                                         errorMessage.contains("Room is full") ||
                                         errorMessage.contains("Game already started") ||
                                         errorMessage.contains("Connection error")
                
                // Only convert technical errors to user-friendly message
                val displayError = if (isUserFriendlyError) {
                    errorMessage // Use known error as-is
                } else {
                    errorMessage.toUserFriendlyError("Something went wrong while loading games.")
                }
                
                // Show the error message without "Error:" prefix for user-friendly errors
                snackbarHostState.showSnackbar(if (isUserFriendlyError) displayError else "Error: $displayError")
            }
            viewModel.clearError()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {
                // Header with back button and title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GamifiedIconButton(
                        iconRes = Res.drawable.back,
                        onClick = navigateBack
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "Public Games",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontFamily = testSohneFont(),
                                fontWeight = FontWeight.Black,
                                color = GameBlack,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "Join an available game room",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = patrickHandFont(),
                                color = GameBlack.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Refresh button
                    GamifiedIconButton(
                        iconRes = Res.drawable.refresh,
                        onClick = { viewModel.loadPublicGames() }
                    )
                }
                
                // Public Games List
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                            color = GameBlack,
                            strokeWidth = 4.dp
                        )
                    } else if (state.games.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Public Games Available",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontFamily = testSohneFont(),
                                    fontWeight = FontWeight.Bold,
                                    color = GameBlack
                                ),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "There are currently no public game rooms available. Try refreshing or create your own game room!",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontFamily = patrickHandFont(),
                                    color = GameBlack.copy(alpha = 0.7f)
                                ),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            GamifiedButton(
                                text = "REFRESH",
                                isLoading = false,
                                onClick = { viewModel.loadPublicGames() }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.games) { game ->
                                PublicGameCard(
                                    game = game,
                                    onClick = { viewModel.selectGame(game) }
                                )
                            }
                        }
                    }
                }
            }
            
            // Join Game Dialog
            if (state.showJoinDialog && state.selectedGame != null) {
                JoinPublicGameDialog(
                    game = state.selectedGame!!,
                    isJoining = state.isJoining,
                    onConfirm = { viewModel.joinSelectedGame() },
                    onDismiss = { viewModel.dismissJoinDialog() }
                )
            }
        }
        
        // Snackbar for errors
        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun PublicGameCard(
    game: PublicGameUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "CardOffset"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Shadow Layer (Static)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .offset(y = (-GameShadowHeight))
                .background(GameBlack, RoundedCornerShape(16.dp))
                .padding(bottom = 4.dp)
        )
        
        // Card Content
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .fillMaxWidth()
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(GameWhite)
                .padding(16.dp)
        ) {
            Column {
                // Room Name and Theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.roomName,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontFamily = testSohneFont(),
                            fontWeight = FontWeight.Bold,
                            color = GameBlack
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .border(1.dp, GameBlack.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = game.theme.displayName,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontFamily = patrickHandFont(),
                                color = GameBlack.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Divider(color = GameBlack.copy(alpha = 0.1f), thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Game Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Round Duration
                    GameDetailItem(
                        label = "Round Duration",
                        value = formatDurationSeconds(game.roundDurationSeconds)
                    )
                    
                    // Total Rounds
                    GameDetailItem(
                        label = "Total Rounds",
                        value = "${game.totalRounds}"
                    )
                    
                    // Cooldown
                    GameDetailItem(
                        label = "Cooldown",
                        value = "${game.cooldownSeconds}s"
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Players Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TAP TO JOIN",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = testSohneFont(),
                            fontWeight = FontWeight.Black,
                            color = MainYellow,
                            letterSpacing = 1.sp
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .background(
                                color = MainYellow,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (game.maxPlayers != null) {
                                "${game.participantsCount}/${game.maxPlayers}"
                            } else {
                                "${game.participantsCount}/∞"
                            },
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontFamily = testSohneFont(),
                                fontWeight = FontWeight.Bold,
                                color = GameBlack
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameDetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontFamily = patrickHandFont(),
                color = GameBlack.copy(alpha = 0.6f)
            )
        )
        
        Text(
            text = value,
            style = TextStyle(
                fontSize = 16.sp,
                fontFamily = testSohneFont(),
                fontWeight = FontWeight.Bold,
                color = GameBlack
            )
        )
    }
}

@Composable
fun JoinPublicGameDialog(
    game: PublicGameUiModel,
    isJoining: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 4.dp)
                    .background(
                        color = GameBlack,
                        shape = RoundedCornerShape(16.dp)
                    )
            )
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = GameBlack,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(
                        color = GameWhite,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Join Game Room",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontFamily = testSohneFont(),
                        fontWeight = FontWeight.Black,
                        color = GameBlack
                    ),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Are you sure you want to join \"${game.roomName}\"?",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Game details summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = GameGrey.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    GameInfoRow(
                        label = "Theme",
                        value = game.theme.displayName
                    )
                    
                    GameInfoRow(
                        label = "Round Duration",
                        value = formatDurationSeconds(game.roundDurationSeconds)
                    )
                    
                    GameInfoRow(
                        label = "Total Rounds",
                        value = "${game.totalRounds}"
                    )
                    
                    GameInfoRow(
                        label = "Players",
                        value = if (game.maxPlayers != null) {
                            "${game.participantsCount}/${game.maxPlayers}"
                        } else {
                            "${game.participantsCount}/unlimited"
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 2.dp,
                                color = GameBlack,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .background(GameGrey)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = testSohneFont(),
                                fontWeight = FontWeight.Bold,
                                color = GameBlack
                            )
                        )
                    }
                    
                    // Join button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 2.dp,
                                color = GameBlack,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = !isJoining,
                                onClick = onConfirm
                            )
                            .background(MainYellow)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = GameBlack,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Join",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontFamily = testSohneFont(),
                                    fontWeight = FontWeight.Bold,
                                    color = GameBlack
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                fontFamily = patrickHandFont(),
                color = GameBlack.copy(alpha = 0.7f)
            )
        )
        
        Text(
            text = value,
            style = TextStyle(
                fontSize = 14.sp,
                fontFamily = testSohneFont(),
                fontWeight = FontWeight.Bold,
                color = GameBlack
            )
        )
    }
}