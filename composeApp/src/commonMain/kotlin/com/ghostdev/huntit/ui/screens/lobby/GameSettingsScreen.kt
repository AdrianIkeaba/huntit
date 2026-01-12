package com.ghostdev.huntit.ui.screens.lobby

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.data.model.RoundDuration
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.back
import huntit.composeapp.generated.resources.clock
import huntit.composeapp.generated.resources.close
import huntit.composeapp.generated.resources.delete
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

// Consistent Game Colors - matching other screens
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameInputBg = Color(0xFFF7F7F7)
private val GameShadowHeight = 4.dp

@Composable
fun GameSettingsScreen(
    innerPadding: PaddingValues,
    onBackClick: () -> Unit,
    viewModel: GameSettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    GameSettingsComponent(
        innerPadding = innerPadding,
        roomName = state.roomName,
        roundDuration = state.roundDuration,
        isLoading = state.isLoading,
        error = state.error,
        onBackClick = onBackClick,
        onRoomNameChange = { viewModel.updateRoomName(it) },
        onRoundDurationChange = { viewModel.updateRoundDuration(it) },
        onSaveSettings = { viewModel.saveSettings() },
        onDeleteGame = { viewModel.deleteGame() }
    )
}

@Composable
fun GameSettingsComponent(
    innerPadding: PaddingValues,
    roomName: String,
    roundDuration: RoundDuration,
    isLoading: Boolean,
    error: String?,
    onBackClick: () -> Unit,
    onRoomNameChange: (String) -> Unit,
    onRoundDurationChange: (RoundDuration) -> Unit,
    onSaveSettings: () -> Unit,
    onDeleteGame: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showDeleteConfirmDialog) {
        DeleteGameDialog(
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                onDeleteGame()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MainGreen,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // Top Bar with Back Button, Title, and Delete Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side: Back button and title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Back button with 3D effect
                                Box(
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    // Shadow Layer
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GameBlack.copy(alpha = 0.3f))
                                    )

                                    // Main Layer
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .border(1.5.dp, GameBlack, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GameGrey)
                                            .clickable(onClick = onBackClick),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            modifier = Modifier.size(20.dp),
                                            painter = painterResource(Res.drawable.back),
                                            contentDescription = "Back",
                                            colorFilter = ColorFilter.tint(GameBlack)
                                        )
                                    }
                                }
                                
                                Text(
                                    text = "GAME SETTINGS",
                                    style = TextStyle(
                                        color = GameBlack,
                                        fontFamily = testSohneFont(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            
                            // Right side: Delete button as an icon
                            Box(
                                modifier = Modifier.size(40.dp)
                            ) {
                                // Shadow Layer
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .offset(x = 2.dp, y = 2.dp)
                                        .clip(CircleShape)
                                        .background(GameBlack.copy(alpha = 0.3f))
                                )

                                // Main Layer
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .border(1.5.dp, GameBlack, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFEEEE))
                                        .clickable { showDeleteConfirmDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(Res.drawable.delete),
                                        contentDescription = "Delete Game",
                                        colorFilter = ColorFilter.tint(MainRed)
                                    )
                                }
                            }
                        }

                        // Error message if any
                        if (error != null) {
                            GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = error,
                                    style = TextStyle(
                                        color = MainRed,
                                        fontFamily = patrickHandFont(),
                                        fontSize = 16.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Room Name Field with 3D styling
                        GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "ROOM NAME",
                                    style = TextStyle(
                                        fontFamily = testSohneFont(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = GameBlack.copy(alpha = 0.6f)
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                GameTextField(
                                    value = roomName,
                                    onValueChange = onRoomNameChange,
                                    placeholder = "Enter room name...",
                                    enabled = true
                                )
                            }
                        }

                        // Round Duration Selection with 3D styling from RoundDurationCard.kt
                        GamifiedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    modifier = Modifier.wrapContentSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Icon with circular background
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(MainYellow, CircleShape)
                                            .border(1.dp, GameBlack, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            modifier = Modifier.size(14.dp),
                                            painter = painterResource(Res.drawable.clock),
                                            contentDescription = null
                                        )
                                    }
                                    
                                    Text(
                                        text = "ROUND DURATION",
                                        style = TextStyle(
                                            fontFamily = testSohneFont(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = GameBlack.copy(alpha = 0.6f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GameDurationButton(
                                        modifier = Modifier.weight(1f),
                                        title = "Quick",
                                        subtitle = "${RoundDuration.QUICK.seconds}s",
                                        selected = roundDuration == RoundDuration.QUICK,
                                        onClick = { onRoundDurationChange(RoundDuration.QUICK) }
                                    )

                                    GameDurationButton(
                                        modifier = Modifier.weight(1f),
                                        title = "Standard",
                                        subtitle = "${RoundDuration.STANDARD.seconds / 60} min",
                                        selected = roundDuration == RoundDuration.STANDARD,
                                        onClick = { onRoundDurationChange(RoundDuration.STANDARD) }
                                    )

                                    GameDurationButton(
                                        modifier = Modifier.weight(1f),
                                        title = "Marathon",
                                        subtitle = "1m 30s",
                                        selected = roundDuration == RoundDuration.MARATHON,
                                        onClick = { onRoundDurationChange(RoundDuration.MARATHON) }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // Save Settings Button with 3D effect - full width and prominent
                        GamifiedActionButton(
                            text = "SAVE CHANGES",
                            bgColor = MainGreen,
                            onClick = { onSaveSettings() },
                            enabled = roomName.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GamifiedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Shadow layer (bottom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = GameShadowHeight)
                .background(GameBlack, RoundedCornerShape(20.dp))
                .matchParentSize()
        )

        // Content layer (top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameWhite, RoundedCornerShape(20.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
        ) {
            content()
        }
    }
}

@Composable
fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean
) {
    val shape = RoundedCornerShape(12.dp)
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = patrickHandFont(),
            fontSize = 18.sp,
            color = GameBlack
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(GameInputBg, shape)
                    .border(2.dp, GameBlack.copy(alpha = 0.1f), shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 18.sp,
                            color = Color.Gray.copy(alpha = 0.6f)
                        )
                    )
                }
                innerTextField()
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun GameDurationButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current
    
    // Animated scale effect for interactive feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonScale"
    )
    
    // Offset for 3D button effect
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight/2 else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(80.dp)
    ) {
        // Shadow layer (static at bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter)
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Content layer (moves when pressed)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = offsetY)
                .background(
                    color = if (selected) MainYellow else GameWhite,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.5.dp, GameBlack, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        audioPlayer?.playSound("files/button_click.mp3")
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = testSohneFont(),
                        fontWeight = FontWeight.Bold,
                        color = GameBlack,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = patrickHandFont(),
                        color = if (selected) GameBlack else GameBlack.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                )

                // Selected indicator
                if (selected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(GameBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = GameWhite,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GamifiedActionButton(
    text: String,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current
    
    val actualBgColor = if (enabled) bgColor else bgColor.copy(alpha = 0.5f)
    
    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    Box(
        modifier = modifier
            .height(58.dp) // Total height reserved
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
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
                .background(actualBgColor, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun DeleteGameDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Main Dialog Box with 3D effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Shadow Layer (bottom part)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameShadowHeight) // Push down to create 3D effect
                    .background(GameBlack, RoundedCornerShape(20.dp))
                    .height(220.dp)
            )

            // Content Layer (top part)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameWhite, RoundedCornerShape(20.dp))
                    .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Title
                    Text(
                        text = "DELETE GAME",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = GameBlack
                        )
                    )

                    // Message
                    Text(
                        text = "Are you sure you want to delete this game? All participants will be removed and the game data will be permanently deleted.",
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 16.sp,
                            color = GameBlack.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Cancel Button
                        DialogButton(
                            text = "CANCEL",
                            bgColor = GameGrey,
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        )

                        // Confirm Button
                        DialogButton(
                            text = "DELETE",
                            bgColor = MainRed,
                            modifier = Modifier.weight(1f),
                            onClick = onConfirm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "DialogButtonOffset"
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            )
    ) {
        // Shadow (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(42.dp)
                .background(GameBlack, RoundedCornerShape(12.dp))
        )

        // Touchable Button (Moves up and down)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(42.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.5.dp, GameBlack, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (bgColor == MainRed) Color.White else GameBlack
            )
        }
    }
}