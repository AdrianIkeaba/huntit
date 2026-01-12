# Error Handling Implementation Examples

This document provides examples of how to implement consistent error handling across different screen types in the Huntit app.

## 1. Screen with SnackbarHostState

Most screens in the app use a SnackbarHostState to display error messages. Here's the pattern to follow:

```kotlin
import com.ghostdev.huntit.utils.toUserFriendlyError

@Composable
fun MyScreen(...) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error message with user-friendly text
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Convert technical error to user-friendly message
            val userFriendlyError = it.toUserFriendlyError("Something went wrong. Please try again.")
            snackbarHostState.showSnackbar("Error: $userFriendlyError")
            viewModel.clearErrorMessage() // Clear after showing
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Screen content...

        // Always show snackbar host to handle error messages
        StyledSnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
```

## 2. Screen with Custom Error Cards

For screens where errors prevent normal operation, use a dedicated error card component:

```kotlin
@Composable
fun MyDetailScreen(...) {
    // Get the current state
    val submissionState = viewModel.getState()
    
    if (submissionState is SubmissionState.Error) {
        // Handle unexpected errors with the generic error card
        GenericErrorCard(
            errorMessage = submissionState.message.toUserFriendlyError("Something went wrong."),
            onReturnToHome = { navigateToHome() },
            onRetry = if (submissionState.canRetry) {
                { viewModel.retry() }
            } else null
        )
    } else {
        // Normal screen content
    }
}
```

## 3. Preserving Specific Error Messages

Sometimes, you'll want to provide very specific error messages for certain scenarios. In these cases, you can check for known error messages before applying the generic conversion:

```kotlin
// In ViewModel - provide specific, actionable error messages for known cases
val errorMessage = when {
    error.message?.contains("2 players") == true -> 
        "At least 2 players are needed to start a game. Share the room code to invite more players!"
        
    error.message?.contains("duplicate key") == true ->
        "Game challenges already exist. Please restart the app and try again."
        
    else -> "Failed to start game: ${error.message ?: "Unknown error"}"
}

// In Composable - preserve specific error messages
LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let {
        // Check if this is a known specific error message that should be preserved
        val userFriendlyError = if (it.contains("At least 2 players are needed")) {
            // This is already a user-friendly message from the ViewModel, keep it as is
            it
        } else {
            // Otherwise convert the technical error to a user-friendly message
            it.toUserFriendlyError("Something went wrong.")
        }
        snackbarHostState.showSnackbar("Error: $userFriendlyError")
    }
}
```

## 4. Camera/Permission Error Handling

When handling camera or other permissions, provide clear, actionable error messages:

```kotlin
when (permissionStatus) {
    PermissionStatus.GRANTED -> {
        try {
            // Action when permission is granted
            doSomething()
        } catch (e: Exception) {
            scope.launch {
                val userFriendlyError = e.message.toUserFriendlyError(
                    "Cannot access camera. Please try again."
                )
                snackbarHostState.showSnackbar("Error: $userFriendlyError")
            }
        }
    }
    PermissionStatus.DENIED -> scope.launch {
        snackbarHostState.showSnackbar(
            "Camera permission is required. Please enable it in your device settings."
        )
    }
}
```

## 5. Network-Related Screens

For screens that depend on network connectivity, provide specific error messages:

```kotlin
// In ViewModel
try {
    // API call
    repository.fetchData()
} catch (e: Exception) {
    _state.update { 
        it.copy(
            errorMessage = e.message ?: "Unknown error"
        )
    }
}

// In Composable
LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let {
        val userFriendlyError = it.toUserFriendlyError(
            "Something went wrong while loading data. Check your connection and try again."
        )
        snackbarHostState.showSnackbar("Error: $userFriendlyError")
    }
}
```

## 6. Form Validation Errors

For forms with validation, use specific error messages:

```kotlin
// Password field validation
if (password.length < 8) {
    showError("Password must be at least 8 characters long")
    return
}

// Email validation error
if (!email.contains('@')) {
    showError("Please enter a valid email address")
    return
}
```

## 7. Properly Clearing Error State After Display

To prevent error messages from reappearing when a user navigates between screens, always clear the error state after displaying the message:

```kotlin
// In ViewModel:
fun clearError() {
    _state.update { it.copy(error = null) }
}

// In Screen Composable:
@Composable
fun MyScreen(..., viewModel: MyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    
    // Pass the error handling to component
    MyComponent(
        error = state.error,
        onErrorShown = { viewModel.clearError() }
    )
}

// In Component Composable:
@Composable
fun MyComponent(..., error: String?, onErrorShown: () -> Unit) {
    // Display error message
    LaunchedEffect(error) {
        if (error != null) {
            // Show the error
            snackbarHostState.showSnackbar("Error: $error")
            
            // Clear the error state to prevent it from reappearing
            // when navigating back to this screen
            onErrorShown()
        }
    }
}
```

## Best Practices

1. **Be context-aware** - Customize default error messages based on the screen or action
2. **Clear error state** - Always clear error messages after showing them to avoid them reappearing
3. **Retry options** - When appropriate, provide a way to retry the failed operation
4. **Check for common cases** - Network issues are the most frequent error source - handle them well
5. **Use centralized error handling** - Use the `ErrorHandler` utility consistently
6. **Preserve specific messages** - Don't overwrite already user-friendly, specific messages with generic ones
7. **Pass clearing callbacks** - When using components, pass callbacks to clear errors after display