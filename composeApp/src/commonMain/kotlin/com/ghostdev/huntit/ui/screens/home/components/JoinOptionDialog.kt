package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.people
import huntit.composeapp.generated.resources.code
import org.jetbrains.compose.resources.painterResource

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val PublicColor = Color(0xFF42A5F5)
private val PrivateColor = MainYellow
private val GameGrey = Color(0xFFF0F0F0)

private val GameShadowHeight = 4.dp

@Composable
fun JoinOptionDialog(
    onDismiss: () -> Unit,
    onJoinByCode: () -> Unit,
    onBrowsePublicGames: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = GameShadowHeight, x = 2.dp)
                    .height(360.dp)
                    .background(GameBlack, RoundedCornerShape(32.dp))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameWhite, RoundedCornerShape(32.dp))
                    .border(2.dp, GameBlack, RoundedCornerShape(32.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "HOW DO YOU WANT TO PLAY?",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = GameBlack,
                        textAlign = TextAlign.Center
                    )
                }

                // Options container
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircleOptionButton(
                        modifier = Modifier.weight(1f),
                        title = "PUBLIC",
                        description = "Join publicly open games",
                        iconRes = Res.drawable.people,
                        color = PublicColor,
                        onClick = {
                            onDismiss()
                            onBrowsePublicGames()
                        }
                    )
                    
                    // Private Games Option
                    CircleOptionButton(
                        modifier = Modifier.weight(1f),
                        title = "PRIVATE",
                        description = "Use a room code to join private games",
                        iconRes = Res.drawable.code,
                        color = PrivateColor,
                        onClick = {
                            onDismiss()
                            onJoinByCode()
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.dp)
                        .background(GameBlack.copy(alpha = 0.1f))
                )
                
                // Cancel button
                CloseButton(onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun CircleOptionButton(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonScale"
    )
    
    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(110.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .offset(y = GameShadowHeight, x = 2.dp)
                    .background(GameBlack, CircleShape)
                    .align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(color, CircleShape)
                    .border(2.dp, GameBlack, CircleShape)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = GameWhite
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title
        Text(
            text = title,
            style = TextStyle(
                fontFamily = testSohneFont(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = GameBlack
        )
        
        // Description
        Text(
            text = description,
            style = TextStyle(
                fontFamily = patrickHandFont(),
                fontSize = 14.sp
            ),
            color = GameBlack.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CloseButton(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "CloseButtonScale"
    )
    
    Box(
        modifier = Modifier.size(60.dp)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .offset(2.dp, 2.dp)
                .background(GameBlack, CircleShape)
                .align(Alignment.Center)
        )
        
        // Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(GameGrey, CircleShape)
                .border(2.dp, GameBlack, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        audioPlayer?.playSound("files/button_click.mp3")
                        onClick()
                    }
                )
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = GameBlack
            )
        }
    }
}