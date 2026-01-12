package com.ghostdev.huntit.ui.screens.history

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.back
import huntit.composeapp.generated.resources.history
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

data class GameHistory(
    val id: String,
    val roomCode: String,
    val date: String,
    val score: Int,
    val position: Int,
    val totalPlayers: Int,
    val items: List<String> // Items found
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PastGamesScreen(
    innerPadding: PaddingValues,
    navigateBack: () -> Unit,
    viewModel: PastGamesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            val isUserFriendlyError = it.contains("No past games found") ||
                                     it.contains("Connection error") ||
                                     it.contains("Please sign in")

            val displayError = if (isUserFriendlyError) {
                it
            } else {
                it.toUserFriendlyError("Something went wrong while loading your game history.")
            }

            snackbarHostState.showSnackbar(if (isUserFriendlyError) displayError else "Error: $displayError")
            viewModel.clearErrorMessage()
        }
    }

    BackHandler {
        if (uiState.selectedGameId != null) {
            viewModel.clearSelectedGame()
        } else {
            navigateBack()
        }
    }


    PastGamesComponent(
        innerPadding = innerPadding,
        games = uiState.games,
        isLoading = uiState.isLoading,
        selectedGameId = uiState.selectedGameId,
        leaderboard = uiState.leaderboard,
        challenges = uiState.challenges,
        isLoadingLeaderboard = uiState.isLoadingLeaderboard,
        onBackClick = navigateBack,
        onBackToGamesList = viewModel::clearSelectedGame,
        onGameClick = viewModel::loadLeaderboard,
        onRefresh = viewModel::refreshGameHistory,
        snackbarHostState = snackbarHostState
    )
}

@Composable
@Preview
private fun PastGamesComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    games: List<GameHistory> = emptyList(),
    isLoading: Boolean = false,
    selectedGameId: String? = null,
    leaderboard: List<LeaderboardEntry> = emptyList(),
    challenges: List<RoundChallenge> = emptyList(),
    isLoadingLeaderboard: Boolean = false,
    onBackClick: () -> Unit = {},
    onBackToGamesList: () -> Unit = {},
    onGameClick: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            if (selectedGameId != null) {
                // Show leaderboard for selected game
                LeaderboardScreen(
                    innerPadding = innerPadding,
                    leaderboard = leaderboard,
                    challenges = challenges,
                    isLoading = isLoadingLeaderboard,
                    onBackClick = onBackToGamesList
                )
            } else {
                // Show past games list
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with Back Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button with 3D effect
                        BackButton(onClick = onBackClick)

                        // Title
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "YOUR PAST GAMES",
                            style = TextStyle(
                                fontFamily = testSohneFont(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = GameBlack,
                            textAlign = TextAlign.Center
                        )

                        // Empty spacer to balance the back button
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    if (isLoading) {
                        // Loading state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        }
                    } else if (games.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Empty state icon
                                Image(
                                    painter = painterResource(Res.drawable.history),
                                    contentDescription = "History",
                                    modifier = Modifier.size(64.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "No games yet!",
                                    style = TextStyle(
                                        fontFamily = testSohneFont(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = GameBlack
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Start hunting to see your game history here",
                                    style = TextStyle(
                                        fontFamily = patrickHandFont(),
                                        fontSize = 16.sp
                                    ),
                                    color = GameBlack.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Game history list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            items(games) { game ->
                                GameHistoryItem(
                                    game = game,
                                    onClick = { onGameClick(game.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun LeaderboardScreen(
    innerPadding: PaddingValues,
    leaderboard: List<LeaderboardEntry>,
    challenges: List<RoundChallenge> = emptyList(),
    isLoading: Boolean,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button with 3D effect
            BackButton(onClick = onBackClick)

            // Title
            Text(
                modifier = Modifier.weight(1f),
                text = "LEADERBOARD",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = GameBlack,
                textAlign = TextAlign.Center
            )

            // Empty spacer to balance the back button
            Spacer(modifier = Modifier.size(40.dp))
        }

        if (isLoading) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 3.dp
                )
            }
        } else if (leaderboard.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Empty state icon
                    Image(
                        painter = painterResource(Res.drawable.history),
                        contentDescription = "No Leaderboard",
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No leaderboard data",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack
                    )
                }
            }
        } else {
            // Leaderboard and challenges
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Challenges Section
                if (challenges.isNotEmpty()) {
                    item {
                        ChallengesSection(challenges = challenges)
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Leaderboard Entries
                items(leaderboard) { entry ->
                    LeaderboardEntryItem(entry = entry)
                }
            }
        }
    }
}

@Composable
fun ChallengesSection(challenges: List<RoundChallenge>) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp)) {
        // Content layer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameWhite, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "ROUND CHALLENGES",
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = GameBlack
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // List of round challenges
            challenges.forEach { challenge ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Round number badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MainYellow, CircleShape)
                            .border(1.dp, GameBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${challenge.roundNumber}",
                            style = TextStyle(
                                fontFamily = testSohneFont(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = GameBlack
                        )
                    }
                    
                    Spacer(modifier = Modifier.size(width = 12.dp, height = 0.dp))
                    
                    // Challenge text
                    Text(
                        text = challenge.challengeText,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 14.sp
                        ),
                        color = GameBlack
                    )
                }
            }
        }
    }
}

