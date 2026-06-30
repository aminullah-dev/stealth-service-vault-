package com.security.stealthapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.LocalStrings

/**
 * Shown after login when the account is not APPROVED:
 *  - PENDING  → a provider whose salon is still awaiting admin review.
 *  - REJECTED → shows the admin's rejection reason, if one was given.
 *  - SUSPENDED / other → a generic "account not active" message.
 *
 * This is the UI gate that stops a non-approved provider from landing on a live
 * dashboard. (Provider writes are also blocked server-side by the Firestore
 * rules' isApproved() checks.)
 */
@Composable
fun AccountStatusScreen(
    status: String,
    reason: String = "",
    onBack: () -> Unit,
) {
    val strings   = LocalStrings.current
    val isPending  = status.equals("PENDING", ignoreCase = true)
    val isRejected = status.equals("REJECTED", ignoreCase = true)

    val title = when {
        isPending  -> strings.registrationSubmittedTitle
        isRejected -> strings.rejected
        else       -> strings.accountInactiveTitle
    }
    val message = when {
        isPending  -> strings.salonUnderReviewText
        isRejected -> reason.ifBlank { strings.accountInactiveText }
        else       -> strings.accountInactiveText
    }
    val icon = if (isPending) Icons.Default.HourglassEmpty else Icons.Default.Lock

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElegantCream)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(RoseGold.copy(alpha = 0.12f), RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = RoseGold, modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                color = DeepRose,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = Color(0xFF555555),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RoseGold),
            ) {
                Text(strings.gotIt, color = Color.White)
            }
        }
    }
}
