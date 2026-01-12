package com.ghostdev.huntit.ui.screens.onboarding

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.components.AnimatedBackground
import com.ghostdev.huntit.ui.components.StyledSnackbarHost
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.ghostdev.huntit.utils.toUserFriendlyError

private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameInputBg = Color(0xFFF7F7F7)

@Composable
fun UserNameScreen(
    innerPadding: PaddingValues,
    navigateToHome: () -> Unit,
    viewModel: UserNameViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            val isValidationError = it.contains("Please enter") || 
                                   it.contains("Name must be") ||
                                   it.contains("Name is too long")

            val displayError = if (isValidationError) {
                it
            } else {
                it.toUserFriendlyError("Something went wrong while saving your username.")
            }
            
            snackbarHostState.showSnackbar(if (isValidationError) it else "Error: $displayError")
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.shouldNavigateToHome) {
        if (uiState.shouldNavigateToHome) {
            viewModel.onNavigationComplete()
            navigateToHome()
        }
    }

    UserNameComponent(
        innerPadding = innerPadding,
        name = uiState.name,
        isLoading = uiState.isLoading,
        onNameChange = viewModel::onNameChange,
        onDoneClick = viewModel::onDoneClick,
        snackbarHostState = snackbarHostState
    )
}

@Composable
@Preview
private fun UserNameComponent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    name: String = "",
    isLoading: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onDoneClick: () -> Unit = {},
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
                        text = "What should we\ncall you?",
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

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally)
                            .height(IntrinsicSize.Min)
                    ) {
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
                                GameLabel("YOUR NICKNAME")
                                Spacer(Modifier.height(8.dp))
                                GameTextField(
                                    value = name,
                                    onValueChange = onNameChange,
                                    enabled = !isLoading,
                                    placeholder = "GhostHunter...",
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done
                                    )
                                )

                                Spacer(Modifier.height(24.dp))

                                GamifiedButton(
                                    text = "LET'S HUNT",
                                    isLoading = isLoading,
                                    onClick = onDoneClick
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