@Composable
fun LeaderboardEntryItem(entry: LeaderboardEntry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        // Content Layer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameWhite, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Position and Avatar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Position badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            when (entry.position) {
                                1 -> MainYellow
                                2 -> Color(0xFFD0D0D0) // Silver
                                3 -> Color(0xFFCD7F32) // Bronze
                                else -> GameGrey
                            },
                            CircleShape
                        )
                        .border(1.5.dp, GameBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${entry.position}",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack
                    )
                }

                // Avatar with player name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier.size(40.dp)
                    ) {
                        // Shadow Layer (bottom)
                        Image(
                            modifier = Modifier
                                .size(40.dp)
                                .offset(x = 2.dp, y = 2.dp)
                                .background(GameBlack, CircleShape),
                            painter = painterResource(
                                HomeViewModel.getProfilePictureById(
                                    entry.avatarId
                                )
                            ),
                            contentDescription = null,
                            alpha = 0.3f
                        )

                        // Main Layer (top)
                        Image(
                            modifier = Modifier
                                .size(40.dp)
                                .border(1.5.dp, GameBlack, CircleShape)
                                .background(Color.White, CircleShape),
                            painter = painterResource(
                                HomeViewModel.getProfilePictureById(
                                    entry.avatarId
                                )
                            ),
                            contentDescription = "Player Avatar"
                        )
                    }

                    // Player name
                    Text(
                        text = entry.displayName,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack
                    )
                }
            }

            // Right side: Score
            Box(
                modifier = Modifier
                    .background(
                        GameGrey,
                        RoundedCornerShape(8.dp)
                    )
                    .border(1.5.dp, GameBlack, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Score: ${entry.score}",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = GameBlack
                )
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Shadow Layer (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(36.dp)
                .background(GameBlack, RoundedCornerShape(8.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .size(36.dp)
                .background(GameGrey, RoundedCornerShape(8.dp))
                .border(1.5.dp, GameBlack, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Uncomment when arrow_back resource is available
             Image(
                 painter = painterResource(Res.drawable.back),
                 contentDescription = "Back",
                 modifier = Modifier.size(20.dp)
             )

        }
    }
}

@Composable
fun GameHistoryItem(game: GameHistory, onClick: () -> Unit) {
    // 'expanded' variable removed as we're now using single-click behavior

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Shadow Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 4.dp)
                .background(GameBlack, RoundedCornerShape(16.dp))
                .height(130.dp) // Fixed height for all items
        )

        // Content Layer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameWhite, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp))
                .clickable {
            // Call onClick directly to navigate to leaderboard with a single click
            onClick()
        }
                .padding(16.dp)
        ) {
            // Game basic info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Date and Room code
                Column {
                    Text(
                        text = game.date,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack
                    )

                    Text(
                        text = "Room: ${game.roomCode}",
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 12.sp
                        ),
                        color = GameBlack.copy(alpha = 0.6f)
                    )
                }

                // Right side: Position and Score
                Box(
                    modifier = Modifier
                        .background(
                            when (game.position) {
                                1 -> MainYellow
                                2 -> Color(0xFFD0D0D0) // Silver
                                3 -> Color(0xFFCD7F32) // Bronze
                                else -> GameGrey
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.5.dp, GameBlack, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when (game.position) {
                            1 -> "1st Place"
                            2 -> "2nd Place"
                            3 -> "3rd Place"
                            else -> "${game.position}th Place"
                        },
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Score and Players count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Score: ${game.score}",
                    style = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 14.sp
                    ),
                    color = GameBlack
                )

                Text(
                    text = "${game.totalPlayers} Players",
                    style = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 14.sp
                    ),
                    color = GameBlack
                )
            }

            // Items found and Call to Action - always visible now
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // Items found
                    if (game.items.isNotEmpty()) {
                        Text(
                            text = "Items Found:",
                            style = TextStyle(
                                fontFamily = patrickHandFont(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = GameBlack
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = game.items.joinToString(", "),
                            style = TextStyle(
                                fontFamily = patrickHandFont(),
                                fontSize = 14.sp
                            ),
                            color = GameBlack.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Call to action for leaderboard
                    Text(
                        text = "Tap to view leaderboard →",
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack.copy(alpha = 0.8f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}