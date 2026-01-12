package com.ghostdev.huntit.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.onboarding_compete
import huntit.composeapp.generated.resources.onboarding_hunt
import huntit.composeapp.generated.resources.onboarding_snap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val GameBlack = Color(0xFF1A1A1A)
private val GameGrey = Color(0xFFE5E5E5)

data class OnboardingPage(
    val title: String,
    val description: String,
    val imageRes: DrawableResource
)

@Composable
fun OnboardingScreen(
    navigateToSignIn: () -> Unit,
    viewModel: OnboardingViewModel = org.koin.compose.viewmodel.koinViewModel()
) {
    val pages = listOf(
        OnboardingPage(
            title = "Join the Hunt!",
            description = "Explore the real world and find hidden objects around you.",
            imageRes = Res.drawable.onboarding_hunt
        ),
        OnboardingPage(
            title = "Snap to Score",
            description = "Found an item? Take a picture with the app to claim your points!",
            imageRes = Res.drawable.onboarding_snap
        ),
        OnboardingPage(
            title = "Challenge Friends",
            description = "Create rooms and compete in real-time to see who is the ultimate hunter.",
            imageRes = Res.drawable.onboarding_compete
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    AnimatedBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                val audioPlayer = LocalAudioPlayer.current
                val onSkipClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    viewModel.onOnboardingCompleted()
                    navigateToSignIn()
                }
                TextButton(onClick = onSkipClick) {
                    Text(
                        text = "SKIP",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = GameBlack.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageIndicators(
                    pagerState = pagerState,
                    pageCount = pages.size
                )

                Spacer(modifier = Modifier.height(24.dp))

                val isLastPage = pagerState.currentPage == pages.lastIndex
                val buttonText = if (isLastPage) "GET STARTED" else "CONTINUE"

                GamifiedButton(
                    text = buttonText,
                    isLoading = false,
                    onClick = {
                        if (isLastPage) {
                            viewModel.onOnboardingCompleted()
                            navigateToSignIn()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image Container with 3D effect
        Box(
            modifier = Modifier.size(280.dp)
        ) {
            Image(
                painter = painterResource(page.imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = page.title,
            style = TextStyle(
                fontFamily = testSohneFont(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = GameBlack,
                lineHeight = 36.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = TextStyle(
                fontFamily = patrickHandFont(),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = GameBlack.copy(alpha = 0.7f),
                lineHeight = 24.sp
            )
        )
    }
}

@Composable
fun PageIndicators(
    pagerState: PagerState,
    pageCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { iteration ->
            val isSelected = pagerState.currentPage == iteration

            // Animate size for a bouncy effect when changing
            val size by animateDpAsState(
                targetValue = if (isSelected) 16.dp else 10.dp,
                animationSpec = spring(dampingRatio = 0.5f),
                label = "indicatorSize"
            )
            val color = if (isSelected) MainYellow else GameGrey

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, GameBlack, CircleShape)
            )
        }
    }
}

@Composable
fun GamifiedButton(
    text: String,
    isLoading: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    // Animation states
    val shadowHeight = 6.dp
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) shadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ButtonOffset"
    )

    // Color transitions for disabled state
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MainYellow else GameGrey,
        label = "BackgroundColor"
    )

    Box(contentAlignment = Alignment.Center) {
        // Shadow/base layer
        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                .height(50.dp)
                .width(220.dp)
                .clip(CircleShape)
                .background(GameBlack)
        )

        // Button layer that moves
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .height(50.dp)
                .width(220.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(2.dp, GameBlack, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = isEnabled
                ) { 
                    if (isEnabled) {
                        audioPlayer?.playSound("files/button_click.mp3")
                    }
                    onClick() 
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = GameBlack,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = GameBlack
                    )
                )
            }
        }
    }
}