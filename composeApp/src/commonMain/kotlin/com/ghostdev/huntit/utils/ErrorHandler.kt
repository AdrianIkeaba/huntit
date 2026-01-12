package com.ghostdev.huntit.utils

import com.ghostdev.huntit.utils.SnackbarManager

/**
 * Utility class for standardizing error message handling across the app.
 * Instead of showing raw API errors or stack traces to users, this class helps
 * convert technical errors into user-friendly messages.
 */
object ErrorHandler {
    /**
     * Processes an error and returns a user-friendly message.
     * 
     * @param error The original error or exception
     * @param defaultMessage A default message to show if error can't be categorized
     * @param showInSnackbar Whether to also display the error in a snackbar
     * @return A user-friendly error message
     */
    fun handleError(
        error: Any?, 
        defaultMessage: String = "Something went wrong. Please try again later.",
        showInSnackbar: Boolean = false
    ): String {
        val friendlyMessage = when {
            // Request timeout errors
            error.toString().contains("Request timeout") ||
            error.toString().contains("request_timeout") ->
                "The request took too long to complete. Please check your connection and try again."
                
            // Network connectivity errors
            error.toString().contains("Connection") || 
            error.toString().contains("network") || 
            error.toString().contains("timeout") || 
            error.toString().contains("SocketTimeoutException") ||
            error.toString().contains("ConnectException") -> 
                "Check your internet connection and try again."
            
            // Authentication errors
            error.toString().contains("401") || 
            error.toString().contains("auth") || 
            error.toString().contains("token") ||
            error.toString().contains("permission") ||
            error.toString().contains("unauthorized") -> 
                "You need to sign in again."
            
            // Resource not found errors
            error.toString().contains("404") ||
            error.toString().contains("not found") -> 
                "We couldn't find what you were looking for."
            
            // Server errors
            error.toString().contains("500") || 
            error.toString().contains("server") -> 
                "Our servers are having trouble right now. Please try again later."
                
            // Rate limiting or quota errors  
            error.toString().contains("429") || 
            error.toString().contains("quota") || 
            error.toString().contains("rate") -> 
                "Whoa! Slow down a bit. You're doing that too quickly."
                
            // For any other errors, use the default message
            else -> defaultMessage
        }
        
        // If requested, also show in snackbar
        if (showInSnackbar) {
            SnackbarManager.instance.showMessage(friendlyMessage, isError = true)
        }
        
        return friendlyMessage
    }
    
    /**
     * Helper function to handle exceptions specifically
     */
    fun handleException(
        exception: Exception?,
        defaultMessage: String = "Something went wrong. Please try again later.",
        showInSnackbar: Boolean = false
    ): String {
        return handleError(exception?.message ?: exception, defaultMessage, showInSnackbar)
    }
}

/**
 * Extension function to get a user-friendly error message from any object
 */
fun Any?.toUserFriendlyError(defaultMessage: String = "Something went wrong. Please try again later."): String {
    return ErrorHandler.handleError(this, defaultMessage)
}

/**
 * Extension function to get a user-friendly error message from an exception
 */
fun Exception?.toUserFriendlyError(defaultMessage: String = "Something went wrong. Please try again later."): String {
    return ErrorHandler.handleException(this, defaultMessage)
}