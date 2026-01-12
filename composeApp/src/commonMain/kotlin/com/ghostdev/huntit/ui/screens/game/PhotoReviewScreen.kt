package com.ghostdev.huntit.ui.screens.game

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
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
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.ui.screens.game.GameViewModel
import com.ghostdev.huntit.utils.LocalAudioPlayer
import org.koin.compose.koinInject
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.reset
import org.jetbrains.compose.resources.painterResource
import com.ghostdev.huntit.data.model.SubmissionState

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@Composable
fun PhotoReviewScreen(
    innerPadding: PaddingValues,
    viewModel: SubmissionViewModel,
    imageUrl: String? = null,
    navigateBack: () -> Unit,
    navigateToGame: () -> Unit,
    navigateToWinners: () -> Unit = {}
) {
    // Add GameViewModel to monitor game state changes
    val gameViewModel: GameViewModel = koinInject()
    val gameState by gameViewModel.state.collectAsState()
    
    // Flag to prevent automatic navigation back after button clicks
    val preventFurtherNavigation = remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // Use a remembered mutable state for review data that can be updated
    val reviewDataState = remember { androidx.compose.runtime.mutableStateOf(viewModel.getReviewData()) }
    // For UI rendering, get the current value
    val reviewData = reviewDataState.value
    
    // Get the audio player to play sound effects
    val audioPlayer = LocalAudioPlayer.current
    
    println("PhotoReviewScreen loaded with review data: $reviewData")
    
    // Play the appropriate sound effect when the review data is available
    LaunchedEffect(reviewData) {
        if (reviewData != null) {
            // Play success or failure sound based on the submission result
            if (reviewData.isSuccess) {
                audioPlayer?.playSound("files/success.mp3")
            } else {
                audioPlayer?.playSound("files/failed.mp3")
            }
        }
    }

    // Modified approach to wait for review data without auto-navigating back
    LaunchedEffect(Unit) {
        // Only attempt to get data if review data is null and navigation isn't prevented
        if (reviewData == null && !preventFurtherNavigation.value) {
            println("No initial review data available, will keep checking in background")
            
            // Use a counter for logging purposes only
            var attempt = 0
            
            // Continue checking until we find data or the screen is disposed
            while (!preventFurtherNavigation.value) {
                attempt++
                
                // Use an increasing delay pattern with a maximum delay of 1 second
                val delayMs = (300L * attempt).coerceAtMost(1000L)
                println("Attempt $attempt: Waiting ${delayMs}ms for review data...")
                kotlinx.coroutines.delay(delayMs)
                
                // Check again after delay
                val latestReviewData = viewModel.getReviewData()
                
                // Update our state with the latest data
                reviewDataState.value = latestReviewData
                
                if (latestReviewData != null) {
                    println("Review data appeared on attempt $attempt, staying on review screen")
                    break // Exit the loop, we have data
                }
                
                // If we've been prevented from navigation during our checks, stop trying
                if (preventFurtherNavigation.value) {
                    println("Navigation prevention flag was set, staying on review screen")
                    break
                }
                
                // Add a large number of attempts check as a safety valve
                // but NEVER automatically navigate back to game screen
                if (attempt >= 20) {
                    println("Made 20 attempts to get review data, still waiting...")
                    // Just continue waiting instead of navigating back
                }
            }
        }
    }
    
    // Handle navigation to winners screen when game is over
    LaunchedEffect(gameState.shouldNavigateToWinners) {
        if (gameState.shouldNavigateToWinners) {
            println("Game over detected while in PhotoReviewScreen - navigating to winners")
            gameViewModel.onWinnersNavigationHandled()
            navigateToWinners()
        }
    }

    // Use DisposableEffect to handle screen lifecycle
    DisposableEffect(Unit) {
        // Mark screen as active to ensure navigation works correctly
        val isFirstAppearance = true
        
        onDispose {
            println("PhotoReviewScreen is being disposed - this is normal during navigation")
            // Do not clear the cache here - we manage it explicitly during navigation
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Use AnimatedBackground as in the design
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            // Make sure the content is centered vertically and has proper padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Only show content if we have review data
                // Get the current submission state to check for errors
                val submissionState = viewModel.getReviewState()
                
                if (submissionState is SubmissionState.Error) {
                    // Handle unexpected errors with the generic error card
                    GenericErrorCard(
                        errorMessage = "Something went wrong. Please try again later.",
                        onReturnToGame = {
                            // Prevent automatic navigation back
                            preventFurtherNavigation.value = true
                            println("Preventing further navigation after error Return To Game click")
                            
                            // Clear data and return to game
                            val dataCleanupScope = kotlinx.coroutines.MainScope()
                            // First navigate
                            navigateToGame()
                            // Use a delay before clearing data to ensure navigation completes
                            dataCleanupScope.launch {
                                try {
                                    // Wait for navigation to complete
                                    kotlinx.coroutines.delay(500)
                                    viewModel.clearReviewData()
                                    println("Cleared review data after error")
                                } catch (e: Exception) {
                                    println("Error during cleanup: ${e.message}")
                                }
                            }
                        },
                        onRetry = if (submissionState.canRetry) {
                            {
                                // Prevent automatic navigation back
                                preventFurtherNavigation.value = true
                                println("Preventing further navigation after error Try Again click")
                                
                                viewModel.clearReviewData()
                                navigateBack()
                            }
                        } else null
                    )
                } else if (reviewDataState.value != null) {
                    // Use the latest data from our state
                    val reviewData = reviewDataState.value
                    // Check if we have a round ended error
                    if (viewModel.isRoundEndedError) {
                        // For round ended error, show a simplified view with only return to game option
                        RoundEndedErrorCard(
                            challenge = reviewData!!.challenge,
                            onReturnToGame = {
                                // Prevent automatic navigation back after button click
                                preventFurtherNavigation.value = true
                                println("Preventing further navigation after button click")
                                
                                // Clear data and return to game
                                val dataCleanupScope = kotlinx.coroutines.MainScope()
                                // First navigate
                                navigateToGame()
                                // Use a delay before clearing data to ensure navigation completes
                                dataCleanupScope.launch {
                                    try {
                                        // Wait for navigation to complete
                                        kotlinx.coroutines.delay(500)
                                        viewModel.clearReviewData()
                                        println("Cleared review data after round ended error")
                                    } catch (e: Exception) {
                                        println("Error during cleanup: ${e.message}")
                                    }
                                }
                            }
                        )
                    } else {
                        // Normal review card for other scenarios
                        ReviewCard(
                            reviewData = reviewData!!,
                            onTryAgain = {
                                // Prevent automatic navigation back
                                preventFurtherNavigation.value = true
                                println("Preventing further navigation after Try Again click")
                                
                                viewModel.clearReviewData()
                                navigateBack()
                            },
                            onSkipRound = {
                                // Prevent automatic navigation back
                                preventFurtherNavigation.value = true
                                println("Preventing further navigation after Skip Round click")
                                
                                // First skip the round
                                viewModel.skipRound()
                                // Create a scope for cleanup that won't be canceled by navigation
                                val cleanupScope = kotlinx.coroutines.MainScope()
                                // Then navigate
                                navigateToGame()
                                // Clear data after navigation with a longer delay
                                cleanupScope.launch {
                                    try {
                                        // Longer delay to ensure navigation completes first
                                        kotlinx.coroutines.delay(500)
                                        viewModel.clearReviewData()
                                        println("Cleared review data after skip round")
                                    } catch (e: Exception) {
                                        println("Error during cleanup after skip: ${e.message}")
                                    }
                                }
                            },
                            onContinue = {
                                // Prevent automatic navigation back
                                preventFurtherNavigation.value = true
                                println("Preventing further navigation after Continue click")
                                
                                // Create scope for cleanup that won't be canceled by navigation
                                val dataCleanupScope = kotlinx.coroutines.MainScope()
                                // First navigate
                                navigateToGame()
                                // Use a longer delay before clearing data to ensure navigation completes
                                dataCleanupScope.launch {
                                    try {
                                        // Wait for navigation to complete
                                        kotlinx.coroutines.delay(500)
                                        viewModel.clearReviewData()
                                        println("Cleared review data after navigation")
                                    } catch (e: Exception) {
                                        println("Error during cleanup: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                } else {
                    // No review data, show loading with app's style
                    // Add a debounced loading state to prevent flickering
                    val showLoading = remember { androidx.compose.runtime.mutableStateOf(false) }
                    
                    // Only show loading indicator after a short delay to prevent flickering
                    LaunchedEffect(Unit) {
                        // Delay showing the loading indicator to prevent flickering
                        kotlinx.coroutines.delay(500)
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
                    } else {
                        // Show nothing while waiting for the loading indicator to appear
                        // This helps prevent flickering on fast loads
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
    // 3D Card effect with shadow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .padding(bottom = GameShadowHeight)
    ) {
        // Card content
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
        // Success icon with app-themed colors
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

        // Title text with app's font
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

        // Description with app's style
        Text(
            text = "Your photo matches the challenge perfectly",
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Challenge requirements box with consistent styling
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

        // Points indicator with app's style
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

        // Continue button with app's 3D style
        GamifiedButton(
            text = "CONTINUE TO NEXT ROUND",
            bgColor = MainYellow,
            onClick = onContinue
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom message
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
        // Error icon with app-themed colors
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

        // Title text with app's font
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

        // Description
        Text(
            text = "Your photo doesn't match the challenge",
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )

        // Display the specific reason for rejection
        Text(
            text = reason,
            fontFamily = patrickHandFont(),
            fontSize = 16.sp,
            color = MainRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Challenge requirements box with consistent styling
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

        // Tips box with app's consistent styling
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
                    // Light bulb icon with app's style
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

        // Try Again button with app's 3D style
        GamifiedButton(
            text = "TAKE ANOTHER PHOTO",
            bgColor = MainYellow,
            onClick = onTryAgain,
            iconPainter = painterResource(Res.drawable.reset)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Skip button with app's style
        GamifiedButton(
            text = "SKIP THIS ROUND",
            bgColor = GameGrey,
            onClick = onSkipRound
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom message
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

    // Animate the vertical offset for the physical "push down" effect
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f), // Bouncy spring for tactile feel
        label = "ButtonOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // Total height reserved including shadow space
            .clickable(
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
                .height(52.dp) // Match button height
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Button Layer (Moves when pressed)
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

/**
 * Special card to display when submission was attempted after the round ended
 */
@Composable
private fun RoundEndedErrorCard(
    challenge: String,
    onReturnToGame: () -> Unit
) {
    // 3D Card effect with shadow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .padding(bottom = GameShadowHeight)
    ) {
        // Card content
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
                // Round ended icon with app-themed colors
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

                // Title text with app's font
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

                // Description with app's style
                Text(
                    text = "You were too late! The round ended before your submission was processed.",
                    fontFamily = patrickHandFont(),
                    fontSize = 16.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Challenge that was attempted box
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

                // Return to Game button with app's 3D style
                GamifiedButton(
                    text = "RETURN TO GAME",
                    bgColor = MainYellow,
                    onClick = onReturnToGame
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom message
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

/**
 * Generic error card to display for unexpected errors
 */
@Composable
private fun GenericErrorCard(
    errorMessage: String = "Something went wrong",
    onReturnToGame: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    // 3D Card effect with shadow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .padding(bottom = GameShadowHeight)
    ) {
        // Card content
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
                // Error icon with app-themed colors
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

                // Title text with app's font
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

                // Generic user-friendly error message
                Text(
                    text = errorMessage,
                    fontFamily = patrickHandFont(),
                    fontSize = 18.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // If retry option is available, show retry button
                if (onRetry != null) {
                    GamifiedButton(
                        text = "TRY AGAIN",
                        bgColor = MainYellow,
                        onClick = onRetry
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Return to Game button
                GamifiedButton(
                    text = "RETURN TO GAME",
                    bgColor = if (onRetry == null) MainYellow else GameGrey,
                    onClick = onReturnToGame
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom message
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