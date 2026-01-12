package com.ghostdev.huntit.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.data.model.GameTheme as DataGameTheme
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.screens.home.components.CooldownDurationCard
import com.ghostdev.huntit.ui.screens.home.components.GameRoomNameCard
import com.ghostdev.huntit.ui.screens.home.components.GameTheme
import com.ghostdev.huntit.ui.screens.home.components.GameThemeCard
import com.ghostdev.huntit.ui.screens.home.components.GameVisibilityCard
import com.ghostdev.huntit.ui.screens.home.components.MaxPlayersCard
import com.ghostdev.huntit.ui.screens.home.components.NumberOfRoundsCard
import com.ghostdev.huntit.ui.screens.home.components.RoundDurationCard
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.back
import huntit.composeapp.generated.resources.fashion_icon
import huntit.composeapp.generated.resources.indoors_icon
import huntit.composeapp.generated.resources.outdoors_icon
import huntit.composeapp.generated.resources.pop_culture
import huntit.composeapp.generated.resources.pop_culture_icon
import huntit.composeapp.generated.resources.school
import huntit.composeapp.generated.resources.school_icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@Composable
fun CreateGameRoomScreen(
    innerPadding: PaddingValues,
    navigateBack: () -> Unit,
    navigateToLobby: (String) -> Unit,
    viewModel: CreateGameRoomViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Reset state when screen is closed
    DisposableEffect(Unit) {
        onDispose { viewModel.resetState() }
    }

    // Navigation Effect
    LaunchedEffect(state.createdRoomCode) {
        state.createdRoomCode?.let { roomCode ->
            navigateToLobby(roomCode)
        }
    }

    // Error Handling
    LaunchedEffect(state.error) {
        if (state.error != null) {
            coroutineScope.launch {
                // Get the error string
                val errorString = state.error ?: "Unknown error"
                
                // Check if it's a validation error (these are already user-friendly)
                val isValidationError = errorString.contains("Room name can't be empty") ||
                                       errorString.contains("Maximum players must be at least 2")
                
                // Only convert technical errors to user-friendly message
                val displayError = if (isValidationError) {
                    errorString // Use validation error as-is
                } else {
                    errorString.toUserFriendlyError("Something went wrong while creating the game room.")
                }
                
                snackbarHostState.showSnackbar(if (isValidationError) displayError else "Error: $displayError")
            }
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CreateGameRoomComponent(
            innerPadding = innerPadding,
            roomName = state.roomName,
            selectedRoundDuration = state.roundDuration,
            selectedTheme = state.gameTheme,
            maxPlayers = state.maxPlayers,
            totalRounds = state.totalRounds,
            cooldownSeconds = state.cooldownSeconds,
            isPublic = state.isPublic,
            isLoading = state.isLoading,
            onRoomNameChanged = viewModel::updateRoomName,
            onRoundDurationChanged = viewModel::updateRoundDuration,
            onThemeChanged = viewModel::updateGameTheme,
            onMaxPlayersChanged = viewModel::updateMaxPlayers,
            onTotalRoundsChanged = viewModel::updateTotalRounds,
            onCooldownSecondsChanged = viewModel::updateCooldownSeconds,
            onVisibilityChanged = viewModel::updateIsPublic,
            onBackClick = { navigateBack() },
            onCreateRoomClick = { viewModel.createRoom() }
        )

        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
@Preview
private fun CreateGameRoomComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    roomName: String = "",
    selectedRoundDuration: RoundDuration = RoundDuration.QUICK,
    selectedTheme: DataGameTheme = DataGameTheme.OUTDOORS_NATURE,
    maxPlayers: Int? = null,
    totalRounds: Int = 3,
    cooldownSeconds: Int = 30,
    isPublic: Boolean = false,
    isLoading: Boolean = false,
    onRoomNameChanged: (String) -> Unit = {},
    onRoundDurationChanged: (RoundDuration) -> Unit = {},
    onThemeChanged: (DataGameTheme) -> Unit = {},
    onMaxPlayersChanged: (Int?) -> Unit = {},
    onTotalRoundsChanged: (Int) -> Unit = {},
    onCooldownSecondsChanged: (Int) -> Unit = {},
    onVisibilityChanged: (Boolean) -> Unit = {},
    onBackClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {}
) {
    val uiGameThemes = remember {
        listOf(
            GameTheme(0, Res.drawable.outdoors_icon, "Outdoors & Nature", "Trees, flowers, rocks, insects, birds"),
            GameTheme(1, Res.drawable.indoors_icon, "Indoors & House", "Chairs, spoons, clocks, pillows, lamps"),
            GameTheme(2, Res.drawable.fashion_icon, "Fashion & Style", "Hats, shoes, belts, watches, sunglasses"),
            GameTheme(3, Res.drawable.school_icon, "School & Study", "Pens, notebooks, rulers, backpacks"),
            GameTheme(4, Res.drawable.pop_culture_icon, "Pop Culture & Fun", "Toys, posters, game controllers")
        )
    }

    AnimatedBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            // -- Header --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamifiedIconButton(
                    iconRes = Res.drawable.back,
                    onClick = onBackClick
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Create Game Room",
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontFamily = testSohneFont(),
                            fontWeight = FontWeight.Black,
                            color = GameBlack,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "Setup your Hunt.it game",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontFamily = patrickHandFont(),
                            color = GameBlack.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // -- Scrollable Form --
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    GameRoomNameCard(
                        roomName = roomName,
                        onRoomNameChanged = onRoomNameChanged
                    )
                }
                item {
                    RoundDurationCard(
                        selectedDuration = selectedRoundDuration,
                        onDurationChanged = onRoundDurationChanged
                    )
                }
                item {
                    NumberOfRoundsCard(
                        totalRounds = totalRounds,
                        onTotalRoundsChanged = onTotalRoundsChanged
                    )
                }
                item {
                    CooldownDurationCard(
                        cooldownSeconds = cooldownSeconds,
                        onCooldownChanged = onCooldownSecondsChanged
                    )
                }
                item {
                    GameThemeCard(
                        themes = uiGameThemes,
                        selectedTheme = selectedTheme,
                        onThemeChanged = onThemeChanged
                    )
                }
                item {
                    MaxPlayersCard(
                        maxPlayers = maxPlayers,
                        onMaxPlayersChanged = onMaxPlayersChanged
                    )
                }
                item {
                    GameVisibilityCard(
                        isPublic = isPublic,
                        onVisibilityChanged = onVisibilityChanged
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    val isFormValid = roomName.isNotBlank() &&
                            (maxPlayers == null || (maxPlayers >= 2 && maxPlayers != -1))

                    GamifiedButton(
                        text = "CREATE GAME ROOM",
                        isLoading = isLoading,
                        isEnabled = !isLoading && isFormValid,
                        onClick = onCreateRoomClick
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// UI COMPONENTS
// -------------------------------------------------------------------------

@Composable
fun GamifiedIconButton(
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    val shadowHeight = 4.dp
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) shadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "IconOffset"
    )

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            )
    ) {
        // Shadow Layer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(size - shadowHeight)
                .background(GameBlack, RoundedCornerShape(12.dp))
        )

        // Top Layer
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(size - shadowHeight)
                .background(GameWhite, RoundedCornerShape(12.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(GameBlack)
            )
        }
    }
}

@Composable
fun GamifiedButton(
    text: String,
    isLoading: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    // Animation states
    val shadowHeight = GameShadowHeight
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) shadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    // Color transitions for disabled state
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MainYellow else GameGrey,
        label = "BtnBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isEnabled) GameBlack else GameBlack.copy(alpha = 0.4f),
        label = "BtnText"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = {
                    if (isEnabled) {
                        audioPlayer?.playSound("files/button_click.mp3")
                    }
                    onClick()
                }
            )
    ) {
        // Shadow (Static)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp)
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Touchable Content
        Box(
            modifier = Modifier
                .offset(y = if (isEnabled) offsetY else 0.dp) // No bounce if disabled
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(54.dp)
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = GameBlack,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = text.uppercase(),
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}