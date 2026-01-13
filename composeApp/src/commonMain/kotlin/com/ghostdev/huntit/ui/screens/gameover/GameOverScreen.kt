package com.ghostdev.huntit.ui.screens.gameover

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.data.model.ParticipantUiModel
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.bronze_trophy
import huntit.composeapp.generated.resources.close
import huntit.composeapp.generated.resources.gold_trophy
import huntit.composeapp.generated.resources.profile_picture
import huntit.composeapp.generated.resources.profile_picture_2
import huntit.composeapp.generated.resources.profile_picture_3
import huntit.composeapp.generated.resources.profile_picture_4
import huntit.composeapp.generated.resources.profile_picture_5
import huntit.composeapp.generated.resources.profile_picture_6
import huntit.composeapp.generated.resources.profile_picture_7
import huntit.composeapp.generated.resources.profile_picture_8
import huntit.composeapp.generated.resources.silver_trophy
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

// Consistent Design System Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameGrey = Color(0xFFE5E5E5)

@Composable
fun GameOverScreen(
    innerPadding: PaddingValues,
    navigateToHome: () -> Unit = {}
) {
    val viewModel: GameOverViewModel = koinInject()
    GameOverComponent(innerPadding, viewModel, navigateToHome)
}

@Composable
fun GameOverComponent(
    innerPadding: PaddingValues,
    viewModel: GameOverViewModel,
    navigateToHome: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Get the audio player to play sound effects
    val audioPlayer = LocalAudioPlayer.current
    
    // Play success sound when leaderboard data is loaded
    LaunchedEffect(state.leaderboard, state.error) {
        if (!state.isLoading && state.error == null && state.leaderboard.isNotEmpty()) {
            // Get the current user ID from the ViewModel
            val currentUserId = viewModel.getCurrentUserId()
            
            // Find the current user's entry and rank
            val currentUserRank = state.leaderboard.find { it.participant.id == currentUserId }?.rank ?: 0
            
            // Play appropriate sound based on ranking
            if (currentUserRank in 1..3) {
                audioPlayer?.playSound("files/success.mp3")
            } else if (currentUserRank > 3) {
                // Still played the game, but not in top 3
                audioPlayer?.playSound("files/failed.mp3")
            }
        } else if (!state.isLoading && state.error != null) {
            // Error loading results
            audioPlayer?.playSound("files/failed.mp3")
        }
    }

    // 1. Replaced static background with AnimatedBackground to match GameScreen
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else if (state.error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorMessage(state.error!!)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Header Section
                    ScreenHeader(state.roomName ?: "Game Over", viewModel, navigateToHome)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Podium Section (Top 3)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        WinnersPodiumNew(state.leaderboard.take(3))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // List Section (Rank 4+)
                    // Removed the complex Surface/Sheet look for a cleaner floating card list
                    val remainingList = if (state.leaderboard.size > 3) state.leaderboard.drop(3) else emptyList()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.55f)
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "RUNNERS UP",
                            style = TextStyle(
                                fontFamily = testSohneFont(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GameBlack,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )

                        if (remainingList.isNotEmpty()) {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(remainingList) { entry ->
                                    LeaderboardRowNew(entry)
                                }
                            }
                        } else {
                            // Empty state
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No other players... yet!",
                                    style = TextStyle(
                                        fontFamily = patrickHandFont(),
                                        fontSize = 18.sp,
                                        color = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(
    roomName: String,
    viewModel: GameOverViewModel,
    navigateToHome: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(0.35f)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White)
                .border(1.5.dp, GameBlack, RoundedCornerShape(30.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = roomName.uppercase(),
                style = TextStyle(
                    color = GameBlack,
                    fontFamily = testSohneFont(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(0.45f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LEADERBOARD",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = GameBlack
                ),
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .weight(0.2f, fill = false)
                .size(40.dp)
                .align(Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(GameBlack.copy(alpha = 0.3f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.5.dp, GameBlack, CircleShape)
                    .clip(CircleShape)
                    .background(MainYellow, CircleShape) // Yellow for exit
                    .clickable { viewModel.exitGame(navigateToHome) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close),
                    contentDescription = "Exit Game",
                    tint = GameBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun WinnersPodiumNew(topPlayers: List<LeaderboardEntry>) {
    val paddedList = if (topPlayers.size < 3) {
        topPlayers + List(3 - topPlayers.size) {
            LeaderboardEntry(
                participant = ParticipantUiModel("", "", 1, false, 0),
                points = 0,
                rank = it + topPlayers.size + 1
            )
        }
    } else {
        topPlayers
    }

    val podiumOrder = listOf(paddedList.getOrNull(1), paddedList.getOrNull(0), paddedList.getOrNull(2))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        podiumOrder.forEachIndexed { index, entry ->
            if (entry != null) {
                val isFirst = index == 1
                val isSecond = index == 0

                PodiumAvatarItem(
                    entry = entry,
                    isFirst = isFirst,
                    isSecond = isSecond,
                    showEmpty = entry.participant.id.isEmpty()
                )
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PodiumAvatarItem(
    entry: LeaderboardEntry,
    isFirst: Boolean,
    isSecond: Boolean,
    showEmpty: Boolean
) {
    if (showEmpty) {
        Spacer(modifier = Modifier.width(80.dp))
        return
    }

    val size = if (isFirst) 110.dp else 85.dp
    // Using Game Colors for borders
    val borderColor = GameBlack
    val rankBgColor = if (isFirst) MainYellow else if (isSecond) Color(0xFFC0C0C0) else Color(0xFFCD7F32)
    val rankText = if (isFirst) "1" else if (isSecond) "2" else "3"

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        // Trophies with better spacing
        Box(
            modifier = Modifier.height(40.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isFirst) {
                Image(
                    painter = painterResource(Res.drawable.gold_trophy),
                    contentDescription = "Gold",
                    modifier = Modifier.size(40.dp)
                )
            } else if (isSecond) {
                Image(
                    painter = painterResource(Res.drawable.silver_trophy),
                    contentDescription = "Silver",
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Image(
                    painter = painterResource(Res.drawable.bronze_trophy),
                    contentDescription = "Bronze",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Avatar Box
        Box(contentAlignment = Alignment.BottomCenter) {
            // Avatar Image with thick black border
            Image(
                painter = painterResource(getAvatarResource(entry.participant.avatarId)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, borderColor, CircleShape)
            )

            // Rank Badge (Circle style)
            Box(
                modifier = Modifier
                    .offset(y = 10.dp)
                    .size(28.dp)
                    .background(rankBgColor, CircleShape)
                    .border(1.5.dp, GameBlack, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rankText,
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = GameBlack
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Name
        Text(
            text = entry.participant.name,
            style = TextStyle(
                fontFamily = patrickHandFont(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = GameBlack,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        // Score
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GameBlack)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${entry.points} PTS",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MainYellow
            )
        }
    }
}


@OptIn(ExperimentalResourceApi::class)
@Composable
fun LeaderboardRowNew(entry: LeaderboardEntry) {
    // Card Style Row
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, GameBlack, RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number
            Text(
                text = "#${entry.rank}",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Gray,
                modifier = Modifier.width(36.dp)
            )

            // Avatar
            Image(
                painter = painterResource(getAvatarResource(entry.participant.avatarId)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, GameBlack, CircleShape)
                    .background(GameGrey)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Name
            Text(
                text = entry.participant.name,
                style = TextStyle(
                    fontFamily = patrickHandFont(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = GameBlack,
                modifier = Modifier.weight(1f)
            )

            // Score
            Text(
                text = "${entry.points} pts",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = GameBlack
            )
        }
    }
}

// Helper to get resources
@OptIn(ExperimentalResourceApi::class)
fun getAvatarResource(id: Int): DrawableResource {
    return when (id) {
        1 -> Res.drawable.profile_picture
        2 -> Res.drawable.profile_picture_2
        3 -> Res.drawable.profile_picture_3
        4 -> Res.drawable.profile_picture_4
        5 -> Res.drawable.profile_picture_5
        6 -> Res.drawable.profile_picture_6
        7 -> Res.drawable.profile_picture_7
        8 -> Res.drawable.profile_picture_8
        else -> Res.drawable.profile_picture
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = GameBlack,
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp
        )
        Text(
            text = "CALCULATING...",
            style = TextStyle(
                fontFamily = testSohneFont(),
                fontSize = 14.sp
            )
        )
    }
}

@Composable
fun ErrorMessage(error: String) {
    // Check if it's a user-friendly error or needs conversion
    val isUserFriendlyError = error.contains("No active game") || 
                             error.contains("Failed to load") ||
                             error.contains("Invalid game") ||
                             error.contains("connection") ||
                             error.contains("not found")
                             
    // Format the error appropriately
    val displayError = if (isUserFriendlyError) {
        error // Use as-is for user-friendly errors
    } else {
        // Convert technical errors to user-friendly messages
        "Something went wrong loading the results."
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "OOPS!",
            style = TextStyle(
                fontFamily = testSohneFont(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            ),
            color = GameBlack
        )
        Text(
            text = displayError,
            style = TextStyle(
                fontFamily = patrickHandFont(),
                fontSize = 18.sp
            ),
            color = GameBlack.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}