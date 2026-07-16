package com.safebeauty.app.ui.screens

// Finance/payouts admin tab, split out of AdminDashboardScreen.kt to keep that
// file manageable. Same package, so no call-site changes; FinanceTab is internal
// so the main screen can still dispatch to it.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.navigation.Screen
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.PayoutDocument
import com.safebeauty.app.data.firebase.ProviderBalance
import com.safebeauty.app.data.firebase.SalonBadge
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.UserDocument
import com.safebeauty.app.data.firebase.badge
import com.safebeauty.app.ui.theme.AvailableGreen
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DangerRed
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.Gradients
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.NeutralGrey
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.ui.theme.WarmGold
import com.safebeauty.app.viewmodel.AdminViewModel
import com.safebeauty.app.viewmodel.LanguageViewModel
import com.safebeauty.app.viewmodel.SystemStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceTab(
    commissionPercent: Double,
    balances: List<ProviderBalance>,
    payouts: List<PayoutDocument>,
    refundRequests: List<com.safebeauty.app.data.firebase.RefundRequestDocument>,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    val payoutFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    var confirmPayout by remember { mutableStateOf<ProviderBalance?>(null) }
    var confirmRefund by remember { mutableStateOf<com.safebeauty.app.data.firebase.RefundRequestDocument?>(null) }

    // Seed the editable field from the live value the first time it arrives.
    LaunchedEffect(commissionPercent) {
        if (viewModel.commissionInput.isBlank()) {
            viewModel.commissionInput =
                if (commissionPercent % 1.0 == 0.0) commissionPercent.toInt().toString()
                else commissionPercent.toString()
        }
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Commission setting card ───────────────────────────────────────────
        item {
            ElevatedCard(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(DeepRose.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Percent, null, tint = DeepRose, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            strings.financeCommissionTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = DeepRose
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(strings.financeCommissionHint, fontSize = 12.sp, color = RoseGold)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value         = viewModel.commissionInput,
                            onValueChange = { input ->
                                // Allow digits and a single decimal point only.
                                if (input.isEmpty() || input.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                                    viewModel.commissionInput = input
                                }
                            },
                            label       = { Text(strings.financeCommissionLabel, fontSize = 13.sp) },
                            singleLine  = true,
                            modifier    = Modifier.weight(1f),
                            shape       = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                imeAction    = ImeAction.Done
                            ),
                            colors      = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = DeepRose,
                                unfocusedBorderColor = BlushPink,
                                focusedLabelColor    = DeepRose
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.saveCommission() },
                            enabled = viewModel.commissionInput.toDoubleOrNull()?.let { it in 0.0..100.0 } == true,
                            shape   = RoundedCornerShape(12.dp),
                            colors  = ButtonDefaults.buttonColors(containerColor = DeepRose),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(strings.financeSave, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Provider payout ledger ────────────────────────────────────────────
        // Cash-booking commission debt makes owedAmount go negative for a salon
        // (they owe the platform, not the other way around) — split those out
        // into their own read-only warning section instead of mixing them into
        // the payable list below, where a negative amount would render as a
        // (nonsensical) payable balance with an active "Mark Paid" button.
        val owedToProviders  = balances.filter { it.owedAmount > 0 }
        val owedByProviders  = balances.filter { it.owedAmount < 0 }

        item {
            Text(
                strings.financeBalancesTitle,
                fontSize   = 13.sp,
                color      = RoseGold,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(top = 4.dp)
            )
        }

        if (owedToProviders.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Payments, null, tint = BlushPink, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(strings.financeBalancesNone, fontSize = 13.sp, color = RoseGold)
                    }
                }
            }
        } else {
            items(owedToProviders, key = { it.providerId }) { balance ->
                ProviderBalanceRow(
                    balance    = balance,
                    isPayingOut = viewModel.payoutInProgress == balance.providerId,
                    onMarkPaid = { confirmPayout = balance }
                )
            }
        }

        // ── Cash-commission debt (salons that owe the platform) ────────────────
        if (owedByProviders.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(strings.financeDebtTitle, fontSize = 13.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                    Text(strings.financeDebtHint, fontSize = 11.sp, color = NeutralGrey)
                }
            }
            items(owedByProviders, key = { "debt_${it.providerId}" }) { balance ->
                ProviderDebtRow(balance)
            }
        }

        // ── Payout history ────────────────────────────────────────────────────
        if (payouts.isNotEmpty()) {
            item {
                Text(
                    strings.financePayoutsHistoryTitle,
                    fontSize   = 13.sp,
                    color      = RoseGold,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(top = 8.dp)
                )
            }
            items(payouts, key = { it.id }) { payout ->
                PayoutHistoryRow(payout, payoutFmt.format(Date(payout.createdAt)))
            }
        }

        // ── Pending refund requests (from cancelled, already-paid bookings) ────
        if (refundRequests.isNotEmpty()) {
            item {
                Text(
                    strings.financeRefundsTitle,
                    fontSize   = 13.sp,
                    color      = RoseGold,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(top = 8.dp)
                )
            }
            items(refundRequests, key = { it.id }) { refund ->
                RefundRequestRow(
                    refund        = refund,
                    isProcessing  = viewModel.refundInProgress == refund.id,
                    onMarkRefunded = { confirmRefund = refund }
                )
            }
        }
    }

    // Confirm-payout dialog.
    confirmPayout?.let { balance ->
        AlertDialog(
            onDismissRequest = { confirmPayout = null },
            title = { Text(strings.financePayoutConfirmTitle, color = DeepRose, fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(strings.financePayoutConfirmText, color = RoseGold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${balance.providerName} · ${balance.owedAmount} AFN",
                        color = DeepRose, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.payoutProvider(balance.providerId)
                    confirmPayout = null
                }) {
                    Text(strings.financeMarkPaid, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPayout = null }) {
                    Text(strings.cancel, color = RoseGold)
                }
            }
        )
    }

    if (viewModel.commissionSaved) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCommissionSaved() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCommissionSaved() }) {
                    Text(strings.ok, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(strings.financeSaved, color = DeepRose) }
        )
    }

    viewModel.payoutResult?.let { amount ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPayoutResult() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPayoutResult() }) {
                    Text(strings.ok, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("${strings.financePayoutDone}: $amount AFN", color = DeepRose) }
        )
    }

    // Confirm-refund dialog.
    confirmRefund?.let { refund ->
        AlertDialog(
            onDismissRequest = { confirmRefund = null },
            title = { Text(strings.financeRefundConfirmTitle, color = DeepRose, fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(strings.financeRefundConfirmText, color = RoseGold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${refund.customerName} · ${refund.amount} AFN",
                        color = DeepRose, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.processRefund(refund.id)
                    confirmRefund = null
                }) {
                    Text(strings.financeMarkRefunded, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRefund = null }) {
                    Text(strings.cancel, color = RoseGold)
                }
            }
        )
    }

    if (viewModel.refundResult) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRefundResult() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissRefundResult() }) {
                    Text(strings.ok, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(strings.financeRefundDone, color = DeepRose) }
        )
    }

    if (viewModel.payoutFailed || viewModel.commissionFailed || viewModel.refundFailed) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissPayoutFailed()
                viewModel.dismissCommissionFailed()
                viewModel.dismissRefundFailed()
            },
            title = { Text(strings.actionFailedTitle, color = DeepRose, fontWeight = FontWeight.Bold) },
            text  = { Text(strings.actionFailedText, color = RoseGold, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPayoutFailed()
                    viewModel.dismissCommissionFailed()
                    viewModel.dismissRefundFailed()
                }) {
                    Text(strings.ok, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ProviderBalanceRow(
    balance: ProviderBalance,
    isPayingOut: Boolean,
    onMarkPaid: () -> Unit
) {
    val strings = LocalStrings.current
    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(40.dp)
                        .background(AvailableGreen.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.Default.Payments, null, tint = AvailableGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(balance.providerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                    Text(strings.financeOwed, fontSize = 11.sp, color = RoseGold)
                    Text(
                        if (balance.hesabAccountNumber.isNotBlank())
                            strings.financeHesabAccountLabel(balance.hesabAccountNumber)
                        else strings.financeHesabAccountMissing,
                        fontSize = 11.sp,
                        color    = if (balance.hesabAccountNumber.isNotBlank()) NeutralGrey else DangerRed
                    )
                }
                Text(
                    "${balance.owedAmount} AFN",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = AvailableGreen
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick  = onMarkPaid,
                enabled  = !isPayingOut,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AvailableGreen),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                if (isPayingOut) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.financeMarkPaid, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ProviderDebtRow(balance: ProviderBalance) {
    val strings = LocalStrings.current
    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(40.dp)
                    .background(DangerRed.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(Icons.Default.Percent, null, tint = DangerRed, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(balance.providerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                Text(strings.financeOwesPlatform, fontSize = 11.sp, color = RoseGold)
            }
            Text(
                "${-balance.owedAmount} AFN",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = DangerRed
            )
        }
    }
}

@Composable
private fun RefundRequestRow(
    refund: com.safebeauty.app.data.firebase.RefundRequestDocument,
    isProcessing: Boolean,
    onMarkRefunded: () -> Unit
) {
    val strings = LocalStrings.current
    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(40.dp)
                        .background(DangerRed.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.Default.Payments, null, tint = DangerRed, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(refund.customerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                    Text(refund.salonName, fontSize = 11.sp, color = RoseGold)
                }
                Text(
                    "${refund.amount} AFN",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = DangerRed
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick  = onMarkRefunded,
                enabled  = !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = DangerRed),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.financeMarkRefunded, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PayoutHistoryRow(payout: PayoutDocument, timeLabel: String) {
    ElevatedCard(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(payout.providerName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DeepRose)
                Text(timeLabel, fontSize = 11.sp, color = RoseGold)
            }
            Text(
                "${payout.amount} AFN",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = UnavailableGrey
            )
        }
    }
}
