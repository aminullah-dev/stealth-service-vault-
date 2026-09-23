package com.safebeauty.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.util.launchUpdate

/**
 * Non-dismissible dialog shown when the installed app version is below
 * the minimum required version stored in Firestore.
 *
 * The dialog has no cancel/dismiss button so the user cannot bypass it.
 * Tapping "Update Now" opens the Play Store listing via [launchUpdate], which
 * falls back to a browser and then to a visible message rather than crashing
 * if nothing can handle the link.
 */
@Composable
fun ForceUpdateDialog(
    minVersionName: String,
    updateUrl: String,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* intentionally empty — not dismissible */ },
        title = {
            Text(
                text       = strings.forceUpdateTitle,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            val versionSuffix = if (minVersionName.isNotBlank()) " ($minVersionName)" else ""
            Text("${strings.forceUpdateMessage}$versionSuffix")
        },
        confirmButton = {
            Button(
                modifier  = Modifier.fillMaxWidth(),
                onClick   = {
                    launchUpdate(context, updateUrl, strings.updateOpenFailed)
                },
            ) {
                Text(strings.forceUpdateButton)
            }
        },
    )
}
