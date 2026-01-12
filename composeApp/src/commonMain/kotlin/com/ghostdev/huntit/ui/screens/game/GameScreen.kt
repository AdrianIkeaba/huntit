package com.ghostdev.huntit.ui.screens.game


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.data.model.GamePhase
import com.ghostdev.huntit.ui.components.AnchoredBottomSheet
import com.ghostdev.huntit.ui.components.SheetValue
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.QuickRulesDialog
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.screens.game.components.PulseIndicator
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.screens.lobby.components.Avatars
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.PermissionCallback
import com.ghostdev.huntit.utils.PermissionStatus
import com.ghostdev.huntit.utils.PermissionType
import com.ghostdev.huntit.utils.createPermissionsManager
import com.plusmobileapps.konnectivity.Konnectivity
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.info
import huntit.composeapp.generated.resources.submit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

// Consistent Game Colors - matching other screens
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@Composable
fun GameScreen(
    innerPadding: PaddingValues,
    navigateToCamera: () -> Unit,
    navigateToWinners: () -> Unit = {},
    viewModel: GameViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var shouldCheckPermission by remember { mutableStateOf(false) }
    val konnectivity = remember { Konnectivity() }
    
    // Debug log state at composition time
    println("GameScreen composition - phase: ${state.currentPhase}, canSubmit: ${state.canSubmit}, hasSubmitted: ${state.hasSubmittedCurrentRound}")
    
    // Handle any error messages from the ViewModel
    LaunchedEffect(state.error) {
        if (state.error != null) {
            // Get the error string
            val errorString = state.error ?: "Unknown error"
            
            // Check if it's a validation or known user-friendly error
            val isUserFriendlyError = errorString.contains("already submitted") ||
                                     errorString.contains("No active game found") ||
                                     errorString.contains("Wait for the next round") ||
                                     errorString.contains("round has ended") ||
                                     errorString.contains("Game is in cooldown")
            
            // Only convert technical errors to user-friendly message
            val displayError = if (isUserFriendlyError) {
                errorString // Use validation error as-is
            } else {
                errorString.toUserFriendlyError("Something went wrong with the game.")
            }
            
            // Show the error message without "Error:" prefix for user-friendly errors
            snackbarHostState.showSnackbar(if (isUserFriendlyError) displayError else "Error: $displayError")
            
            // Clear the error state to prevent it from reappearing
            viewModel.clearError()
        }
    }

    // Handle screen lifecycle events, foreground/background state, and cleanup
    DisposableEffect(key1 = Unit) {
        // Initial setup - user just arrived at screen
        println("GameScreen: User returned to screen - initializing game")
        viewModel.handleUserReturnedToScreen()
        
        // Important: Add a small delay to ensure the game is properly initialized
        // This helps when returning from PhotoReviewScreen
        scope.launch {
            try {
                println("GameScreen: Starting initialization delay")
                delay(300)
                println("GameScreen: Initializing game after delay")
                viewModel.initializeGame()
                println("GameScreen: Game initialized")
            } catch (e: Exception) {
                println("GameScreen: Error during initialization: ${e.message}")
                // If initialization fails, try again after a brief pause
                delay(500)
                try {
                    viewModel.initializeGame()
                    println("GameScreen: Game initialized on second attempt")
                } catch (e2: Exception) {
                    println("GameScreen: Failed to initialize game: ${e2.message}")
                }
            }
        }
        
        // Set up lifecycle monitoring - enhanced version
        // This includes timer state checking to detect if we're stuck at 0:00
        val lifecycleMonitorJob = scope.launch {
            var wasInForeground = true
            var lastTimeRemainingMs = state.timeRemainingMs
            var stuckAtZeroCounter = 0
            var hasTriedResync = false
            
            // Track round changes for score refresh
            var lastRoundNumber = state.currentRound
            var lastPhase = state.currentPhase
            var scoreRefreshNeeded = false
            
            while (true) {
                delay(1000) // Check every second
                
                try {
                    // In a real app, this would use platform-specific lifecycle APIs
                    // For demo purposes, we assume we're in foreground when this code runs
                    val isInForeground = true
                    
                    // If foreground state changed, notify the ViewModel
                    if (isInForeground && !wasInForeground) {
                        println("App came to foreground, performing full sync")
                        viewModel.handleAppLifecycleEvent(isInForeground = true)
                        lastTimeRemainingMs = state.timeRemainingMs
                        stuckAtZeroCounter = 0
                        hasTriedResync = false
                        lastRoundNumber = state.currentRound
                        lastPhase = state.currentPhase
                    } else if (!isInForeground && wasInForeground) {
                        println("App went to background")
                        viewModel.handleAppLifecycleEvent(isInForeground = false)
                    }
                    wasInForeground = isInForeground
                    
                    // Check for round changes or phase changes which indicate scores may need refreshing
                    if (state.currentRound != lastRoundNumber || state.currentPhase != lastPhase) {
                        println("Round or phase changed - flagging score refresh needed")
                        scoreRefreshNeeded = true
                        lastRoundNumber = state.currentRound
                        lastPhase = state.currentPhase
                    }
                    
                    // Periodically refresh scores if changes were detected
                    if (scoreRefreshNeeded && isInForeground) {
                        println("Performing score refresh due to game state changes")
                        viewModel.handleUserReturnedToScreen() // Full refresh including scores
                        scoreRefreshNeeded = false
                    }
                    
                    // Check if we're stuck at 0:00 - this helps detect when phase has ended
                    // but UI hasn't updated
                    if (isInForeground && state.timeRemainingMs == 0L) {
                        if (lastTimeRemainingMs == 0L) {
                            // Timer has been at zero for multiple checks
                            stuckAtZeroCounter++
                            
                            // If timer has been stuck at zero for more than 3 seconds and 
                            // we haven't tried to resync yet, force a full resync
                            if (stuckAtZeroCounter >= 3 && !hasTriedResync) {
                                println("Timer stuck at 0:00 for ${stuckAtZeroCounter}s, forcing resync")
                                viewModel.handleUserReturnedToScreen() // Force full resync
                                hasTriedResync = true
                                scoreRefreshNeeded = false // We just did a full refresh
                            } else if (stuckAtZeroCounter >= 10) {
                                // If still stuck after 10 seconds, try again
                                println("Timer still stuck at 0:00 after 10s, trying advanced recovery")
                                // Force game initialization and restart everything
                                viewModel.initializeGame()
                                hasTriedResync = true
                                stuckAtZeroCounter = 0 // Reset counter
                                scoreRefreshNeeded = false // We just did a full refresh
                            }
                        } else {
                            // Timer just reached zero
                            stuckAtZeroCounter = 1
                            hasTriedResync = false
                            // When timer first reaches zero, flag that scores need refreshing
                            // (submissions might be processed at round end)
                            scoreRefreshNeeded = true
                        }
                    } else {
                        // Timer is not at zero or has changed
                        stuckAtZeroCounter = 0
                        hasTriedResync = false
                    }
                    
                    lastTimeRemainingMs = state.timeRemainingMs
                    
                } catch (e: Exception) {
                    println("Error in lifecycle monitoring: ${e.message}")
                }
            }
        }

        onDispose {
            // Clean up resources
            lifecycleMonitorJob.cancel()
            viewModel.handleUserLeftScreen()
            try {
                (konnectivity as? AutoCloseable)?.close()
            } catch (e: Exception) {
                println("Error cleaning up network monitoring: ${e.message}")
            }
        }
    }

    // Handle navigation to winners
    LaunchedEffect(state.shouldNavigateToWinners) {
        if (state.shouldNavigateToWinners) {
            try {
                println("GameScreen: Handling navigation to winners")
                viewModel.onWinnersNavigationHandled()
                navigateToWinners()
                println("GameScreen: Successfully navigated to winners")
            } catch (e: Exception) {
                println("GameScreen: Error navigating to winners: ${e.message}")
                // Try again with a delay to ensure the navigation completes
                delay(500)
                try {
                    viewModel.onWinnersNavigationHandled()
                    navigateToWinners()
                } catch (e2: Exception) {
                    println("GameScreen: Failed to navigate to winners on second attempt: ${e2.message}")
                }
            }
        }
    }

    // Handle navigation to camera - use a simpler approach that's more resilient
    // to composition changes
    LaunchedEffect(state.shouldNavigateToPhoto) {
        if (state.shouldNavigateToPhoto) {
            try {
                println("GameScreen: Handling navigation to photo")
                // Mark navigation as handled immediately to prevent repeated attempts
                viewModel.onPhotoNavigationHandled()
                
                // Just set the flag to check permission, which will be handled below
                println("GameScreen: Setting shouldCheckPermission flag")
                shouldCheckPermission = true 
            } catch (e: Exception) {
                println("GameScreen: Error during photo navigation: ${e.message}")
                // Reset the navigation state on error
                viewModel.onPhotoNavigationHandled()
            }
        }
    }

    // Track network status
    DisposableEffect(konnectivity) {
        var wasDisconnected = false
        val job = scope.launch {
            konnectivity.isConnectedState.collect { isConnected ->
                if (!isConnected) {
                    wasDisconnected = true
                    snackbarHostState.showSnackbar("No internet connection")
                } else if (wasDisconnected) {
                    wasDisconnected = false
                    viewModel.initializeGame()
                }
            }
        }
        onDispose { job.cancel() }
    }

    val permissionsManager = createPermissionsManager(object : PermissionCallback {
        override fun onPermissionStatus(permissionType: PermissionType, status: PermissionStatus) {
            println("GameScreen: Permission callback - type: $permissionType, status: $status")
            
            if (permissionType == PermissionType.CAMERA) {
                when (status) {
                    PermissionStatus.GRANTED -> {
                        println("GameScreen: Camera permission granted, navigating to camera")
                        try {
                            // Navigate directly when permission is granted
                            navigateToCamera()
                        } catch (e: Exception) {
                            // If navigation fails, show error
                            println("GameScreen: Failed to navigate after permission granted: ${e.message}")
                            scope.launch {
                                // Check if it's a known camera-related error
                                val errorMessage = e.message ?: "Unknown error"
                                val isCameraError = errorMessage.contains("camera") || 
                                                  errorMessage.contains("permission") ||
                                                  errorMessage.contains("image")
                                
                                val displayError = if (isCameraError) {
                                    errorMessage // Use known error as-is
                                } else {
                                    errorMessage.toUserFriendlyError("Error launching camera. Please try again.")
                                }
                                
                                snackbarHostState.showSnackbar(if (isCameraError) displayError else "Error: $displayError")
                                // Clear error state - since this is a local exception, no need to call viewModel.clearError()
                            }
                        }
                    }
                    PermissionStatus.                    DENIED -> scope.launch {
                        println("GameScreen: Camera permission denied")
                        snackbarHostState.showSnackbar("Camera permission is required to take photos. Please enable it in your device settings.")
                    }
                    else -> {
                        println("GameScreen: Camera permission check completed with status: $status")
                    }
                }
            }
        }
    })

    // Permission check block - triggered by shouldCheckPermission state
    if (shouldCheckPermission) {
        println("GameScreen: Processing permission check")
        shouldCheckPermission = false  // Reset immediately to prevent multiple checks
        
        if (!permissionsManager.isPermissionGranted(PermissionType.CAMERA)) {
            println("GameScreen: Camera permission not granted, requesting...")
            permissionsManager.askPermission(PermissionType.CAMERA)
            // The callback will handle navigation if permission is granted
        } else {
            println("GameScreen: Camera permission already granted, navigating directly")
            navigateToCamera()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Use AnimatedBackground even during loading to prevent blank white screen
        AnimatedBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            if (viewModel.state.value.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading Game",
                            style = TextStyle(
                                color = Color.Black,
                                fontFamily = testSohneFont(),
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            } else {
                GameComponent(
                    innerPadding = innerPadding,
                    state = state,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    onSubmitClick = {
                        println("GameScreen: Submit button clicked - canSubmit=${state.canSubmit}, hasSubmitted=${state.hasSubmittedCurrentRound}, phase=${state.currentPhase}")
                        viewModel.onSubmitClick()
                    }
                )
            }
        }
        
        // Always show snackbar host to handle error messages
        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
    private fun GameComponent(
        innerPadding: PaddingValues = PaddingValues(0.dp),
        state: GameUiState,
        viewModel: GameViewModel,
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        onSubmitClick: () -> Unit
    ) {
    var showLeaderboardSheet by remember { mutableStateOf(false) }
    var showQuickRulesDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Get data from state
    val leaderboardEntries = state.leaderboard

    // Get current user from state
    val currentUser =
        state.leaderboard.firstOrNull { it.participant.id == viewModel.getCurrentUserId() }
    val currentUserName = currentUser?.participant?.name ?: "You"
    val currentUserAvatar = currentUser?.participant?.avatarId ?: 1
    val currentUserPoints = currentUser?.points ?: 0

    // Format time remaining
    val timeFormatted = viewModel.formatTimeRemaining(state.timeRemainingMs)
    val gameTimerStatus = viewModel.isTimerWarning(state.timeRemainingMs)
    val timerColor = when (gameTimerStatus) {
        TimerStatus.NORMAL -> MainGreen
        TimerStatus.WARNING -> MainRed
        else -> MainYellow
    }

    val isCooldown = state.currentPhase == GamePhase.COOLDOWN
    val challengeText = if (isCooldown) "Figuring out next challenge..." else state.currentChallenge
    val canSubmit = state.canSubmit && !isCooldown

    if (showQuickRulesDialog) {
        QuickRulesDialog(onDismiss = { showQuickRulesDialog = false })
    }

    // No need for AnimatedBackground here since we moved it to the parent
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .padding(bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section with round info and room name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Round pill - aligned to the left
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFFFF9F9F))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${state.currentRound}/${state.totalRounds}",
                        style = TextStyle(
                            color = Color.Black,
                            fontFamily = testSohneFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Room name - centered with improved width constraints
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.roomName.ifEmpty { "Hunt" },
                        style = TextStyle(
                            color = Color.Black,
                            fontFamily = testSohneFont(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        // Fixed: Use fillMaxWidth with a percentage to avoid taking too much space
                        modifier = Modifier
                            .fillMaxWidth(0.9f) // Use 90% of the available space in the Box
                    )
                }

                // Info icon with 3D effect - aligned to the right
                Box(
                    modifier = Modifier.size(36.dp)
                ) {
                    // Shadow Layer
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                    )

                    // Main Layer
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.5.dp, GameBlack, CircleShape)
                            .clip(CircleShape)
                            .background(GameGrey, CircleShape)
                            .clickable { showQuickRulesDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(Res.drawable.info),
                            contentDescription = "Game Rules",
                            colorFilter = ColorFilter.tint(GameBlack)
                        )
                    }
                }
            }

            // Current User Profile Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color.Black, RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        Image(
                            painter = painterResource(
                                HomeViewModel.getProfilePictureById(
                                    currentUserAvatar
                                )
                            ),
                            contentDescription = "Your Avatar",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )

                        Column {
                            Text(
                                text = currentUserName,
                                style = TextStyle(
                                    fontFamily = patrickHandFont(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )

                            Text(
                                text = "You",
                                style = TextStyle(
                                    fontFamily = patrickHandFont(),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            )
                        }
                    }

                    // Points pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🏆",
                                style = TextStyle(fontSize = 12.sp)
                            )

                            Text(
                                text = "$currentUserPoints pts",
                                style = TextStyle(
                                    fontFamily = patrickHandFont(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            // Challenge Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color.Black, RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = challengeText.ifEmpty { "Loading challenge..." },
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCooldown) Color.Gray else Color.Black
                        ),
                        // Fixed: Add proper text wrapping for long challenge text
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFFDCF0FF))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.theme.ifEmpty { "General" },
                            style = TextStyle(
                                fontFamily = patrickHandFont(),
                                fontSize = 14.sp,
                                color = Color(0xFF60748D)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Timer
            PulseIndicator(
                time = timeFormatted,
                color = timerColor,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Submit Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canSubmit) MainYellow else Color.LightGray)
                    .border(1.5.dp, Color.Black, RoundedCornerShape(16.dp))
                    // Use clickable without enabled parameter to ensure clicks are always detected
                    .clickable { 
                        println("GameScreen: Submit button clicked - canSubmit=$canSubmit, hasSubmitted=${state.hasSubmittedCurrentRound}, phase=${state.currentPhase}")
                        if (canSubmit) {
                            println("GameScreen: Submit button proceeding with click")
                            // Show a message if in cooldown
                            if (isCooldown) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please wait for the next round to start")
                                }
                            } else {
                                onSubmitClick()
                            }
                        } else {
                            println("GameScreen: Submit button ignored click - providing feedback")
                            // Provide feedback based on the reason it can't be clicked
                            scope.launch {
                                if (state.hasSubmittedCurrentRound) {
                                    snackbarHostState.showSnackbar("You've already submitted for this round")
                                } else if (state.currentPhase != GamePhase.IN_PROGRESS) {
                                    snackbarHostState.showSnackbar("Submissions are only allowed during active rounds")
                                } else {
                                    snackbarHostState.showSnackbar("Cannot submit right now")
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.hasSubmittedCurrentRound) "SUBMITTED" else "SUBMIT PHOTO",
                    style = TextStyle(
                        fontFamily = testSohneFont(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = if (canSubmit) Color.Black else Color.Gray
                )
            }
        }
    }

    // Leaderboard bottom sheet with minimalist style
    AnchoredBottomSheet(
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetBackgroundColor = Color.White,
        peekHeight = 80.dp,
        expandedHeight = 400.dp,
        initialValue = SheetValue.Collapsed,
        onStateChange = { newState ->
            showLeaderboardSheet = newState == SheetValue.Expanded
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp, top = 16.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.LightGray, RoundedCornerShape(4.dp))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Leaderboard header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🏆",
                        style = TextStyle(fontSize = 18.sp)
                    )

                    Text(
                        text = "LEADERBOARD",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(top = 8.dp)
                        .background(Color.LightGray)
                )

                // Leaderboard entries
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = showLeaderboardSheet),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(
                        bottom = if (showLeaderboardSheet) 32.dp else 0.dp,
                        top = 8.dp
                    )
                ) {
                    val displayEntries = if (showLeaderboardSheet) {
                        leaderboardEntries
                    } else {
                        leaderboardEntries.take(3)
                    }

                    items(
                        items = displayEntries,
                        key = { it.participant.id }
                    ) { entry ->
                        val isCurrentUser = entry.participant.id == viewModel.getCurrentUserId()
                        val last = entry == displayEntries.lastOrNull()

                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .background(
                                        if (isCurrentUser) Color(0xFFFFFBE6) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(if (isCurrentUser) 8.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(
                                            HomeViewModel.getProfilePictureById(
                                                entry.participant.avatarId
                                            )
                                        ),
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = entry.participant.name + (if (isCurrentUser) " (You)" else ""),
                                        style = TextStyle(
                                            fontFamily = patrickHandFont(),
                                            fontSize = 16.sp,
                                            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.Black
                                        )
                                    )
                                }

                                Text(
                                    text = "${entry.points} pts",
                                    style = TextStyle(
                                        fontFamily = patrickHandFont(),
                                        fontSize = 16.sp,
                                        color = Color.Gray
                                    )
                                )
                            }

                            if (!last) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.LightGray)
                                )
                            }
                        }
                    }

                    // "See more" when collapsed
                    if (!showLeaderboardSheet && leaderboardEntries.size > 3) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "See all ${leaderboardEntries.size} players",
                                    style = TextStyle(
                                        fontFamily = patrickHandFont(),
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}