package com.safebeauty.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.util.AnnouncementPrefs

/**
 * Shows the newest admin announcement as a one-time popup dialog. Once the user
 * dismisses it, its id is remembered on-device so it never pops up again — the
 * persistent banner still keeps it visible. Drop this into any dashboard that
 * already observes [broadcasts].
 */
@Composable
fun AnnouncementPopup(broadcasts: List<BroadcastDocument>) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val newest  = broadcasts.maxByOrNull { it.createdAt }
    var shown by remember { mutableStateOf<BroadcastDocument?>(null) }

    LaunchedEffect(newest?.id) {
        if (newest != null && newest.id.isNotBlank() &&
            newest.id != AnnouncementPrefs.lastSeenId(context)
        ) {
            shown = newest
        }
    }

    shown?.let { b ->
        val dismiss = {
            AnnouncementPrefs.markSeen(context, b.id)
            shown = null
        }
        AlertDialog(
            onDismissRequest = dismiss,
            icon  = { Icon(Icons.Default.Campaign, null, tint = RoseGold, modifier = Modifier.size(38.dp)) },
            title = { Text(strings.announcementTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
            text  = { Text(b.message, fontSize = 14.sp, color = Color(0xFF555555)) },
            confirmButton = {
                Button(onClick = dismiss, colors = ButtonDefaults.buttonColors(containerColor = RoseGold)) {
                    Text(strings.ok, color = Color.White)
                }
            },
            containerColor = ElegantCream
        )
    }
}
