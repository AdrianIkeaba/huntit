package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import org.jetbrains.compose.resources.painterResource
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.clock
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.add
import huntit.composeapp.generated.resources.minus

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 2.dp

@Composable
fun CooldownDurationCard(
    modifier: Modifier = Modifier,
    cooldownSeconds: Int,
    onCooldownChanged: (Int) -> Unit
) {
    BaseCardComponent(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.wrapContentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Column {
                    Text(
                        text = "Cooldown Between Rounds",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontFamily = testSohneFont(),
                            color = GameBlack,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Give players time to catch their breath",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = patrickHandFont(),
                            color = GameBlack.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Number controls with 3D effect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus button (decrement by 10 seconds)
                CooldownControlButton(
                    control = 1,
                    enabled = cooldownSeconds > 10,
                    onClick = { if (cooldownSeconds > 10) onCooldownChanged(cooldownSeconds - 10) }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Current value display
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .offset(y = GameShadowHeight)
                        .background(GameBlack, RoundedCornerShape(16.dp))
                        .offset(y = -GameShadowHeight)
                        .background(GameWhite, RoundedCornerShape(16.dp))
                        .border(1.5.dp, GameBlack, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = if (cooldownSeconds == 1) "$cooldownSeconds second" else "$cooldownSeconds seconds",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontFamily = patrickHandFont(),
                            color = GameBlack,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                CooldownControlButton(
                    control = 2,
                    enabled = cooldownSeconds < 90,
                    onClick = { if (cooldownSeconds < 90) onCooldownChanged(cooldownSeconds + 10) }
                )
            }
        }
    }
}

@Composable
private fun CooldownControlButton(
    control: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonScale"
    )

    val offsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GameBlack)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .offset(y = offsetY)
                .clip(RoundedCornerShape(16.dp))
                .background(if (enabled) MainYellow else GameGrey)
                .border(1.5.dp, GameBlack, RoundedCornerShape(16.dp))
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Image(
                painter = painterResource(
                    if (control == 1) {
                        Res.drawable.minus
                    } else {
                        Res.drawable.add
                    }
                ),
                contentDescription = "Controls"
            )
        }
    }
}