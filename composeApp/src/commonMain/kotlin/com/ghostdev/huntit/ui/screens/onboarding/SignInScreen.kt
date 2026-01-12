package com.ghostdev.huntit.ui.screens.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

private val GameBlack = Color(0xFF1A1A1A)
private val GameGrey = Color(0xFFE5E5E5)
private val GameWhite = Color(0xFFFFFFFF)
private val GameInputBg = Color(0xFFF7F7F7)
private val GameShadowHeight = 4.dp

@Composable
fun SignInScreen(
    innerPadding: PaddingValues,
    navigateToResetPassword: () -> Unit,
    navigateToUserName: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToLobby: (String) -> Unit = {},
    navigateToGame: (String) -> Unit = {},
    navigateToLeaderboard: (String) -> Unit = {},
    showGameEndedMessage: () -> Unit = {},
    viewModel: SignInViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Check if it's a validation error (these are already user-friendly)
            val isValidationError = it.contains("Please enter") || 
                                   it.contains("Password must be") ||
                                   it.contains("valid email")
                                   
            // Only convert technical errors to user-friendly message
            val displayError = if (isValidationError) {
                it // Use validation error as-is
            } else {
                it.toUserFriendlyError("Something went wrong during sign in.")
            }
            
            snackbarHostState.showSnackbar(if (isValidationError) it else "Error: $displayError")
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.shouldNavigateToUserName) {
        if (uiState.shouldNavigateToUserName) {
            viewModel.onNavigationComplete()
            navigateToUserName()
        }
    }

    LaunchedEffect(uiState.shouldNavigateToHome) {
        if (uiState.shouldNavigateToHome) {
            viewModel.onNavigationComplete()
            navigateToHome()
        }
    }

    LaunchedEffect(uiState.shouldNavigateToLobby, uiState.activeGameRoomCode) {
        if (uiState.shouldNavigateToLobby && uiState.activeGameRoomCode != null) {
            viewModel.onNavigationComplete()
            navigateToLobby(uiState.activeGameRoomCode!!)
        }
    }

    LaunchedEffect(uiState.shouldNavigateToGame, uiState.activeGameRoomCode) {
        if (uiState.shouldNavigateToGame && uiState.activeGameRoomCode != null) {
            viewModel.onNavigationComplete()
            navigateToGame(uiState.activeGameRoomCode!!)
        }
    }

    LaunchedEffect(uiState.shouldNavigateToLeaderboard) {
        if (uiState.shouldNavigateToLeaderboard) {
            viewModel.onNavigationComplete()
            navigateToLeaderboard(uiState.activeGameRoomCode!!)
        }
    }

    LaunchedEffect(uiState.shouldShowGameEndedMessage) {
        if (uiState.shouldShowGameEndedMessage) {
            showGameEndedMessage()
        }
    }

    SignInComponent(
        innerPadding = innerPadding,
        email = uiState.email,
        password = uiState.password,
        isLoading = uiState.isLoading,
        showCreateAccountDialog = uiState.showCreateAccountDialog,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onResetPasswordClick = navigateToResetPassword,
        onContinueClick = viewModel::onContinueClick,
        onDismissCreateAccountDialog = viewModel::onDismissCreateAccountDialog,
        onConfirmCreateAccount = viewModel::onConfirmCreateAccount,
        snackbarHostState = snackbarHostState
    )
}

