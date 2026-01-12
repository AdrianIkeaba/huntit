package com.ghostdev.huntit.ui.screens.game

import androidx.compose.animation.core.*
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.data.model.SubmissionState
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.CameraController
import com.ghostdev.huntit.utils.CameraView
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.close
import huntit.composeapp.generated.resources.flash
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)

@Composable
fun PhotoScreen(
    innerPadding: PaddingValues,
    controller: CameraController = CameraController(),
    viewModel: SubmissionViewModel,
    challenge: String, // This might be empty, we'll use cached value if so
    timeRemaining: String, // Used only as fallback
    navigateBack: () -> Unit,
    navigateToResults: () -> Unit
) {
    // Use remember for values that shouldn't trigger recomposition of the entire screen
    val flashOn = remember { mutableStateOf(controller.isFlashOn()) }
    var capturedPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    
    // Get cached challenge text and store it in a remembered state to ensure persistence
    val cachedChallenge = viewModel.getCachedChallenge()
    
    // Initialize and persist the challenge text
    // This state will not be reset when photos are taken or other UI events happen
    var currentChallenge by remember { 
        mutableStateOf(
            challenge.ifBlank { cachedChallenge.ifBlank { "Find something interesting" } } // Default fallback to prevent "No challenge available"
        )
    }
    
    // Log for debugging
    println("PhotoScreen recomposed. Challenge: '$challenge', Cached: '$cachedChallenge', Current: '$currentChallenge'")
    
    // If we have a valid challenge from params or cache, ensure it's set
    LaunchedEffect(challenge, cachedChallenge) {
        if (challenge.isNotBlank() && currentChallenge != challenge) {
            currentChallenge = challenge
            println("Updated currentChallenge from challenge parameter: $challenge")
        } else if (challenge.isBlank() && cachedChallenge.isNotBlank() && currentChallenge != cachedChallenge) {
            currentChallenge = cachedChallenge
            println("Updated currentChallenge from cached challenge: $cachedChallenge")
        }
    }

    // Collect submission state
    val submissionState by viewModel.submissionState.collectAsState()
    
    // Get cached time remaining and phase end time
    val cachedTimeRemaining = viewModel.getCachedTimeRemaining()
    // Phase end time is passed directly to the function or may be cached elsewhere
    val phaseEndsAtMs = if (challenge.isNotBlank()) viewModel.getCachedPhaseEndsAtMs() else 0L
    
    // We'll use a separate component for the timer to prevent full screen recomposition
    val timerState = remember { TimerState() }
    
    // Initialize the timer state - this runs whenever we revisit the screen
    // using phaseEndsAtMs as the key to trigger recalculation on every visit
    LaunchedEffect(phaseEndsAtMs) {
        if (phaseEndsAtMs > 0) {
            // We have a valid end time from the server, use it
            println("Using absolute end time from server: $phaseEndsAtMs")
            timerState.setEndTime(phaseEndsAtMs)
        } else {
            // Fallback to relative time if no absolute end time is available
            val staticTime = when {
                timeRemaining.isNotBlank() && timeRemaining != "00:00" -> {
                    println("Using provided relative time: $timeRemaining")
                    timeRemaining
                }
                else -> {
                    val cachedTimeRemaining = viewModel.getCachedTimeRemaining()
                    if (cachedTimeRemaining.isNotBlank() && cachedTimeRemaining != "00:00") {
                        println("Using cached relative time: $cachedTimeRemaining")
                        cachedTimeRemaining
                    } else {
                        // Fallback to a reasonable default
                        println("Using default relative time: 01:30")
                        "01:30"
                    }
                }
            }
            timerState.setInitialTime(staticTime)
        }
        
        println("PhotoScreen initialized with challenge: $currentChallenge, phaseEndsAtMs: $phaseEndsAtMs")
    }

    // Effect to handle state transitions
    LaunchedEffect(submissionState) {
        println("PhotoScreen - submissionState changed to: $submissionState")
        
        when (submissionState) {
            is SubmissionState.Success, is SubmissionState.Failed, is SubmissionState.Error -> {
                // Add delay to ensure review data is fully processed before navigation
                println("PhotoScreen - processing complete, waiting briefly before navigating to results")
                
                // First ensure the review data is created and stored
                try {
                    // Wait to ensure data is fully processed and stored
                    delay(500)
                    
                    // Verify review data is available before navigation
                    val reviewData = viewModel.getReviewData()
                    if (reviewData != null) {
                        println("PhotoScreen - review data ready, navigating to results")
                        navigateToResults()
                    } else {
                        // If still no data, wait a bit longer and try again
                        println("PhotoScreen - review data not ready yet, waiting longer")
                        delay(500)
                        
                        // Navigate regardless - the review screen will keep checking for data
                        println("PhotoScreen - navigating to results after extended wait")
                        navigateToResults()
                    }
                } catch (e: Exception) {
                    println("PhotoScreen - error during navigation preparation: ${e.message}")
                    // Navigate anyway, the review screen can handle missing data
                    navigateToResults()
                }
            }
            is SubmissionState.Idle -> {
                // No navigation needed for Idle state
                println("PhotoScreen - in Idle state")
            }
            is SubmissionState.Capturing -> {
                // Photo capture in progress
                println("PhotoScreen - in capturing state")
            }
            is SubmissionState.Processing, is SubmissionState.Uploading, is SubmissionState.Verifying -> {
                println("PhotoScreen - in processing state: $submissionState")
                // Processing states - no navigation needed
            }
            null -> {
                // Null state should be avoided, but handle it gracefully if it occurs
                println("WARNING: PhotoScreen - submissionState is null, this should not happen")
                // If we somehow get a null state, reset and navigate back to prevent blank screen
                viewModel.resetState()
                navigateBack()
            }
        }
    }

    // Effect to handle clean up and ensure state is reset when going back
    DisposableEffect(Unit) {
        println("PhotoScreen entered, setting up DisposableEffect")
        
        // Use a mutable variable to track navigation type
        var forwardNavigation = false
        
        // Set up a state watcher to detect navigation to results early
        // This ensures we don't reset state when navigating forward
        val stateWatchJob = MainScope().launch {
            viewModel.submissionState.collect { state ->
                if (state is SubmissionState.Success || 
                    state is SubmissionState.Failed || 
                    state is SubmissionState.Error) {
                    println("Detected result state ($state), marking for forward navigation")
                    forwardNavigation = true
                }
            }
        }
        
        onDispose {
            // Cancel the state watch job first
            stateWatchJob.cancel()
            
            println("PhotoScreen leaving, forwardNavigation flag: $forwardNavigation")
            
            // Check the state again to be double sure
            val finalState = viewModel.submissionState.value
            if (finalState is SubmissionState.Success || 
                finalState is SubmissionState.Failed || 
                finalState is SubmissionState.Error) {
                println("PhotoScreen leaving due to forward navigation to results ($finalState), preserving state")
                forwardNavigation = true
            }
            
            // If we're not navigating forward, reset the state
            if (!forwardNavigation) {
                println("PhotoScreen leaving due to back navigation, resetting state")
                // Use MainScope to ensure the reset happens even if our coroutine is canceled
                MainScope().launch {
                    try {
                        viewModel.resetState()
                        println("Successfully reset state on back navigation")
                    } catch (e: Exception) {
                        println("Error resetting state on back navigation: ${e.message}")
                    }
                }
            } else {
                println("Preserved state for forward navigation to results screen")
            }
        }
    }

    // Start the timer countdown in a separate effect
    // Using a more specific key to ensure it runs properly on re-composition
    LaunchedEffect(key1 = "timer_start", key2 = phaseEndsAtMs) {
        println("Starting timer countdown from LaunchedEffect with phaseEndsAtMs: $phaseEndsAtMs")
        timerState.startCountdown()
    }
    
    // Set up timer expiry callback
    LaunchedEffect(timerState) {
        timerState.setOnExpiredCallback {
            println("Timer expired callback triggered in PhotoScreen")
            
            // Use a dedicated scope for navigation that won't be canceled 
            // even if the screen composition changes
            val navScope = MainScope()
            
                            if (capturedPhoto == null) {
                    // No photo captured, navigate back after a brief delay
                    navScope.launch {
                        try {
                            println("Timer expired with no photo - preparing to navigate back")
                            
                            // First, reset the state to ensure clean navigation
                            try {
                                viewModel.resetState()
                                println("Successfully reset viewModel state")
                            } catch (e: Exception) {
                                println("Failed to reset viewModel state: ${e.message}")
                            }
                            
                            // Small delay to ensure UI updates and state resets properly
                            delay(300)
                            
                            // Then navigate
                            println("Navigating back to game screen after timer expiry")
                            navigateBack()
                            println("Navigation call completed")
                            
                        } catch (e: Exception) {
                            println("Error during timer expiry navigation: ${e.message}")
                            
                            // Try one more time after a longer delay
                            delay(800)
                            try {
                                println("Retrying navigation after timer expiry")
                                // Force cleanup again before retry
                                viewModel.resetState()
                                navigateBack()
                                println("Retry navigation completed")
                            } catch (e2: Exception) {
                                println("Failed to navigate after timer expiry: ${e2.message}")
                            }
                        }
                    }
            } else {
                // Photo was captured, give a brief grace period to submit
                navScope.launch {
                    try {
                        println("Timer expired with photo - giving grace period")
                        // Show time's up message for 2 seconds
                        delay(2000) 
                        
                        // If still on this screen and not in the process of submitting, force navigation
                        val currentState = viewModel.submissionState.value
                        println("After grace period, submission state is: $currentState")
                        
                        if (currentState !is SubmissionState.Processing &&
                            currentState !is SubmissionState.Uploading &&
                            currentState !is SubmissionState.Verifying) {
                            println("Timer expired grace period ended - navigating back")
                            viewModel.resetState()
                            // Ensure we're on the main thread for navigation
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                navigateBack()
                            }
                        } else {
                            println("Not navigating back after timer as submission is in progress: $currentState")
                        }
                    } catch (e: Exception) {
                        // Catch any exceptions to prevent crashes on timer expiration
                        println("Error in timer expiration handler: ${e.message}")
                        // Wait a bit then try again
                        delay(500)
                        try {
                            viewModel.resetState()
                            navigateBack()
                        } catch (e2: Exception) {
                            println("Failed again to navigate after timer expiry: ${e2.message}")
                        }
                    }
                }
            }
        }
    }
    
    // Log challenge state before rendering PhotoComponent
    println("Rendering PhotoComponent with challenge: '$currentChallenge', photo null?: ${capturedPhoto == null}")
    
    PhotoComponent(
        innerPadding = innerPadding,
        controller = controller,
        flashOn = flashOn,
        capturedPhoto = capturedPhoto,
        challenge = currentChallenge,
        timerState = timerState,
        submissionState = submissionState,
        toggleFlash = {
            controller.toggleFlash()
            flashOn.value = !flashOn.value
        },
        onShutterClick = {
            controller.takePhoto { imageBitmap ->
                capturedPhoto = imageBitmap
            }
        },
        onCloseClick = {
            println("Close button clicked - resetting state and navigating back")
            try {
                // Always reset state when explicitly closing to avoid blank screen
                viewModel.resetState()
            } catch (e: Exception) {
                println("Error resetting state on close: ${e.message}")
            }
            navigateBack()
        },
        onUsePhotoClick = {
            capturedPhoto?.let { photo ->
                viewModel.processSubmission(photo)
            }
        },
        onTryAgainClick = {
            capturedPhoto = null
        }
    )
}

