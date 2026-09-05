package com.safebeauty.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.viewmodel.ForceUpdateViewModel

/**
 * "There is a newer version" — said once, and dismissible.
 *
 * The app could already block an outdated build, and had nothing between that
 * and silence: a customer on last month's version was either stopped at a
 * dialog she could not close or told nothing at all. Most updates are neither
 * emergency nor secret, and blocking someone from booking a haircut because a
 * nicer version exists is not a kindness.
 *
 * The line about what changed comes from the console as one string per
 * language, so it says something true about this release rather than a fixed
 * sentence that ages into a lie.
 */
@Composable
fun UpdateAvailableBanner(
    info: ForceUpdateViewModel.UpdateAvailable,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val note = info.whatsNew[strings.language.code].orEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BlushPink.copy(alpha = 0.55f))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(Icons.Default.SystemUpdate, null, tint = RoseGold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (info.versionName.isBlank()) strings.updateAvailableTitle
                else "${strings.updateAvailableTitle} · ${info.versionName}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DeepRose,
            )
            Text(
                // What the console said about this release, or the generic line
                // when it said nothing.
                note.ifBlank { strings.updateAvailableBody },
                fontSize = 11.sp, lineHeight = 16.sp, color = DeepRose.copy(alpha = 0.8f),
            )
        }
        if (info.updateUrl.isNotBlank()) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.updateUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) {
                Text(strings.updateNow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RoseGold)
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, strings.close, tint = RoseGold, modifier = Modifier.size(16.dp))
        }
    }
}
