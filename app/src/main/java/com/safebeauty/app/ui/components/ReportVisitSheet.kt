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
val VISIT_REASONS = listOf(
    "NOT_SERVED", "TURNED_AWAY", "DIFFERENT_SERVICE", "OVERCHARGED", "SAFETY", "OTHER"
)

/**
 * What she says happened at a visit — the mirror of what a salon can already
 * say about her.
 *
 * reportCustomer has let a salon rate its customer, mark her a no-show and flag
 * her for misconduct since this shipped, and an admin can suspend her over it.
 * She had nothing. Meanwhile a booking becomes COMPLETED two hours after its
 * start time whether or not anyone served her, and the commission is booked on
 * that.
 */
@Composable
fun ReportVisitSheetContent(
    salonName: String,
    whenLabel: String,
    onSubmit: (reason: String, note: String) -> Unit,
    sending: Boolean,
    sent: Boolean,
    error: String,
) {
    val strings = LocalStrings.current
    var reason by remember { mutableStateOf(VISIT_REASONS.first()) }
    var note by remember { mutableStateOf("") }

    val labels = mapOf(
        "NOT_SERVED" to strings.visitNotServed,
        "TURNED_AWAY" to strings.visitTurnedAway,
        "DIFFERENT_SERVICE" to strings.visitDifferentService,
        "OVERCHARGED" to strings.visitOvercharged,
        "SAFETY" to strings.visitSafety,
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
                Text(strings.reportVisitSentTitle, fontWeight = FontWeight.Bold,
                     fontSize = 16.sp, color = DeepRose)
                // What happens next, including the money — and that the salon
                // is not told who complained. She has to go back there.
                Text(strings.reportVisitSentBody, fontSize = 13.sp, color = TextMuted)
            }
            return@Column
        }

        Text(strings.reportVisitTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose)
        Spacer(Modifier.height(10.dp))

        // Which visit, so she cannot report the wrong one from a list.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(DashboardSurface).padding(13.dp)
        ) {
            Text(salonName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextStrong)
            Text(whenLabel, fontSize = 12.sp, color = TextMuted)
        }

        Spacer(Modifier.height(14.dp))
        Text(strings.reportVisitWhy, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(DashboardSurface)) {
            VISIT_REASONS.forEachIndexed { index, key ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { reason = key }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(labels[key] ?: key, fontSize = 14.sp, color = TextStrong,
                         modifier = Modifier.weight(1f))
                    if (reason == key) {
                        Icon(Icons.Default.Check, null, tint = RoseGold, modifier = Modifier.size(17.dp))
                    }
                }
                if (index != VISIT_REASONS.lastIndex) {
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

        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                error, fontSize = 12.5.sp, color = DangerRed,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .background(DangerRed.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(reason, note.trim()) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                          color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(strings.reportSubmit, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