// Create a separate TimerState class to isolate timer updates from the main composition
@OptIn(ExperimentalTime::class)
class TimerState {
    // Public StateFlow that can be collected for changes
    private val _timeStateFlow = MutableStateFlow("00:00")
    val timeStateFlow = _timeStateFlow
    
    // Property accessed from UI for one-time reads
    val formattedTime: String 
        get() = _timeStateFlow.value
        
    // Tracks whether the timer has expired (reached 00:00)
    private val _isExpired = MutableStateFlow(false)
    val isExpired: Boolean
        get() = _isExpired.value
    
    // Callback to notify when timer expires
    private var onExpiredCallback: (() -> Unit)? = null
    
    private var isRunning = false
    private var timerJob: kotlinx.coroutines.Job? = null
    
    // Store the absolute end time
    private var phaseEndsAt: kotlin.time.Instant? = null
    
    // Store local time offset
    private var localTimeOffsetMs: Long = 0
    
    // Set a callback to be invoked when timer expires
    fun setOnExpiredCallback(callback: () -> Unit) {
        onExpiredCallback = callback
        
        // If timer already expired when callback is set, invoke immediately
        if (isExpired) {
            callback()
        }
    }
    
    @OptIn(ExperimentalTime::class)
    fun setEndTime(phaseEndsAtMs: Long) {
        // Convert milliseconds to Instant
        this.phaseEndsAt = kotlin.time.Instant.fromEpochMilliseconds(phaseEndsAtMs)
        
        // Check if the end time is already in the past
        val now = Clock.System.now()
        if (this.phaseEndsAt!! <= now) {
            // Time has already expired
            _isExpired.value = true
            _timeStateFlow.value = "00:00"
            println("Timer end time is in the past, marking as expired immediately: $phaseEndsAt vs $now")
            onExpiredCallback?.invoke()
        } else {
            // Time is still in the future
            println("Timer end time set to: $phaseEndsAt, remaining: ${(this.phaseEndsAt!!.toEpochMilliseconds() - now.toEpochMilliseconds())/1000.0}s")
            _isExpired.value = false
            
            // Calculate initial remaining time
            updateTimeDisplay()
        }
    }
    
