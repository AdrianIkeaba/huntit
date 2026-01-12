package com.ghostdev.huntit.ui.screens.home

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.data.model.User
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.screens.home.components.EditProfileDialog
import com.ghostdev.huntit.ui.screens.home.components.JoinOptionDialog
import com.ghostdev.huntit.ui.screens.home.components.JoinRoomDialog
import com.ghostdev.huntit.ui.screens.home.components.LogoutConfirmationDialog
import com.ghostdev.huntit.ui.screens.home.components.SoundSettingsDialog
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.EnhancedAudioPlayer
import com.ghostdev.huntit.utils.LocalAudioPlayer
import com.ghostdev.huntit.utils.rememberSnackbarManager
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.add
import huntit.composeapp.generated.resources.history
import huntit.composeapp.generated.resources.music
import huntit.composeapp.generated.resources.people
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

// Helper function for safe function calls
private fun safeCall(action: () -> Unit) {
    try {
        action()
    } catch (e: Exception) {
        println("Error in UI action: ${e.message}")
    }
}

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    navigateToCreateGameRoom: () -> Unit,
    navigateToLobby: (String) -> Unit,
    navigateToSignIn: () -> Unit,
    navigateToPastGames: () -> Unit = {}, // Past games navigation
    navigateToPublicGames: () -> Unit = {}, // Public games navigation
    viewModel: HomeViewModel = koinViewModel()
) {
    // Safe state collection with fallback
    val uiState by remember { 
        try { 
            viewModel.uiState 
        } catch (e: Exception) { 
            println("Error getting uiState flow: ${e.message}")
            kotlinx.coroutines.flow.MutableStateFlow(HomeUiState())
        } 
    }.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarManager = rememberSnackbarManager()
    
    // Safely access audio player without forcing a return
    val audioPlayer = LocalAudioPlayer.current

    // Show error message with proper error handling
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Check if it's a validation or known user-friendly error
            val isUserFriendlyError = it.contains("Please enter") || 
                                     it.contains("Profile update failed") ||
                                     it.contains("Connection error") ||
                                     it.contains("Maximum players") ||
                                     it.contains("Room name")
            
            // Only convert technical errors to user-friendly message
            val displayError = if (isUserFriendlyError) {
                it // Use validation error as-is
            } else {
                // Safe conversion
                try {
                    it.toUserFriendlyError("Something went wrong. Please try again later.")
                } catch (e: Exception) {
                    "Something went wrong. Please try again later."
                }
            }
            
            // Show the error message without "Error:" prefix for user-friendly errors
            snackbarHostState.showSnackbar(if (isUserFriendlyError) it else "Error: $displayError")
            
            // Safely clear messages
            try {
                viewModel.clearMessages()
            } catch (e: Exception) {
                println("Error clearing messages: ${e.message}")
            }
        }
    }

    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            try {
                viewModel.clearMessages()
            } catch (e: Exception) {
                println("Error clearing success messages: ${e.message}")
            }
        }
    }

    // Show messages from SnackbarManager
    LaunchedEffect(snackbarManager.currentMessage.value) {
        val message = snackbarManager.currentMessage.value
        if (message != null) {
            snackbarHostState.showSnackbar(message.message)
            try {
                snackbarManager.clearMessage()
            } catch (e: Exception) {
                println("Error clearing snackbar message: ${e.message}")
            }
        }
    }

    // Handle navigation after successful logout
    LaunchedEffect(uiState.logoutComplete) {
        if (uiState.logoutComplete) {
            try {
                viewModel.resetLogoutState()
            } catch (e: Exception) {
                println("Error resetting logout state: ${e.message}")
            }
            navigateToSignIn()
        }
    }

    // Use an outer box to provide a stable structure
    Box(modifier = Modifier.fillMaxSize()) {
        if (audioPlayer != null) {
            // Use the normal component if audio is available
            HomeComponent(
                innerPadding = innerPadding,
                audioPlayer = audioPlayer,
                userName = uiState.user?.displayName ?: "Player",
                avatarId = uiState.user?.avatarId ?: 1,
                isLoading = uiState.isLoading,
                isLoggingOut = uiState.isLoggingOut,
                showProfileDialog = uiState.showProfileDialog,
                showLogoutConfirmation = uiState.showLogoutConfirmation,
                onEditProfileClick = { 
                    safeCall { viewModel.showProfileDialog() }
                },
                onUpdateProfile = { name, avatarId -> 
                    safeCall { viewModel.updateProfile(name, avatarId) }
                },
                onLogout = {
                    safeCall { viewModel.showLogoutConfirmation() }
                },
                onConfirmLogout = {
                    safeCall { viewModel.logout() }
                },
                onCancelLogout = {
                    safeCall { viewModel.hideLogoutConfirmation() }
                },
                onDismissProfileDialog = { 
                    safeCall { viewModel.hideProfileDialog() }
                },
                createGameRoom = { 
                    safeCall { navigateToCreateGameRoom() }
                },
                onJoinRoom = { roomCode -> 
                    safeCall { navigateToLobby(roomCode) }
                },
                onViewPastGames = {
                    safeCall { navigateToPastGames() }
                },
                onViewPublicGames = {
                    safeCall { navigateToPublicGames() }
                },
                snackbarHostState = snackbarHostState
            )
        } else {
            // Simplified fallback UI when audio player is null
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Hunt.it",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = GameBlack
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Preparing game resources...",
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = GameBlack
                    ),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                CircularProgressIndicator(color = GameBlack)
            }
        }
        
        // Always show snackbar host for error messages
        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Try to recover by reinitializing after a delay
        LaunchedEffect(Unit) {
            delay(1000)
            // Force refresh the screen
            safeCall { viewModel.clearMessages() }
        }
    }
}

