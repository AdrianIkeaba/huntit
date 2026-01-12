package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)

@Composable
fun LogoutConfirmationDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .background(GameBlack, RoundedCornerShape(20.dp))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameWhite, RoundedCornerShape(20.dp))
                    .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(MainRed.copy(alpha = 0.1f), CircleShape)
                        .border(2.dp, MainRed, CircleShape)
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MainRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "LOG OUT?",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = "Are you sure you want to log out? You'll need to sign in again to continue playing.",
                    style = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    color = GameBlack.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cancel Button
                    DialogButton(
                        text = "CANCEL",
                        bgColor = GameGrey,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )

                    // Logout Button
                    DialogButton(
                        text = "LOG OUT",
                        bgColor = MainRed,
                        isLoading = isLoading,
                        modifier = Modifier.weight(1f),
                        onClick = onConfirmLogout
                    )
                }
            }
        }
    }
}