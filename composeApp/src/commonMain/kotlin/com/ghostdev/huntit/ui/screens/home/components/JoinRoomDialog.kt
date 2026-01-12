package com.ghostdev.huntit.ui.screens.home.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.ui.screens.home.JoinGameViewModel
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import org.koin.compose.viewmodel.koinViewModel

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameInputBg = Color(0xFFF7F7F7)
private val GameGrey = Color(0xFFE5E5E5)
private val ErrorRed = Color(0xFFFF4B4B)
private val GameShadowHeight = 4.dp

@Composable
fun JoinRoomDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    viewModel: JoinGameViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Clear any errors when the dialog is shown
    LaunchedEffect(Unit) {
        viewModel.clearError()
    }

    // Handle successful join
    LaunchedEffect(state.joinSuccess) {
        if (state.joinSuccess) {
            viewModel.resetJoinSuccess()
            onJoin(state.roomCode)
        }
    }

    // Reset state when dialog is dismissed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        // Dialog Box with 3D effect
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Shadow Layer (bottom part)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp) // Push down to create depth
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .background(GameBlack, RoundedCornerShape(20.dp))
            )

            // Main Content Layer (top part)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameWhite, RoundedCornerShape(20.dp))
                    .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "JOIN GAME ROOM",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Enter the 6-digit room code",
                    style = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 16.sp,
                    ),
                    color = GameBlack.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Room Code Input
                GameTextField(
                    value = state.roomCode,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(6)
                        viewModel.updateRoomCode(filtered)
                    },
                    isError = state.error != null,
                    placeholder = "000000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Error message
                state.error?.let { errorMessage ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ErrorRed,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    DialogButton(
                        text = "CANCEL",
                        bgColor = GameGrey,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )

                    // Join Button
                    DialogButton(
                        text = "JOIN",
                        bgColor = MainYellow,
                        isLoading = state.isLoading,
                        enabled = state.roomCode.length == 6 && !state.isLoading,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.joinGameRoom() }
                    )
                }
            }
        }
    }
}

@Composable
fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    placeholder: String,
    keyboardOptions: KeyboardOptions
) {
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = patrickHandFont(),
            fontSize = 20.sp,
            color = GameBlack,
            textAlign = TextAlign.Center,
            letterSpacing = 4.sp
        ),
        keyboardOptions = keyboardOptions,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(GameInputBg, shape)
                    .border(2.dp, if (isError) ErrorRed else GameBlack.copy(alpha = 0.1f), shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 20.sp,
                            color = Color.Gray.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
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
fun DialogButton(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    Box(
        modifier = modifier
            .height(50.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (enabled) {
                        audioPlayer?.playSound("files/button_click.mp3")
                    }
                    onClick()
                }
            )
    ) {
        // Shadow Layer (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(
                    if (enabled) bgColor else GameGrey.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = GameBlack,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack.copy(alpha = if (enabled) 1f else 0.5f)
                )
            }
        }
    }
}