package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.hashtag
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.patrickHandScFont
import com.ghostdev.huntit.ui.theme.testSohneFont

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)

@Composable
fun GameRoomNameCard(
    modifier: Modifier = Modifier,
    roomName: String = "",
    onRoomNameChanged: (String) -> Unit = {}
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
                        painter = painterResource(Res.drawable.hashtag),
                        contentDescription = null
                    )
                }

                Text(
                    text = "Room Name",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = testSohneFont(),
                        color = GameBlack,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated text field with focus effect
            GamifiedTextField(
                value = roomName,
                onValueChange = { if (it.length <= 30) onRoomNameChanged(it) },
                placeholder = "Enter room name..."
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${roomName.length}/30",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack.copy(alpha = 0.6f),
                        textAlign = TextAlign.End
                    )
                )
            }
        }
    }
}

@Composable
private fun GamifiedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Animation for focus state
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) 8f else 4f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "ShadowAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = GameBlack.copy(alpha = 0.2f)
            )
            .background(GameWhite, RoundedCornerShape(16.dp))
            .border(
                2.dp,
                GameBlack.copy(alpha = if (isFocused) 0.8f else 0.4f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontFamily = patrickHandFont(),
                color = GameBlack
            ),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = patrickHandFont(),
                            color = GameBlack.copy(alpha = 0.4f),
                            lineHeight = 14.sp
                        )
                    )
                }
                innerTextField()
            }
        )
    }
}