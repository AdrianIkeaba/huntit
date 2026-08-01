package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.theme.MainRed
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont

private const val PRIVACY_POLICY_URL =
    "https://huntit-privacy-safety.adrianikeaba.chatgpt.site/privacy/"
private const val ACCOUNT_DELETION_URL =
    "https://huntit-privacy-safety.adrianikeaba.chatgpt.site/delete-account/"

@Composable
fun PrivacyAndSafetyDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "PRIVACY & SAFETY",
                fontFamily = testSohneFont(),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PolicySection(
                    title = "Photos",
                    body = "Challenge photos are private, sent to Google Gemini for automated verification, and deleted from Hunt.it storage after the verification attempt."
                )
                PolicySection(
                    title = "Account and game data",
                    body = "We store your email, display name, avatar, room activity, scores, and safety actions. Finished games are normally removed after 7 days."
                )
                PolicySection(
                    title = "Reports",
                    body = "Safety reports are visible only to moderators and retained for up to 24 months. Account identifiers are removed when an account is deleted."
                )
                PolicySection(
                    title = "Your controls",
                    body = "You can report or block players from the lobby. Deleting your account permanently removes your account, profile, hosted games, participation, scores, and stored photos."
                )
                TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                    Text("OPEN FULL PRIVACY POLICY")
                }
                TextButton(onClick = { uriHandler.openUri(ACCOUNT_DELETION_URL) }) {
                    Text("ONLINE DELETION REQUEST")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE")
            }
        }
    )
}

@Composable
fun DeleteAccountDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var confirmation by remember { mutableStateOf("") }
    val canDelete = confirmation.trim().uppercase() == "DELETE" && !isLoading

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "DELETE ACCOUNT?",
                fontFamily = testSohneFont(),
                fontWeight = FontWeight.Bold,
                color = MainRed
            )
        },
        text = {
            Column {
                Text(
                    text = "This permanently deletes your profile, hosted games, participation, scores, and uploaded photos. Safety reports may remain without your account identifiers for up to 24 months.",
                    fontFamily = patrickHandFont(),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Type DELETE to confirm",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(12) },
                    enabled = !isLoading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("CANCEL")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canDelete
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                    Text(if (isLoading) "DELETING..." else "DELETE")
                }
            }
        }
    )
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontFamily = testSohneFont()
        )
        Text(
            text = body,
            fontFamily = patrickHandFont(),
            fontSize = 16.sp
        )
    }
}