@Composable
@Preview
private fun SignInComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    email: String = "",
    password: String = "",
    isLoading: Boolean = false,
    showCreateAccountDialog: Boolean = false,
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onResetPasswordClick: () -> Unit = {},
    onContinueClick: () -> Unit = {},
    onDismissCreateAccountDialog: () -> Unit = {},
    onConfirmCreateAccount: () -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Are you ready\nfor the hunt?",
                        fontSize = 28.sp,
                        textAlign = TextAlign.Start,
                        color = GameBlack,
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Center Content (Card)
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GamifiedCard(
                        email = email,
                        password = password,
                        isLoading = isLoading,
                        onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange,
                        onContinueClick = onContinueClick
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val audioPlayer = LocalAudioPlayer.current
                    Text(
                        text = "FORGOT PASSWORD?",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                audioPlayer?.playSound("files/button_click.mp3")
                                onResetPasswordClick()
                            }
                            .padding(8.dp),
                        color = GameBlack.copy(alpha = 0.6f)
                    )
                }

                // Footer
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
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
        }

        // Create Account Dialog
        if (showCreateAccountDialog) {
            CreateAccountDialog(
                email = email,
                onDismiss = onDismissCreateAccountDialog,
                onConfirm = onConfirmCreateAccount
            )
        }

        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamifiedCard(
    email: String,
    password: String,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContinueClick: () -> Unit
) {
    // Get keyboard controller and focus manager to hide keyboard
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // This creates the "3D" card effect (White card sitting on Black shadow)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Wraps content height
    ) {
        // Shadow Layer (The bottom part)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp) // Push down to create depth
                .background(GameBlack, RoundedCornerShape(20.dp))
        )

        // Content Layer (The top part)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameWhite, RoundedCornerShape(20.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Email Field
                GameLabel(text = "EMAIL")
                Spacer(Modifier.height(8.dp))
                GameTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    enabled = !isLoading,
                    placeholder = "hunter@ghost.dev",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(Modifier.height(20.dp))

                // Password Field
                GameLabel(text = "PASSWORD")
                Spacer(Modifier.height(8.dp))
                GameTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    enabled = !isLoading,
                    isPassword = true,
                    placeholder = "••••••••",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(Modifier.height(32.dp))

                // 3D Action Button
                GamifiedButton(
                    text = "START HUNTING",
                    isLoading = isLoading,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onContinueClick()
                    }
                )
            }
        }
    }
}

/**
 * A custom TextField wrapper that looks like a slot or game input.
 */
@Composable
fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions
) {
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = patrickHandFont(),
            fontSize = 18.sp,
            color = GameBlack
        ),
        keyboardOptions = keyboardOptions,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(GameInputBg, shape)
                    .border(2.dp, GameBlack.copy(alpha = 0.1f), shape) // Subtle border
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 18.sp,
                            color = Color.Gray.copy(alpha = 0.6f)
                        )
                    )
                }
                innerTextField()
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * A Button that physically moves down when pressed (Duolingo style).
 */
@Composable
fun GamifiedButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
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
                indication = null, // Disable default ripple, we use physical movement
                enabled = !isLoading,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            )
    ) {
        // Shadow (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp) // Match button height
                .background(GameBlack, RoundedCornerShape(16.dp))
        )

        // Touchable Button (Moves up and down)
        Box(
            modifier = Modifier
                .offset(y = offsetY) // Moves down when pressed
                .align(Alignment.TopCenter) // Starts at top
                .fillMaxWidth()
                .height(54.dp)
                .background(MainYellow, RoundedCornerShape(16.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = GameBlack,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = text.uppercase(),
                    style = TextStyle(
                        fontFamily = testSohneFont(), // Bold font for button
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
fun GameLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = testSohneFont(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = GameBlack.copy(alpha = 0.6f)
    )
}

/**
 * A gamified dialog asking if the user wants to create a new account.
 */
@Composable
fun CreateAccountDialog(
    email: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Main Dialog Box with 3D effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Shadow Layer (bottom part)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp) // Push down to create 3D effect
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .background(GameBlack, RoundedCornerShape(20.dp))
                    .height(IntrinsicSize.Min)
            )

            // Content Layer (top part)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, GameBlack, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = GameWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = "CREATE ACCOUNT?",
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    // Message
                    Text(
                        text = buildAnnotatedString {
                            append("No account found with ")

                            withStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = GameBlack
                                )
                            ) {
                                append("$email.")
                            }

                            append(" Would you like to create a new account with this email?")
                        },
                        style = TextStyle(
                            fontFamily = patrickHandFont(),
                            fontSize = 16.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = GameBlack
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        DialogButton(
                            text = "CANCEL",
                            bgColor = GameGrey,
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Confirm Button
                        DialogButton(
                            text = "CREATE",
                            bgColor = MainYellow,
                            modifier = Modifier.weight(1f),
                            onClick = onConfirm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Similar to GamifiedButton but smaller and simpler
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val audioPlayer = LocalAudioPlayer.current

    // Animate the vertical offset (pushing down)
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp, // Smaller offset than main button
        animationSpec = spring(dampingRatio = 0.4f),
        label = "DialogButtonOffset"
    )

    Box(
        modifier = modifier
            .height(46.dp) // Smaller than main button
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    audioPlayer?.playSound("files/button_click.mp3")
                    onClick()
                }
            )
    ) {
        // Shadow (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(42.dp)
                .background(GameBlack, RoundedCornerShape(12.dp))
        )

        // Touchable Button (Moves up and down)
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(42.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.5.dp, GameBlack, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = GameBlack
            )
        }
    }
}