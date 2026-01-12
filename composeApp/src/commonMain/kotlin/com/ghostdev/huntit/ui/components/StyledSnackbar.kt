package com.ghostdev.huntit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.close
import huntit.composeapp.generated.resources.info
import org.jetbrains.compose.resources.painterResource

// Define gamified colors for the snackbar
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val ErrorRed = Color(0xFFFF4B4B) // Bright alert red
private val InfoBlue = Color(0xFF1CB0F6) // Gamified blue (or use MainYellow)

@Composable
fun StyledSnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
        // Takes up the full screen to allow us to control absolute positioning inside
        // purely for the snackbar without blocking touches elsewhere (SnackbarHost handles touch pass-through)
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp) // Pushes it up so it floats above navigation/bottom edge
                .fillMaxWidth(),
            snackbar = { data ->
                val isError = data.visuals.message.startsWith("Error:")
                // Clean the message for display
                val cleanMessage = data.visuals.message.removePrefix("Error:").trim()

                GamifiedSnackbar(
                    message = cleanMessage,
                    isError = isError
                )
            }
        )
    }
}

@Composable
fun GamifiedSnackbar(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    // Configuration based on state
    val backgroundColor = if (isError) ErrorRed else InfoBlue
    val textColor = GameWhite
    val titleText = if (isError) "OH NO!" else "HEY HUNTER!"
    val icon = if (isError) Res.drawable.close else Res.drawable.info

    // Shape styling
    val containerShape = RoundedCornerShape(16.dp)

    // 3D Shadow Logic
    Box(
        modifier = modifier
            .padding(horizontal = 20.dp) // Side padding from screen edges
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 1. The Shadow (Bottom Layer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp, start = 2.dp) // Offset for 3D effect
                .background(GameBlack, containerShape)
        )

        // 2. The Card (Top Layer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, containerShape)
                .border(2.dp, GameBlack, containerShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.2f)), // Subtle dark backing for icon
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = GameWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content
            Column {
                Text(
                    text = titleText,
                    style = TextStyle(
                        fontFamily = testSohneFont(), // Bold font for title
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = textColor.copy(alpha = 0.8f)
                )

                Text(
                    text = message,
                    style = TextStyle(
                        fontFamily = patrickHandFont(), // Handwritten font for body
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = textColor,
                    lineHeight = 20.sp
                )
            }
        }
    }
}