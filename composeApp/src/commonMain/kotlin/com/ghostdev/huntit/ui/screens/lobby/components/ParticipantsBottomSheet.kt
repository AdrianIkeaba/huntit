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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.close
import huntit.composeapp.generated.resources.host_crown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

// Consistent Game Colors - matching other screens
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsBottomSheet(
    participants: List<ParticipantUiModel>,
    sheetState: SheetState,
    coroutineScope: CoroutineScope,
    isCurrentUserHost: Boolean = false,
    currentUserId: String? = null,
    onRemoveParticipant: (ParticipantUiModel) -> Unit = {},
    onDismiss: () -> Unit
) {
    // State for showing remove confirmation dialog
    val showRemoveDialog = remember { mutableStateOf<ParticipantUiModel?>(null) }

    // Sort participants so host is first, then alphabetically by name
    val sortedParticipants = participants.sortedWith(
        compareByDescending<ParticipantUiModel> { it.isHost }
            .thenBy { it.name }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GameWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PARTICIPANTS",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = GameBlack
                    )
                )
                
                // Close button with 3D effect
                Box(
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                coroutineScope.launch {
                                    sheetState.hide()
                                }.invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        onDismiss()
                                    }
                                }
                            },
                        tint = GameBlack
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GameBlack.copy(alpha = 0.1f))
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(sortedParticipants) { participant ->
                    val isLast = participant == sortedParticipants.last()
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with 3D effect
                            Box(
                                modifier = Modifier.size(48.dp)
                            ) {
                                // Shadow Layer (bottom)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(top = 2.dp, start = 2.dp)
                                        .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                                )

                                // Main Layer (top)
                                Avatars(
                                    modifier = Modifier.size(48.dp),
                                    painter = painterResource(
                                        HomeViewModel.getProfilePictureById(
                                            participant.avatarId
                                        )
                                    ),
                                    borderColor = if (participant.isHost) Color(0xFFFFC107) else Color.Transparent
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Text(
                                text = participant.name,
                                style = TextStyle(
                                    fontFamily = patrickHandFont(),
                                    fontSize = 18.sp,
                                    color = GameBlack
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (participant.isHost) {
                                Icon(
                                    painter = painterResource(Res.drawable.host_crown),
                                    contentDescription = "Host",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Show remove button only if current user is host and this is not the current user
                            if (isCurrentUserHost && participant.id != currentUserId && !participant.isHost) {
                                // Remove button with 3D effect
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFFFEEEE), CircleShape)
                                        .border(1.dp, Color(0xFFEF4444), CircleShape)
                                        .clickable { showRemoveDialog.value = participant },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.close),
                                        contentDescription = "Remove participant",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(GameBlack.copy(alpha = 0.1f))
                            )
                        }
                    }
                }
            }
        }
    }

    // Show remove confirmation dialog if needed
    showRemoveDialog.value?.let { participant ->
        RemoveParticipantDialog(
            participantName = participant.name,
            onConfirm = {
                onRemoveParticipant(participant)
                showRemoveDialog.value = null
            },
            onDismiss = {
                showRemoveDialog.value = null
            }
        )
    }
}