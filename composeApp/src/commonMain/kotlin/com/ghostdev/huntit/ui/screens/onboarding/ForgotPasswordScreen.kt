package com.ghostdev.huntit.ui.screens.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.mail
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameInputBg = Color(0xFFF7F7F7)

@Composable
fun ForgotPasswordScreen(
    innerPadding: PaddingValues,
    navigateBack: () -> Unit = {},
    viewModel: ForgotPasswordViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            val isUserFriendlyError = it.contains("Please enter") || 
                                     it.contains("valid email") ||
                                     it.contains("No account found")
                                   
            // Only convert technical errors to user-friendly message
            val displayError = if (isUserFriendlyError) {
                it // Use validation error as-is
            } else {
                it.toUserFriendlyError("Something went wrong with your password reset request.")
            }
            
            snackbarHostState.showSnackbar(if (isUserFriendlyError) it else "Error: $displayError")
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSuccessShown()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetState() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Determine which content to show
                if (uiState.resetEmailSent) {
                    ResetEmailSentContent(
                        email = uiState.email,
                        onBackToLoginClick = navigateBack,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    ResetPasswordInputContent(
                        email = uiState.email,
                        isLoading = uiState.isLoading,
                        onEmailChange = viewModel::onEmailChange,
                        onResetClick = viewModel::onResetClick,
                        modifier = Modifier.align(Alignment.Center)
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

        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}


@Composable
private fun ResetPasswordInputContent(
    email: String,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        // Header
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Reset your\nPassword.",
                fontSize = 28.sp,
                textAlign = TextAlign.Start,
                color = GameBlack,
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 6.dp)
                    .background(GameBlack, RoundedCornerShape(20.dp))
            )
            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameWhite, RoundedCornerShape(20.dp))
                    .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column {
                    GameLabel("ENTER YOUR EMAIL")
                    Spacer(Modifier.height(8.dp))
                    GameTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        enabled = !isLoading,
                        placeholder = "hunter@ghost.dev",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        )
                    )

                    Spacer(Modifier.height(24.dp))

                    GamifiedButton(
                        text = "SEND RESET LINK",
                        isLoading = isLoading,
                        onClick = onResetClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetEmailSentContent(
    email: String,
    onBackToLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Check your\nInbox!",
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = GameBlack,
            style = TextStyle(
                fontFamily = testSohneFont(),
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 6.dp)
                    .background(GameBlack, RoundedCornerShape(20.dp))
            )
            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameWhite, RoundedCornerShape(20.dp))
                    .border(2.dp, GameBlack, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MainYellow)
                            .border(2.dp, GameBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.mail),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "We sent instructions to:",
                        style = TextStyle(fontFamily = patrickHandFont(), fontSize = 16.sp),
                        color = GameBlack.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = email,
                        style = TextStyle(
                            fontFamily = testSohneFont(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GameBlack,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    GamifiedButton(
                        text = "BACK TO LOGIN",
                        isLoading = false,
                        onClick = onBackToLoginClick
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
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
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(GameInputBg, shape)
                    .border(2.dp, GameBlack.copy(alpha = 0.1f), shape)
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