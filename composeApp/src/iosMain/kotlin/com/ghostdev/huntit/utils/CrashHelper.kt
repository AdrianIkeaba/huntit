package com.ghostdev.huntit.utils

import platform.Foundation.NSLog
import kotlin.experimental.ExperimentalNativeApi

/**
 * Initializes a global crash handler for uncaught Kotlin exceptions on iOS
 * This helps diagnose issues by logging detailed crash information before the app terminates
 */
@OptIn(ExperimentalNativeApi::class)
fun initializeCrashLogger() {
    setUnhandledExceptionHook { throwable ->
        // 1. Get the message
        val message = throwable.message ?: "Unknown Kotlin Exception"
        
        // 2. Format the stack trace
        val stackTrace = throwable.getStackTrace().joinToString("\n")
        
        // 3. Get the cause if any
        val cause = throwable.cause?.let { 
            "Caused by: ${it.message}\n${it.getStackTrace().joinToString("\n")}" 
        } ?: "No cause specified"
        
        // 4. Print to Apple System Log (visible in Xcode/Console.app)
        // We use NSLog because println() sometimes gets cut off during a hard crash
        NSLog("💥 KOTLIN CRASH DETECTED 💥")
        NSLog("Exception type: ${throwable::class.simpleName}")
        NSLog("Message: $message")
        NSLog("Trace: $stackTrace")
        NSLog("Cause: $cause")
        
        // Additional information that might be helpful
        NSLog("Current time: ${platform.Foundation.NSDate()}")
    }
}