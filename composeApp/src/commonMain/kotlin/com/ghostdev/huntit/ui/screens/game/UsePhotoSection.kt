package com.ghostdev.huntit.ui.screens.game

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
import androidx.compose.foundation.layout.size
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
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@Composable
fun UsePhotoSection(
    modifier: Modifier,
    onUsePhotoClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    isEnabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isEnabled) {
            // Show a time's up message when timer has expired
            Text(
                text = "TIME'S UP!",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                ),
                color = Color.Red,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "This round has ended",
                style = TextStyle(
                    fontFamily = patrickHandFont(),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                ),
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Use Photo button (primary action)
        GamifiedActionButton(
            text = if (isEnabled) "USE PHOTO" else "TOO LATE",
            bgColor = if (isEnabled) MainYellow else Color.Gray,
            onClick = onUsePhotoClick,
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = isEnabled
        )
        
        // Try Again button (secondary action)
        GamifiedActionButton(
            text = "TRY AGAIN",
            bgColor = GameGrey,
            onClick = onTryAgainClick,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

@Composable
private fun GamifiedActionButton(
    text: String,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate the vertical offset for the physical "push down" effect
    val offsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f), // Bouncy spring for tactile feel
        label = "ButtonOffset"
    )

    // Adjust colors for disabled state
    val buttonColor = if (enabled) bgColor else bgColor.copy(alpha = 0.6f)
    val borderColor = if (enabled) GameBlack else GameBlack.copy(alpha = 0.4f)
    val shadowColor = if (enabled) GameBlack else GameBlack.copy(alpha = 0.3f)
    val textColor = if (enabled) GameBlack else GameBlack.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .height(58.dp) // Total height reserved including shadow space
            .clickable(
                enabled = enabled,
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
                .height(54.dp) // Match button height
                .background(shadowColor, RoundedCornerShape(16.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(54.dp)
                .background(buttonColor, RoundedCornerShape(16.dp))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                ),
                color = textColor
            )
        }
    }
}