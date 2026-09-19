package com.safebeauty.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.ui.theme.AvailableGreen
import com.safebeauty.app.ui.theme.DangerRed
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextStrong

/** The reasons the server accepts, in the order they are offered. */
val REPORT_REASONS = listOf("HARASSMENT", "NUDITY", "HATE", "SCAM", "SPAM", "OTHER")

/**
 * Reporting a post, a story, a comment or a review — one sheet for all four,
 * because the decision is the same every time: what is wrong with it, why if
 * she wants to say, and whether she also wants to stop seeing this person.
 *
 * Blocking is offered here rather than only from a separate screen because this
 * is the moment she wants it: she is looking at the thing that upset her. Play
 * asks for both mechanisms, and putting them one tap apart is what makes the
 * second one get used.
 *
 * [authorId] blank means the content carries no author, so only reporting is
 * offered — there would be nobody to block.
 */
@Composable
fun ReportSheetContent(
    authorId: String,
    onSubmit: (reason: String, note: String, alsoBlock: Boolean) -> Unit,
    sending: Boolean,
    sent: Boolean,
    /** Blank while nothing has gone wrong. */
    error: String,
) {
    val strings = LocalStrings.current
    var reason by remember { mutableStateOf(REPORT_REASONS.first()) }
    var note by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(false) }

    val labels = mapOf(
        "HARASSMENT" to strings.reportHarassment,
        "NUDITY" to strings.reportNudity,
        "HATE" to strings.reportHate,
        "SCAM" to strings.reportScam,
        "SPAM" to strings.reportSpam,
        "OTHER" to strings.reportOther,
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
    ) {
        if (sent) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen,
                     modifier = Modifier.size(38.dp))
                Text(strings.reportSentTitle, fontWeight = FontWeight.Bold,
                     fontSize = 16.sp, color = DeepRose)
                // The 24-hour commitment, said to her rather than only promised
                // to the store reviewer. It is also the honest answer to
                // "what happens now".
                Text(strings.reportSentBody, fontSize = 13.sp, color = TextMuted)
            }
            return@Column
        }

        Text(strings.reportTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose)
        Spacer(Modifier.height(14.dp))
        Text(strings.reportWhy, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(DashboardSurface)
        ) {
            REPORT_REASONS.forEachIndexed { index, key ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { reason = key }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(labels[key] ?: key, fontSize = 14.sp, color = TextStrong,
                         modifier = Modifier.weight(1f))
                    if (reason == key) {
                        Icon(Icons.Default.Check, null, tint = RoseGold,
                             modifier = Modifier.size(17.dp))
                    }
                }
                if (index != REPORT_REASONS.lastIndex) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 500) note = it },
            placeholder = { Text(strings.reportNotePlaceholder, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            minLines = 2,
            maxLines = 5,
        )

        if (authorId.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp)).background(DashboardSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.reportAlsoBlock, fontSize = 14.sp, color = TextStrong,
                         fontWeight = FontWeight.Medium)
                    Text(strings.reportAlsoBlockHint, fontSize = 11.sp, color = TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = alsoBlock,
                    onCheckedChange = { alsoBlock = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = RoseGold)
                )
            }
        }

        // Every failure used to be silent: the comment's author deletes it
        // while the sheet is open, the callable throws not-found, the spinner
        // stops and the form sits there unchanged. The one control the store
        // asks for by name looked broken whenever it failed.
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                error, fontSize = 12.5.sp, color = DangerRed,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(DangerRed.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(reason, note.trim(), alsoBlock) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(strings.reportSubmit, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
