package com.ghostdev.huntit.ui.screens.lobby.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont

// Consistent Game Colors - matching other screens
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 3.dp

@Composable
fun LeaveRoomDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
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
                    .height(200.dp)
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Title
                    Text(
                        text = "LEAVE GAME ROOM?",
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
                        text = "Are you sure you want to leave this game room?",
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 16.sp,
                            color = GameBlack.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        DialogButton(
                            text = "CANCEL",
                            onClick = onDismiss,
                            color = GameGrey,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Leave button
                        DialogButton(
                            text = "LEAVE",
                            onClick = onConfirm,
                            color = Color(0xFFF39E9E), // Light red for leave button
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f), // Bouncy spring
        label = "ButtonOffset"
    )

    Box(
        modifier = modifier
            .height(50.dp) // Total height reserved
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple, using custom animation
                onClick = onClick
            )
    ) {
        // Shadow Layer (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp) // Match button height
                .background(GameBlack, RoundedCornerShape(12.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(color, RoundedCornerShape(12.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(12.dp)),
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
                color = GameBlack
            )
        }
    }
}