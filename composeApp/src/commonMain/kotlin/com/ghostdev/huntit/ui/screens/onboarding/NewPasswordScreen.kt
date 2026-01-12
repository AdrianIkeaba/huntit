package com.ghostdev.huntit.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameInputBg = Color(0xFFF7F7F7)
private val ErrorRed = Color(0xFFFF4B4B)

@Composable
fun NewPasswordScreen(
    innerPadding: PaddingValues,
    accessToken: String? = null,
    refreshToken: String? = null,
    expiresIn: Long? = null,
    navigateToLogin: () -> Unit,
    viewModel: NewPasswordViewModel = koinViewModel()
) {
    // Pass the access token to the view model
    LaunchedEffect(accessToken, refreshToken) {
        if (accessToken != null && refreshToken != null) {
            viewModel.setTokens(accessToken, refreshToken, expiresIn)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Check if it's a validation or known user-friendly error
            val isUserFriendlyError = it.contains("Please enter") || 
                                     it.contains("Password must be") ||
                                     it.contains("Passwords don't match") ||
                                     it.contains("Invalid reset link")
            
            // Only convert technical errors to user-friendly message
            val displayError = if (isUserFriendlyError) {
                it // Use validation error as-is
            } else {
                it.toUserFriendlyError("Something went wrong while resetting your password.")
            }
            
            // Show the error message without "Error:" prefix for user-friendly errors
            snackbarHostState.showSnackbar(if (isUserFriendlyError) displayError else "Error: $displayError")
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSuccessShown()
        }
    }

    // Navigate to login screen if password was reset successfully
    LaunchedEffect(uiState.passwordResetSuccess) {
        if (uiState.passwordResetSuccess) {
            delay(1500)
            navigateToLogin()
        }
    }

    NewPasswordComponent(
        innerPadding = innerPadding,
        password = uiState.password,
        confirmPassword = uiState.confirmPassword,
        isLoading = uiState.isLoading,
        passwordMatchError = uiState.passwordMatchError,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onResetPasswordClick = viewModel::onResetPasswordClick,
        snackbarHostState = snackbarHostState
    )
}

@Composable
@Preview
private fun NewPasswordComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    password: String = "",
    confirmPassword: String = "",
    isLoading: Boolean = false,
    passwordMatchError: Boolean = false,
    onPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onResetPasswordClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Set New\nPassword",
                        fontSize = 28.sp,
                        textAlign = TextAlign.Start,
                        color = GameBlack,
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        ),
                    )
                }

                // Centered Content
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 3D Card
                    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        // Shadow Layer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 6.dp)
                                .background(GameBlack, RoundedCornerShape(20.dp))
                        )

                        // Content Layer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GameWhite, RoundedCornerShape(20.dp))
                                .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                                .padding(24.dp)
                        ) {
                            Column {
                                // Password Field
                                GameLabel("NEW PASSWORD")
                                Spacer(Modifier.height(8.dp))
                                GameTextField(
                                    value = password,
                                    onValueChange = onPasswordChange,
                                    enabled = !isLoading,
                                    placeholder = "••••••••",
                                    isPassword = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    )
                                )

                                Spacer(Modifier.height(20.dp))

                                // Confirm Password Field
                                GameLabel("CONFIRM PASSWORD")
                                Spacer(Modifier.height(8.dp))
                                GameTextField(
                                    value = confirmPassword,
                                    onValueChange = onConfirmPasswordChange,
                                    enabled = !isLoading,
                                    placeholder = "••••••••",
                                    isPassword = true,
                                    isError = passwordMatchError, // Visual feedback for error
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    )
                                )

                                // Error Text
                                if (passwordMatchError) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Passwords don't match!",
                                        style = TextStyle(
                                            fontFamily = patrickHandFont(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = ErrorRed
                                    )
                                }

                                Spacer(Modifier.height(24.dp))

                                GamifiedButton(
                                    text = "UPDATE PASSWORD",
                                    isLoading = isLoading,
                                    onClick = onResetPasswordClick
                                )
                            }
                        }
                    }
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

        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ------------------------------------------------------------
// SHARED GAMIFIED COMPONENTS
// ------------------------------------------------------------

@Composable
private fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    isPassword: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isError) ErrorRed else GameBlack.copy(alpha = 0.1f)
    val borderWidth = if (isError) 2.dp else 2.dp

    // Animate border color change
    val animatedBorderColor by animateColorAsState(targetValue = borderColor, label = "BorderColor")

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
                    .border(borderWidth, animatedBorderColor, shape)
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