@Composable
@Preview
private fun HomeComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    audioPlayer: EnhancedAudioPlayer,
    userName: String = "Fabulous Fighter",
    avatarId: Int = 1,
    isLoading: Boolean = false,
    isLoggingOut: Boolean = false,
    showProfileDialog: Boolean = false,
    showLogoutConfirmation: Boolean = false,
    onEditProfileClick: () -> Unit = {},
    onUpdateProfile: (String, Int) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onConfirmLogout: () -> Unit = {},
    onCancelLogout: () -> Unit = {},
    onDismissProfileDialog: () -> Unit = {},
    createGameRoom: () -> Unit = {},
    onJoinRoom: (String) -> Unit = {},
    onViewPastGames: () -> Unit = {}, // Handler for past games
    onViewPublicGames: () -> Unit = {}, // Handler for public games
    snackbarHostState: SnackbarHostState = SnackbarHostState()
) {
    val showJoinOptionDialog = remember { mutableStateOf(false) }
    val showJoinRoomDialog = remember { mutableStateOf(false) }
    val showSoundSettingsDialog = remember { mutableStateOf(false) }
    
    // Sound settings view model
    val soundSettingsViewModel: SoundSettingsViewModel = koinViewModel()
    val backgroundMusicEnabled by soundSettingsViewModel.backgroundMusicEnabled.collectAsState()
    val soundEffectsEnabled by soundSettingsViewModel.soundEffectsEnabled.collectAsState()
    val musicVolume by soundSettingsViewModel.musicVolume.collectAsState()
    val soundEffectsVolume by soundSettingsViewModel.soundEffectsVolume.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile button
                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .clickable(onClick = onEditProfileClick)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with 3D effect
                        Box(
                            modifier = Modifier.size(40.dp)
                        ) {
                            // Shadow Layer (bottom)
                            Image(
                                modifier = Modifier
                                    .size(40.dp)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .clip(CircleShape)
                                    .background(GameBlack, CircleShape),
                                painter = painterResource(
                                    HomeViewModel.getProfilePictureById(
                                        avatarId
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
                                    .clip(CircleShape),
                                painter = painterResource(
                                    HomeViewModel.getProfilePictureById(
                                        avatarId
                                    )
                                ),
                                contentDescription = "Profile Picture"
                            )
                        }

                        Column {
                            Text(
                                text = userName,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontFamily = patrickHandFont(),
                                    fontWeight = FontWeight.Bold,
                                    color = GameBlack
                                )
                            )

                            Text(
                                text = "Tap to edit",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontFamily = patrickHandFont(),
                                    color = GameBlack.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                    
                    // Music settings button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                    ) {
                        // Shadow (bottom layer)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .offset(x = 0.5.dp, y = 0.5.dp)
                                .clip(CircleShape)
                                .background(GameBlack.copy(alpha = 0.3f))
                                .align(Alignment.BottomEnd)
                        )
                        
                        // Button (top layer)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GameWhite)
                                .border(1.5.dp, GameBlack, CircleShape)
                                .clickable { showSoundSettingsDialog.value = true }
                                .align(Alignment.TopStart),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.music),
                                contentDescription = "Sound Settings",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Title
                GamifiedTitle(text = "Hunt.it")

                Spacer(modifier = Modifier.height(16.dp))

                // Game description
                Text(
                    text = "The social game where friends compete to find and snap items before time runs out!",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                // Game Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Join Game Button
                    GamifiedActionButton(
                        text = "JOIN A GAME ROOM",
                        iconRes = Res.drawable.people,
                        bgColor = MainYellow,
                        onClick = {
                            showJoinOptionDialog.value = true
                        }
                    )

                    // Create Game Button
                    GamifiedActionButton(
                        text = "CREATE A GAME ROOM",
                        iconRes = Res.drawable.add,
                        bgColor = GameWhite,
                        onClick = createGameRoom
                    )

                     GamifiedActionButton(
                         text = "VIEW PAST GAMES",
                         iconRes = Res.drawable.history,
                         bgColor = GameGrey,
                         onClick = onViewPastGames
                     )
                }

                Spacer(modifier = Modifier.weight(0.5f))

                // Footer
                Box(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "ghost.dev",
                        fontSize = 14.sp,
                        color = GameBlack.copy(alpha = 0.4f),
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            if (showJoinOptionDialog.value) {
                JoinOptionDialog(
                    onDismiss = { showJoinOptionDialog.value = false },
                    onJoinByCode = { showJoinRoomDialog.value = true },
                    onBrowsePublicGames = onViewPublicGames
                )
            }
            
            if (showJoinRoomDialog.value) {
                JoinRoomDialog(
                    onDismiss = { showJoinRoomDialog.value = false },
                    onJoin = { roomCode ->
                        showJoinRoomDialog.value = false
                        onJoinRoom(roomCode)
                    }
                )
            }

            if (showProfileDialog) {
                EditProfileDialog(
                    user = User(
                        id = "",
                        email = "",
                        displayName = userName,
                        avatarId = avatarId,
                        totalGamesPlayed = 0
                    ),
                    isLoading = isLoading,
                    onDismiss = onDismissProfileDialog,
                    onSave = onUpdateProfile,
                    onLogout = onLogout
                )
            }

            // Show logout confirmation dialog if requested
            if (showLogoutConfirmation) {
                LogoutConfirmationDialog(
                    isLoading = isLoggingOut,
                    onDismiss = onCancelLogout,
                    onConfirmLogout = onConfirmLogout
                )
            }
            
            // Sound settings dialog
            if (showSoundSettingsDialog.value) {
                SoundSettingsDialog(
                    onDismiss = { showSoundSettingsDialog.value = false },
                    backgroundMusicEnabled = backgroundMusicEnabled,
                    soundEffectsEnabled = soundEffectsEnabled,
                    musicVolume = musicVolume,
                    soundEffectsVolume = soundEffectsVolume,
                    onBackgroundMusicToggled = soundSettingsViewModel::setBackgroundMusicEnabled,
                    onSoundEffectsToggled = soundSettingsViewModel::setSoundEffectsEnabled,
                    onMusicVolumeChanged = soundSettingsViewModel::setMusicVolume,
                    onSoundEffectsVolumeChanged = soundSettingsViewModel::setSoundEffectsVolume
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
fun GamifiedTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Shadow layer
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 42.sp,
                    fontFamily = testSohneFont(),
                    fontWeight = FontWeight.Bold,
                    color = GameBlack.copy(alpha = 0.2f)
                ),
                modifier = Modifier.offset(x = 3.dp, y = 3.dp)
            )

            // Main text layer
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 42.sp,
                    fontFamily = testSohneFont(),
                    fontWeight = FontWeight.Bold,
                    color = GameBlack
                )
            )
        }
    }
}

@Composable
fun GamifiedActionButton(
    text: String,
    iconRes: DrawableResource,
    bgColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Safely access audio player without forcing a return
    val audioPlayer = LocalAudioPlayer.current

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f), // Bouncy spring
        label = "ButtonOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp) // Total height reserved
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple, using custom animation
                onClick = {
                    // Safe sound playback that won't crash if it fails
                    safeCall {
                        audioPlayer?.playSound("files/button_click.mp3")
                    }
                    
                    // Execute the main click handler safely
                    safeCall { onClick() }
                }
            )
    ) {
        // Shadow Layer (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp) // Match button height
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(54.dp)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon if provided
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(12.dp))

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