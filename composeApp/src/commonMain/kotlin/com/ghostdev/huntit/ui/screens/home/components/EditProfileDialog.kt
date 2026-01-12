package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ghostdev.huntit.data.model.User
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.logout
import org.jetbrains.compose.resources.painterResource

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameInputBg = Color(0xFFF7F7F7)
private val GameGrey = Color(0xFFE5E5E5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    user: User,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (displayName: String, avatarId: Int) -> Unit,
    onLogout: () -> Unit
) {
    var selectedAvatarId by remember { mutableStateOf(user.avatarId) }
    var displayName by remember { mutableStateOf(user.displayName) }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                // Title
                Text(
                    text = "EDIT PROFILE",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Avatar Display
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Shadow Layer
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .offset(x = 3.dp, y = 3.dp)
                            .clip(CircleShape)
                            .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                    )

                    // Main Avatar Image
                    Image(
                        painter = painterResource(
                            HomeViewModel.getProfilePictureById(
                                selectedAvatarId
                            )
                        ),
                        contentDescription = "Selected Avatar",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, GameBlack, CircleShape)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Display Name Input
                Text(
                    text = "DISPLAY NAME",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(8.dp))

                BasicTextField(
                    value = displayName,
                    onValueChange = {
                        if (it.length <= 20) {
                            displayName = it
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 18.sp,
                        color = GameBlack
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(GameInputBg, RoundedCornerShape(12.dp))
                                .border(
                                    2.dp,
                                    GameBlack.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            innerTextField()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // Avatar Selection
                Text(
                    text = "CHOOSE AVATAR",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(12.dp))

                // Avatar Grid
                FlowRow(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 4,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1..8) {
                        AvatarOption(
                            avatarId = i,
                            isSelected = selectedAvatarId == i,
                            onClick = { selectedAvatarId = i }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

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

                    // Save Button
                    DialogButton(
                        text = "SAVE",
                        bgColor = MainYellow,
                        isLoading = isLoading,
                        enabled = displayName.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = { onSave(displayName, selectedAvatarId) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // Logout Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(onClick = onLogout)
                        .padding(8.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.logout),
                        contentDescription = "Log out",
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.size(8.dp))

                    Text(
                        text = "LOG OUT",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MainRed
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarOption(
    avatarId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "AvatarOffset"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(6.dp)
            .size(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .offset(x = 2.dp, y = 2.dp)
                .clip(CircleShape)
                .background(GameBlack, CircleShape)
        )

        Image(
            painter = painterResource(HomeViewModel.getProfilePictureById(avatarId)),
            contentDescription = "Avatar Option $avatarId",
            modifier = Modifier
                .offset(y = offsetY)
                .size(52.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.5.dp,
                    color = if (isSelected) MainYellow else GameBlack,
                    shape = CircleShape
                )
                .background(
                    if (isSelected) MainYellow.copy(alpha = 0.2f) else Color.Transparent,
                    CircleShape
                )
        )
    }
}