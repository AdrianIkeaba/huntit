package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.clock
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.patrickHandScFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.data.model.RoundDuration

// For backwards compatibility with GameSettingsScreen
@Composable
fun DurationOptionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Just delegate to the new implementation
    GameDurationButton(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick
    )
}

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 2.dp

@Composable
fun RoundDurationCard(
    modifier: Modifier = Modifier,
    selectedDuration: RoundDuration = RoundDuration.STANDARD,
    onDurationChanged: (RoundDuration) -> Unit = {}
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
            horizontalAlignment = Alignment.Start
        ) {
            // Title row with 3D effect
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
                    text = "Round Duration",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = testSohneFont(),
                        color = GameBlack,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration options with 3D button effect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameDurationButton(
                    modifier = Modifier.weight(1f),
                    title = "Quick",
                    subtitle = "${RoundDuration.QUICK.seconds}s",
                    selected = selectedDuration == RoundDuration.QUICK,
                    onClick = { onDurationChanged(RoundDuration.QUICK) }
                )

                GameDurationButton(
                    modifier = Modifier.weight(1f),
                    title = "Standard",
                    subtitle = "${RoundDuration.STANDARD.seconds / 60} min",
                    selected = selectedDuration == RoundDuration.STANDARD,
                    onClick = { onDurationChanged(RoundDuration.STANDARD) }
                )

                GameDurationButton(
                    modifier = Modifier.weight(1f),
                    title = "Marathon",
                    subtitle = "1m 30s",
                    selected = selectedDuration == RoundDuration.MARATHON,
                    onClick = { onDurationChanged(RoundDuration.MARATHON) }
                )
            }
        }
    }
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

    // Animated scale effect for interactive feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonScale"
    )

    // Offset for 3D button effect
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
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
                    onClick = onClick
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