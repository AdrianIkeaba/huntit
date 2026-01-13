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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.ghostdev.huntit.data.model.SubmissionState
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.reset
import huntit.composeapp.generated.resources.close
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
fun PhotoReviewScreen(
    innerPadding: PaddingValues,
    viewModel: SubmissionViewModel,
    navigateBack: () -> Unit,
    navigateToGame: () -> Unit,
    navigateToWinners: () -> Unit = {}
) {
    val wrappedNavigateToGame: () -> Unit = { navigateToGame() }
    val wrappedNavigateBack: () -> Unit = { navigateBack() }
    
    val gameViewModel: GameViewModel = koinInject()
    val gameState by gameViewModel.state.collectAsState()
    
    val preventFurtherNavigation = remember { androidx.compose.runtime.mutableStateOf(false) }
    val reviewDataState = remember { androidx.compose.runtime.mutableStateOf(viewModel.getReviewData()) }
    val reviewData = reviewDataState.value 
                    
    if (reviewData != null) {
        preventFurtherNavigation.value = true
    }
    
    val audioPlayer = LocalAudioPlayer.current
    val expectedDisposal = remember { androidx.compose.runtime.mutableStateOf(false) }
    
    LaunchedEffect(reviewData) {
        if (reviewData != null) {
            if (reviewData.isSuccess) {
                audioPlayer?.playSound("files/success.mp3")
            } else {
                audioPlayer?.playSound("files/failed.mp3")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (reviewData == null) {
            val latestReviewData = viewModel.getReviewData()
            if (latestReviewData != null) {
                reviewDataState.value = latestReviewData
            }
        }
    }

    val phaseEndsAtMs = viewModel.getCachedPhaseEndsAtMs()
    
    LaunchedEffect(gameState.timeRemainingMs) {
        val currentTimeMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val isPhaseActuallyEnded = phaseEndsAtMs in 1..currentTimeMs
        val isTimerAtZero = gameState.timeRemainingMs <= 0
        
        if ((isTimerAtZero && isPhaseActuallyEnded) && !preventFurtherNavigation.value) {
            // Add a longer delay to avoid immediate transition
            delay(6500)
            wrappedNavigateToGame()
        }
    }
    
    // KEEP: #2 - Game finishing navigation
    LaunchedEffect(gameState.shouldNavigateToWinners) {
        if (gameState.shouldNavigateToWinners) {
            expectedDisposal.value = true
            gameViewModel.onWinnersNavigationHandled()
            navigateToWinners()
        }
    }

    DisposableEffect(Unit) {
        expectedDisposal.value = false
        
        onDispose {
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(elevation = 4.dp, shape = CircleShape)
                            .background(GameWhite, CircleShape)
                            .border(1.dp, GameBlack, CircleShape)
                            .clickable {
                                preventFurtherNavigation.value = true
                                expectedDisposal.value = true
                                
                                val dataCleanupScope = MainScope()
                                navigateToGame()
                                
                                dataCleanupScope.launch {
                                    try {
                                        delay(500)
                                        viewModel.clearReviewData()
                                    } catch (e: Exception) {
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = "Close",
                            tint = GameBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                val submissionState = viewModel.getReviewState()
                
                if (submissionState is SubmissionState.Error) {
                    GenericErrorCard(
                        errorMessage = "Something went wrong. Please try again later.",
                        onReturnToGame = {
                            preventFurtherNavigation.value = true
                            expectedDisposal.value = true
                            
                            val dataCleanupScope = kotlinx.coroutines.MainScope()
                            wrappedNavigateToGame()
                            
                            dataCleanupScope.launch {
                                try {
                                    kotlinx.coroutines.delay(500)
                                    viewModel.clearReviewData()
                                } catch (e: Exception) {
                                }
                            }
                        },
                        onRetry = if (submissionState.canRetry) {
                            {
                                preventFurtherNavigation.value = true
                                expectedDisposal.value = true
                                
                                viewModel.clearReviewData()
                                wrappedNavigateBack()
                            }
                        } else null
                    )
                } else if (reviewDataState.value != null) {
                    val reviewData = reviewDataState.value
                    if (viewModel.isRoundEndedError) {
                        RoundEndedErrorCard(
                            challenge = reviewData!!.challenge,
                            onReturnToGame = {
                                preventFurtherNavigation.value = true
                                expectedDisposal.value = true
                                
                                val dataCleanupScope = kotlinx.coroutines.MainScope()
                                wrappedNavigateToGame()
                                dataCleanupScope.launch {
                                    try {
                                        kotlinx.coroutines.delay(500)
                                        viewModel.clearReviewData()
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                        )
                    } else {
                        ReviewCard(
                            reviewData = reviewData!!,
                            onTryAgain = {
                                preventFurtherNavigation.value = true
                                expectedDisposal.value = true
                                
                                viewModel.clearReviewData()
                                wrappedNavigateBack()
                            },
                            onSkipRound = {
                                preventFurtherNavigation.value = true
                                expectedDisposal.value = true
                                
                                viewModel.skipRound()
                                val cleanupScope = kotlinx.coroutines.MainScope()
                                wrappedNavigateToGame()
                                cleanupScope.launch {
                                    try {
                                        kotlinx.coroutines.delay(500)
                                        viewModel.clearReviewData()
                                    } catch (e: Exception) {
                                    }
                                }
                            },
                            onContinue = {
                                preventFurtherNavigation.value = true
                                expectedDisposal.value = true
                                
                                val dataCleanupScope = MainScope()
                                wrappedNavigateToGame()
                                dataCleanupScope.launch {
                                    try {
                                        delay(500)
                                        viewModel.clearReviewData()
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                        )
                    }
                } else {
                    val showLoading = remember { androidx.compose.runtime.mutableStateOf(false) }
                    
                    LaunchedEffect(Unit) {
                        delay(500)
                        if (reviewDataState.value == null) {
                            showLoading.value = true
                        }
                    }
                    
                    if (showLoading.value) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MainYellow,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            
                            Text(
                                text = "LOADING RESULTS",
                                style = TextStyle(
                                    fontFamily = testSohneFont(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center,
                                    color = GameBlack
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    reviewData: SubmissionViewModel.ReviewData,
    onTryAgain: () -> Unit,
    onSkipRound: () -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .padding(bottom = GameShadowHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = GameWhite,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 2.dp,
                    color = GameBlack,
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            if (reviewData.isSuccess) {
                SuccessContent(
                    challenge = reviewData.challenge,
                    points = reviewData.points,
                    onContinue = onContinue
                )
            } else {
                FailureContent(
                    challenge = reviewData.challenge,
                    reason = reviewData.reason,
                    onTryAgain = onTryAgain,
                    onSkipRound = onSkipRound
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    challenge: String,
    points: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MainGreen, CircleShape)
                .border(2.dp, GameBlack, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = GameWhite,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "PERFECT MATCH!",
            style = TextStyle(
                fontFamily = testSohneFont(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = GameBlack,
                textAlign = TextAlign.Center
            )
        )

        Text(
            text = "Your photo matches the challenge perfectly",
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFDEF9E5),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = MainGreen,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CHALLENGE REQUIREMENTS",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MainGreen.copy(alpha = 0.8f)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "\"$challenge\"",
                    fontFamily = patrickHandFont(),
                    fontSize = 16.sp,
                    color = MainGreen.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .background(
                    color = Color(0xFFDEF9E5),
                    shape = RoundedCornerShape(32.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = MainGreen,
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🏆",
                    fontSize = 18.sp
                )
                
                Text(
                    text = "+$points POINTS",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MainGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GamifiedButton(
            text = "CONTINUE TO NEXT ROUND",
            bgColor = MainYellow,
            onClick = onContinue
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Great work! Keep hunting to increase your points.",
            fontFamily = patrickHandFont(),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FailureContent(
    challenge: String,
    reason: String,
    onTryAgain: () -> Unit,
    onSkipRound: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MainRed, CircleShape)
                .border(2.dp, GameBlack, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = GameWhite,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "NOT QUITE RIGHT",
            style = TextStyle(
                fontFamily = testSohneFont(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = GameBlack,
                textAlign = TextAlign.Center
            )
        )

        Text(
            text = "Your photo doesn't match the challenge",
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )

        Text(
            text = reason,
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = MainRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFFDE9E9),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = MainRed,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CHALLENGE REQUIREMENTS",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MainRed.copy(alpha = 0.8f)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "\"$challenge\"",
                    fontFamily = patrickHandFont(),
                    fontSize = 16.sp,
                    color = MainRed.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = Color(0xFF64B5F6),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF2196F3), CircleShape)
                            .border(1.dp, GameBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "TIPS FOR SUCCESS",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF1976D2)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipItem(text = "Ensure your subject is well-lit and in focus")
                    TipItem(text = "Fill the frame with the main subject")
                    TipItem(text = "Match the exact description in the challenge")
                    TipItem(text = "Avoid blurry or unclear images")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GamifiedButton(
            text = "TAKE ANOTHER PHOTO",
            bgColor = MainYellow,
            onClick = onTryAgain,
            iconPainter = painterResource(Res.drawable.reset)
        )

        Spacer(modifier = Modifier.height(8.dp))

        GamifiedButton(
            text = "SKIP THIS ROUND",
            bgColor = GameGrey,
            onClick = onSkipRound
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Don't worry! You can try again or skip this challenge",
            fontFamily = patrickHandFont(),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TipItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = Color(0xFF1976D2),
        )

        Text(
            text = text,
            fontFamily = patrickHandFont(),
            fontSize = 14.sp,
            color = Color(0xFF1976D2)
        )
    }
}

@Composable
private fun GamifiedButton(
    text: String,
    bgColor: Color,
    onClick: () -> Unit,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(52.dp)
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(52.dp)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = GameBlack,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = GameBlack
                )
            }
        }
    }
}

@Composable
private fun RoundEndedErrorCard(
    challenge: String,
    onReturnToGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .padding(bottom = GameShadowHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = GameWhite,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 2.dp,
                    color = GameBlack,
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MainRed, CircleShape)
                        .border(2.dp, GameBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏱️",
                        fontSize = 36.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "ROUND ALREADY ENDED",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = GameBlack,
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = "You were too late! The round ended before your submission was processed.",
                    fontFamily = patrickHandFont(),
                    fontSize = 16.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFFDE9E9),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = MainRed,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CHALLENGE EXPIRED",
                            style = TextStyle(
                                fontFamily = testSohneFont(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MainRed.copy(alpha = 0.8f)
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "\"$challenge\"",
                            fontFamily = patrickHandFont(),
                            fontSize = 16.sp,
                            color = MainRed.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GamifiedButton(
                    text = "RETURN TO GAME",
                    bgColor = MainYellow,
                    onClick = onReturnToGame
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Get ready for the next round!",
                    fontFamily = patrickHandFont(),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GenericErrorCard(
    errorMessage: String = "Something went wrong",
    onReturnToGame: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .padding(bottom = GameShadowHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = GameWhite,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 2.dp,
                    color = GameBlack,
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MainRed, CircleShape)
                        .border(2.dp, GameBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        color = GameWhite,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "OOPS!",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = GameBlack,
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = errorMessage,
                    fontFamily = patrickHandFont(),
                    fontSize = 18.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (onRetry != null) {
                    GamifiedButton(
                        text = "TRY AGAIN",
                        bgColor = MainYellow,
                        onClick = onRetry
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }

                GamifiedButton(
                    text = "RETURN TO GAME",
                    bgColor = if (onRetry == null) MainYellow else GameGrey,
                    onClick = onReturnToGame
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (onRetry != null)
                        "You can try again or return to the game"
                    else
                        "Let's get back to the hunt!",
                    fontFamily = patrickHandFont(),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}