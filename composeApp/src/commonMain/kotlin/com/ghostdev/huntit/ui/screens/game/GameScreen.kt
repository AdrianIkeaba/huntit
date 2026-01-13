package com.ghostdev.huntit.ui.screens.game


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
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.QuickRulesDialog
import com.ghostdev.huntit.ui.components.SheetValue
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.screens.game.components.PulseIndicator
import com.ghostdev.huntit.ui.screens.home.HomeViewModel
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.PermissionCallback
import com.ghostdev.huntit.utils.PermissionStatus
import com.ghostdev.huntit.utils.PermissionType
import com.ghostdev.huntit.utils.createPermissionsManager
import com.ghostdev.huntit.utils.toUserFriendlyError
import com.plusmobileapps.konnectivity.Konnectivity
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

private val GameBlack = Color(0xFF1A1A1A)
private val GameGrey = Color(0xFFE5E5E5)

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
    
    LaunchedEffect(state.error) {
        if (state.error != null) {
            val errorString = state.error ?: "Unknown error"
            
            val isUserFriendlyError = errorString.contains("already submitted") ||
                                     errorString.contains("No active game found") ||
                                     errorString.contains("Wait for the next round") ||
                                     errorString.contains("round has ended") ||
                                     errorString.contains("Game is in cooldown")
            
            val displayError = if (isUserFriendlyError) {
                errorString
            } else {
                errorString.toUserFriendlyError("Something went wrong with the game.")
            }
            
            snackbarHostState.showSnackbar(if (isUserFriendlyError) displayError else "Error: $displayError")
            
            viewModel.clearError()
        }
    }

    DisposableEffect(key1 = Unit) {
        viewModel.handleUserReturnedToScreen()
        
        val comingFromPhotoReview = viewModel.state.value.shouldNavigateToPhoto || 
                                  viewModel.state.value.hasSubmittedCurrentRound
                                  
        scope.launch {
            try {
                val initDelay = if (comingFromPhotoReview) 1500L else 300L
                delay(initDelay)
                viewModel.initializeGame()
                
                if (comingFromPhotoReview) {
                    delay(1000L)
                }
            } catch (e: Exception) {
                delay(500)
                try {
                    viewModel.initializeGame()
                } catch (e2: Exception) {
                }
            }
        }
        
        val lifecycleMonitorJob = scope.launch {
            var wasInForeground = true
            var lastTimeRemainingMs = state.timeRemainingMs
            var stuckAtZeroCounter = 0
            var hasTriedResync = false
            
            var lastRoundNumber = state.currentRound
            var lastPhase = state.currentPhase
            var scoreRefreshNeeded = false
            
            while (true) {
                delay(1000)
                
                try {
                    val isInForeground = true
                    
                    if (isInForeground && !wasInForeground) {
                        viewModel.handleAppLifecycleEvent(isInForeground = true)
                        lastTimeRemainingMs = state.timeRemainingMs
                        stuckAtZeroCounter = 0
                        hasTriedResync = false
                        lastRoundNumber = state.currentRound
                        lastPhase = state.currentPhase
                    } else if (!isInForeground && wasInForeground) {
                        viewModel.handleAppLifecycleEvent(isInForeground = false)
                    }
                    wasInForeground = isInForeground
                    
                    if (state.currentRound != lastRoundNumber || state.currentPhase != lastPhase) {
                        scoreRefreshNeeded = true
                        lastRoundNumber = state.currentRound
                        lastPhase = state.currentPhase
                    }
                    
                    if (scoreRefreshNeeded && isInForeground) {
                        viewModel.handleUserReturnedToScreen()
                        scoreRefreshNeeded = false
                    }
                    
                    if (isInForeground && state.timeRemainingMs == 0L) {
                        if (lastTimeRemainingMs == 0L) {
                            stuckAtZeroCounter++
                            
                            if (stuckAtZeroCounter >= 3 && !hasTriedResync) {
                                viewModel.handleUserReturnedToScreen()
                                hasTriedResync = true
                                scoreRefreshNeeded = false
                            } else if (stuckAtZeroCounter >= 10) {
                                viewModel.initializeGame()
                                hasTriedResync = true
                                stuckAtZeroCounter = 0
                                scoreRefreshNeeded = false
                            }
                        } else {
                            stuckAtZeroCounter = 1
                            hasTriedResync = false
                            scoreRefreshNeeded = true
                        }
                    } else {
                        stuckAtZeroCounter = 0
                        hasTriedResync = false
                    }
                    
                    lastTimeRemainingMs = state.timeRemainingMs
                    
                } catch (e: Exception) {
                }
            }
        }

        onDispose {
            lifecycleMonitorJob.cancel()
            viewModel.handleUserLeftScreen()
            try {
                (konnectivity as? AutoCloseable)?.close()
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(state.shouldNavigateToWinners) {
        if (state.shouldNavigateToWinners) {
            try {
                val gameRoom = state.gameRoom
                val currentRound = state.currentRound
                val currentPhase = state.currentPhase
                val isLikelyStartingGame = currentRound <= 1 && 
                                         (currentPhase == GamePhase.IN_PROGRESS ||
                                          currentPhase == GamePhase.LOBBY)

                if (!isLikelyStartingGame) {
                    viewModel.onWinnersNavigationHandled()
                    navigateToWinners()
                } else {
                    viewModel.onWinnersNavigationHandled()
                    delay(500)
                    viewModel.handleUserReturnedToScreen()
                }
            } catch (e: Exception) {
                delay(500)
                try {
                    viewModel.onWinnersNavigationHandled()
                    navigateToWinners()
                } catch (e2: Exception) {
                }
            }
        }
    }

    LaunchedEffect(state.shouldNavigateToPhoto) {
        if (state.shouldNavigateToPhoto) {
            try {
                viewModel.onPhotoNavigationHandled()
                shouldCheckPermission = true 
            } catch (e: Exception) {
                viewModel.onPhotoNavigationHandled()
            }
        }
    }

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
            if (permissionType == PermissionType.CAMERA) {
                when (status) {
                    PermissionStatus.GRANTED -> {
                        try {
                            navigateToCamera()
                        } catch (e: Exception) {
                            scope.launch {
                                val errorMessage = e.message ?: "Unknown error"
                                val isCameraError = errorMessage.contains("camera") || 
                                                  errorMessage.contains("permission") ||
                                                  errorMessage.contains("image")
                                
                                val displayError = if (isCameraError) {
                                    errorMessage
                                } else {
                                    errorMessage.toUserFriendlyError("Error launching camera. Please try again.")
                                }
                                
                                snackbarHostState.showSnackbar(if (isCameraError) displayError else "Error: $displayError")
                            }
                        }
                    }
                    PermissionStatus.DENIED -> scope.launch {
                        snackbarHostState.showSnackbar("Camera permission is required to take photos. Please enable it in your device settings.")
                    }
                    else -> {
                    }
                }
            }
        }
    })

    if (shouldCheckPermission) {
        shouldCheckPermission = false
        
        if (!permissionsManager.isPermissionGranted(PermissionType.CAMERA)) {
            permissionsManager.askPermission(PermissionType.CAMERA)
        } else {
            navigateToCamera()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        viewModel.onSubmitClick()
                    }
                )
            }
        }
        
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

    val leaderboardEntries = state.leaderboard

    val currentUser =
        state.leaderboard.firstOrNull { it.participant.id == viewModel.getCurrentUserId() }
    val currentUserName = currentUser?.participant?.name ?: "You"
    val currentUserAvatar = currentUser?.participant?.avatarId ?: 1
    val currentUserPoints = currentUser?.points ?: 0

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                    )
                }

                Box(
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                    )

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

            PulseIndicator(
                time = timeFormatted,
                color = timerColor,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canSubmit) MainYellow else Color.LightGray)
                    .border(1.5.dp, Color.Black, RoundedCornerShape(16.dp))
                    .clickable { 
                        if (canSubmit) {
                            if (isCooldown) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please wait for the next round to start")
                                }
                            } else {
                                onSubmitClick()
                            }
                        } else {
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