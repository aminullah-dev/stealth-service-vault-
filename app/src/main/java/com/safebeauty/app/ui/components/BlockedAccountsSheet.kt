package com.safebeauty.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextStrong

/**
 * Who she has blocked, and the way back.
 *
 * The block is written server-side, so reinstalling the app does not clear it —
 * without this screen a mis-tap on "also block this account" while reporting a
 * comment hid that person's words from her permanently, with nothing anywhere
 * to lift it.
 *
 * Ids rather than names: a customer may not read another customer's user
 * document, and a name copied at block time goes stale. The id is ugly and
 * distinguishable, which is what this list needs — she blocked at most a
 * handful of people and each row has an Unblock beside it.
 */
@Composable
fun BlockedAccountsSheetContent(
    blocked: Set<String>,
    onUnblock: (String) -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
    ) {
        Text(strings.blockedTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose)
        Spacer(Modifier.height(12.dp))

        if (blocked.isEmpty()) {
            Text(strings.blockedEmpty, fontSize = 13.sp, color = TextMuted)
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            blocked.sorted().forEachIndexed { index, id ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        id, fontSize = 12.5.sp, color = TextStrong,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onUnblock(id) }) {
                        Text(strings.unblockAction, fontSize = 13.sp, color = RoseGold)
                    }
                }
                if (index != blocked.size - 1) HorizontalDivider(color = DashboardSurface)
            }
        }
    }
}
