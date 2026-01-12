package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.people
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.patrickHandScFont
import com.ghostdev.huntit.ui.theme.testSohneFont

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)

enum class MaxPlayersOption { NO_LIMIT, SET_LIMIT }

@Composable
fun MaxPlayersCard(
    modifier: Modifier = Modifier,
    maxPlayers: Int?,
    onMaxPlayersChanged: (Int?) -> Unit = {}
) {
    var selected by remember { mutableStateOf(if (maxPlayers == null) MaxPlayersOption.NO_LIMIT else MaxPlayersOption.SET_LIMIT) }
    var limitText by remember { mutableStateOf(maxPlayers?.takeIf { it > 0 }?.toString() ?: "") }
    val focusRequester = remember { FocusRequester() }

    // Initialize from props if needed
    LaunchedEffect(maxPlayers) {
        selected = if (maxPlayers == null) MaxPlayersOption.NO_LIMIT else MaxPlayersOption.SET_LIMIT
        limitText = maxPlayers?.takeIf { it > 0 }?.toString() ?: ""
    }

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
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        painter = painterResource(Res.drawable.people),
                        contentDescription = null
                    )
                }

                Text(
                    text = "Max Players",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = testSohneFont(),
                        color = GameBlack,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // No limit option
            GamifiedRadioOption(
                text = "No limit",
                isSelected = selected == MaxPlayersOption.NO_LIMIT,
                onClick = {
                    selected = MaxPlayersOption.NO_LIMIT
                    onMaxPlayersChanged(null)  // null means no limit
                }
            )

            // Set limit option with input field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selected = MaxPlayersOption.SET_LIMIT
                        val currentValue = limitText.toIntOrNull()?.takeIf { it >= 2 } ?: 8
                        limitText = currentValue.toString()
                        onMaxPlayersChanged(currentValue)
                        focusRequester.requestFocus()
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio button with bouncy animation
                GamifiedRadioButton(
                    isSelected = selected == MaxPlayersOption.SET_LIMIT
                )

                Text(
                    text = "Set limit:",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack
                    )
                )

                // Input field with 3D effect
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .width(54.dp)
                        .offset(y = 2.dp) // 3D shadow effect
                        .background(GameBlack.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .offset(y = -2.dp) // Move content back
                        .background(GameWhite, RoundedCornerShape(12.dp))
                        .border(
                            1.5.dp,
                            GameBlack.copy(alpha = if (selected == MaxPlayersOption.SET_LIMIT) 1f else 0.3f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = limitText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }.take(2)
                            limitText = filtered

                            if (selected == MaxPlayersOption.SET_LIMIT) {
                                if (filtered.isNotEmpty()) {
                                    val playerCount = filtered.toInt()
                                    // Only update with valid value if >= 2
                                    if (playerCount >= 2) {
                                        onMaxPlayersChanged(playerCount)
                                    } else {
                                        // Signal an invalid state to the parent with -1
                                        onMaxPlayersChanged(-1)
                                    }
                                } else {
                                    // Empty field - signal invalid state to parent
                                    onMaxPlayersChanged(-1)
                                }
                            }
                        },
                        singleLine = true,
                        enabled = selected == MaxPlayersOption.SET_LIMIT,
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontFamily = patrickHandFont(),
                            color = GameBlack,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .fillMaxWidth(),
                        decorationBox = { inner ->
                            if (limitText.isEmpty()) {
                                Text(
                                    text = "8",
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontFamily = patrickHandFont(),
                                        color = Color(0xFFBBBBBB),
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            inner()
                        }
                    )
                }

                Text(
                    text = "players",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack
                    )
                )
            }
        }
    }
}

@Composable
fun GamifiedRadioOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GamifiedRadioButton(isSelected = isSelected)

        Text(
            text = text,
            style = TextStyle(
                fontSize = 14.sp,
                fontFamily = patrickHandFont(),
                color = GameBlack
            )
        )
    }
}

@Composable
fun GamifiedRadioButton(
    isSelected: Boolean
) {
    // Bouncy animation for selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "RadioButtonScale"
    )

    // Outer circle with border
    Box(
        modifier = Modifier
            .size(22.dp)
            .scale(scale)
            .background(GameWhite, CircleShape)
            .border(1.5.dp, GameBlack, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Inner circle for selected state
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MainYellow, CircleShape)
                    .border(1.dp, GameBlack.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}