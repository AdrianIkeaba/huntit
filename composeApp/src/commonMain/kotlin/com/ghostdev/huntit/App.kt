package com.ghostdev.huntit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.ghostdev.huntit.navigation.AppNavHost
import com.ghostdev.huntit.ui.theme.patrickHandTypography
import com.ghostdev.huntit.utils.DeepLinkHandler
import com.ghostdev.huntit.utils.LocalDeepLinkHandler
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Use the platform-independent DeepLinkHandler
    val deepLinkHandler = DeepLinkHandler.instance



    CompositionLocalProvider(LocalDeepLinkHandler provides deepLinkHandler) {
        MaterialTheme(
            typography = patrickHandTypography()
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
            ) { padding ->
                AppNavHost(
                    innerPadding = padding,
                    deepLinkAccessToken = deepLinkHandler.accessToken.value
                )
            }
        }
    }
}