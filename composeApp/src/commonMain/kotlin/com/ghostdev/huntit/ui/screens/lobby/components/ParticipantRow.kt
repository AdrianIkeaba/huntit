package com.ghostdev.huntit.ui.screens.lobby.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.zIndex
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import org.jetbrains.compose.resources.painterResource

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)

@Composable
fun ParticipantRow(
    participants: List<ParticipantUiModel>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .wrapContentWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarSize = 44.dp
        val step = 24.dp

        // Only display up to 3 participants (or 4 if we need to show the +X chip)
        val visibleCount = if (participants.size <= 3) participants.size else 3
        val extraCount = participants.size - visibleCount

        if (participants.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(avatarSize + step * (if (participants.size > 3) 3 else (maxOf(
                        0,
                        participants.size - 1
                    ))))
                    .height(avatarSize)
            ) {
                // Display the first 3 participants (or all if there are less than 3)
                participants.take(visibleCount).forEachIndexed { index, participant ->
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .align(Alignment.CenterStart)
                            .offset(x = step * index)
                            .zIndex(index.toFloat())
                    ) {
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .offset(x = 2.dp, y = 2.dp)
                                .clip(CircleShape)
                                .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                        )
                        
                        // Main Layer (top)
                        Avatars(
                            modifier = Modifier.size(avatarSize),
                            painter = painterResource(HomeViewModel.getProfilePictureById(participant.avatarId)),
                            borderColor = if (participant.isHost) Color(0xFFFFC107) else Color(0xFF2F80ED)
                        )
                    }
                }

                // If there are more than 3 participants, show the +X chip
                if (extraCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .align(Alignment.CenterStart)
                            .offset(x = step * 3)
                            .zIndex(3f)
                    ) {
                        // Shadow Layer
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .offset(x = 2.dp, y = 2.dp)
                                .clip(CircleShape)
                                .background(GameBlack.copy(alpha = 0.2f), CircleShape)
                        )
                        
                        // Main Layer
                        PlusCountChip(
                            modifier = Modifier.size(avatarSize),
                            count = extraCount
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(avatarSize)
                    .height(avatarSize)
            )
        }

        Text(
            modifier = Modifier,
            text = if (participants.isEmpty()) "No players" else "players",
            style = TextStyle(
                fontSize = 16.sp,
                fontFamily = patrickHandFont(),
                fontWeight = FontWeight.Normal,
                color = GameBlack.copy(alpha = 0.7f)
            )
        )
    }
}