    fun setInitialTime(timeString: String) {
        try {
            val parts = timeString.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toInt()
                val seconds = parts[1].toInt()
                
                // Convert to milliseconds from now
                val totalMs = (minutes * 60 + seconds) * 1000L
                
                // Don't allow negative time, but also don't artificially add time
                val actualMs = totalMs.coerceAtLeast(0L)
                
                // If time is already expired, mark as expired immediately
                if (actualMs <= 0L) {
                    _isExpired.value = true
                    _timeStateFlow.value = "00:00"
                    println("Initial time is zero or negative, marking timer as expired")
                } else {
                    // Set end time as current time + duration
                    setEndTime(Clock.System.now().toEpochMilliseconds() + actualMs)
                    println("Initial time set from string: $timeString (${actualMs}ms)")
                }
            }
        } catch (e: Exception) {
            println("Error parsing initial time: $e")
            // Fallback to a short time (10 seconds) if there's an error
            // This is better than defaulting to 90 seconds which is too long
            setEndTime(Clock.System.now().toEpochMilliseconds() + 10_000L)
            println("Error parsing time, using 10s fallback")
        }
    }
    
    @OptIn(ExperimentalTime::class)
    private fun updateTimeDisplay() {
        val phaseEndsAt = phaseEndsAt ?: return
        
        // Calculate time remaining using local clock with offset adjustment
        val now = Clock.System.now()
        val adjustedNow = kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - localTimeOffsetMs)
        val remaining = phaseEndsAt - adjustedNow
        val remainingMs = remaining.inWholeMilliseconds.coerceAtLeast(0L)
        
        // Convert milliseconds to MM:SS format
        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minutesStr = if (minutes < 10) "0$minutes" else "$minutes"
        val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
        
        _timeStateFlow.value = "$minutesStr:$secondsStr"
        println("Timer updated: ${_timeStateFlow.value}")
    }
    
    @OptIn(ExperimentalTime::class)
    fun startCountdown() {
        // Only start if not already running
        if (isRunning) {
            println("Timer already running, not starting again")
            return
        }
        
        val phaseEndsAt = phaseEndsAt
        if (phaseEndsAt == null) {
            println("Timer has no end time set, using default 90 seconds")
            setEndTime(Clock.System.now().toEpochMilliseconds() + 90_000L)
        }
        
        println("Starting timer countdown to ${this.phaseEndsAt}")
        isRunning = true
        
        // Cancel any existing job
        timerJob?.cancel()
        
        // Use MainScope for UI updates
        timerJob = MainScope().launch {
            try {
                println("Timer countdown launched")
                
                while (isActive) {
                    // Update the time display
                    updateTimeDisplay()
                    
                    // Check if time has reached zero
                    val now = Clock.System.now()
                    val adjustedNow = kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - localTimeOffsetMs)
                    val remaining = this@TimerState.phaseEndsAt?.minus(adjustedNow)?.inWholeMilliseconds ?: 0L
                    
                                            if (remaining <= 0) {
                            // Check if we need to trigger the expired callback
                            if (!_isExpired.value) {
                                println("Timer expired - triggering callback")
                                // First set the expired flag
                                _isExpired.value = true
                                // Then ensure time display shows 00:00
                                _timeStateFlow.value = "00:00"
                                
                                try {
                                    // Call the callback in a safe way
                                    onExpiredCallback?.invoke()
                                    println("Timer expiration callback completed")
                                } catch (e: Exception) {
                                    println("Error in timer expiration callback: ${e.message}")
                                }
                            }
                            
                            // We've reached zero but keep updating to show 00:00
                            delay(1000)
                        } else {
                            // Update every second if time remains
                            delay(1000)
                        }
                }
            } catch (e: Exception) {
                println("Timer error: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }
    
    fun stopCountdown() {
        timerJob?.cancel()
        isRunning = false
    }
}

@Composable
private fun PhotoComponent(
    innerPadding: PaddingValues,
    controller: CameraController,
    flashOn: MutableState<Boolean>,
    capturedPhoto: ImageBitmap?,
    challenge: String,
    timerState: TimerState,
    submissionState: SubmissionState,
    toggleFlash: () -> Unit,
    onShutterClick: () -> Unit,
    onCloseClick: () -> Unit,
    onUsePhotoClick: () -> Unit,
    onTryAgainClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Show either camera view or captured photo
        if (capturedPhoto == null) {
            CameraView(controller = controller)
        } else {
            Image(
                bitmap = capturedPhoto,
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Header - always visible
        HeaderComponent(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            title = challenge,
            timerState = timerState,
            flashOn = flashOn.value,
            showFlashButton = capturedPhoto == null,
            onToggleFlash = toggleFlash,
            onCloseClick = onCloseClick
        )

        // Bottom section - show different options based on state
        when {
            // Show loading overlay when processing submission
            submissionState is SubmissionState.Processing ||
                    submissionState is SubmissionState.Uploading ||
                    submissionState is SubmissionState.Verifying -> {
                LoadingOverlay(
                    modifier = Modifier.fillMaxSize(),
                    submissionState = submissionState
                )
            }

            // Show shutter when no photo taken
            capturedPhoto == null -> {
                ShutterComponent(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    onShutterClick = onShutterClick,
                    isEnabled = !timerState.isExpired // Disable when timer expires
                )
                
                // If timer expired, show time's up message
                if (timerState.isExpired) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 120.dp)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TIME'S UP!",
                                style = TextStyle(
                                    fontFamily = testSohneFont(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.Red
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Returning to game...",
                                style = TextStyle(
                                    fontFamily = patrickHandFont(),
                                    fontSize = 16.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Show use photo/try again when photo is captured
            else -> {
                PhotoActionsSection(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    onUsePhotoClick = onUsePhotoClick,
                    onTryAgainClick = onTryAgainClick,
                    isEnabled = !timerState.isExpired // Disable when timer expires
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay(
    modifier: Modifier = Modifier,
    submissionState: SubmissionState
) {
    
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        // Simple loading card - fixed size to prevent layout shifts
        Box(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GameBlack)
                .border(2.dp, MainYellow, RoundedCornerShape(24.dp))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .padding(32.dp)
                    .width(216.dp)
            ) {
                // Circular progress indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MainYellow,
                    strokeWidth = 4.dp
                )
                
                // Static title
                Text(
                    text = "SUBMITTING PHOTO",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        color = GameWhite
                    )
                )
                
                // Static description
                Text(
                    text = "Please wait...",
                    style = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@Composable
private fun HeaderComponent(
    modifier: Modifier,
    title: String,
    timerState: TimerState,
    flashOn: Boolean = false,
    showFlashButton: Boolean = true,
    onToggleFlash: () -> Unit = {},
    onCloseClick: () -> Unit
) {
    // Log the title to help diagnose issues
    println("HeaderComponent: displaying challenge title: '$title', isEmpty=${title.isEmpty()}")
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top row with close and flash buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { onCloseClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close),
                    contentDescription = "Close",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }
            
            // Flash button (only shown when camera is active)
            if (showFlashButton) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onToggleFlash() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.flash),
                        contentDescription = "Flash",
                        modifier = Modifier.size(20.dp),
                        tint = if (flashOn) MainYellow else Color.White
                    )
                }
            } else {
                // Empty box for layout consistency
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        // Challenge and timer information
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Store title in a variable for the Text component
                // Important: Don't use ifEmpty here, as we want to ensure the title is always displayed
                val displayTitle = if (title.isNotBlank()) title else "Find something interesting"
                
                // Log every time we display a title
                println("HeaderComponent displaying title: '$displayTitle' (original: '$title')")
                
                Text(
                    text = displayTitle,
                    style = TextStyle(
                        fontFamily = patrickHandFont(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MainYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Time Remaining:",
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )

                    // Use TimerText to prevent recomposition of the parent
                    TimerText(timerState = timerState)
                }
            }
        }
    }
}

// Separate composable for the timer to prevent recomposition of parent components
@Composable
private fun TimerText(timerState: TimerState) {
    // Collect the timer state directly as a state (more reliable than LaunchedEffect with mutableState)
    val timeText by timerState.timeStateFlow.collectAsState()
    
    // Use directly the collected time value
    Text(
        text = timeText,
        style = TextStyle(
            fontFamily = testSohneFont(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MainGreen
        )
    )
}

@Composable
private fun ShutterComponent(
    modifier: Modifier,
    onShutterClick: () -> Unit,
    isEnabled: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Shutter button
        Box(
            modifier = Modifier
                .size(70.dp)
                .border(2.dp, if (isEnabled) Color.White else Color.Gray, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (isEnabled) Color.White.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f))
                .border(2.dp, if (isEnabled) Color.White else Color.Gray, CircleShape)
                .clickable(enabled = isEnabled) { onShutterClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) Color.White else Color.Gray)
            )
        }

        // Instruction text
        Text(
            text = "Position your subject in the frame",
            style = TextStyle(
                fontFamily = patrickHandFont(),
                fontSize = 16.sp,
                color = Color.White
            )
        )
    }
}

@Composable
private fun PhotoActionsSection(
    modifier: Modifier,
    onUsePhotoClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    isEnabled: Boolean = true
) {
    // Use the dedicated UsePhotoSection component from the imported file
    com.ghostdev.huntit.ui.screens.game.UsePhotoSection(
        modifier = modifier,
        onUsePhotoClick = if (isEnabled) onUsePhotoClick else { {} }, // No-op if disabled
        onTryAgainClick = onTryAgainClick,
        isEnabled = isEnabled
    )
}

