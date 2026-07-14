package com.safebeauty.app.ui.screens

// Admin moderation tabs (approvals, KYC review, reports, support), split out of
// AdminDashboardScreen.kt. Same package; the tab entry points are internal.
// CenteredEmpty and LoadingBox stay in the main file (shared).

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
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.Gradients
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.ui.theme.WarmGold
import com.safebeauty.app.viewmodel.AdminViewModel
import com.safebeauty.app.viewmodel.LanguageViewModel
import com.safebeauty.app.viewmodel.SystemStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ApprovalsTab(
    pendingProviders: List<UserDocument>,
    isLoaded: Boolean,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    var rejectTarget by remember { mutableStateOf<UserDocument?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    if (!isLoaded) {
        LoadingBox()
    } else if (pendingProviders.isEmpty()) {
        CenteredEmpty(
            icon    = Icons.Default.AdminPanelSettings,
            title   = strings.noPendingAppsTitle,
            subtext = strings.noPendingAppsSubtext
        )
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pendingProviders, key = { it.uid }) { provider ->
                PendingProviderCard(
                    provider  = provider,
                    onApprove = { viewModel.approveProvider(provider.uid) },
                    onReject  = { rejectReason = ""; rejectTarget = provider }
                )
            }
        }
    }

    // Reject dialog — captures a reason so the applicant isn't left with zero
    // feedback (previously rejectProvider() took no reason at all).
    rejectTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text(strings.rejectDialogTitle, color = DeepRose, fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(provider.name, color = RoseGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = rejectReason,
                        onValueChange = { rejectReason = it },
                        label         = { Text(strings.rejectionReasonLabel, fontSize = 13.sp) },
                        placeholder   = { Text(strings.rejectReasonHint, fontSize = 12.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        minLines      = 2,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = DeepRose,
                            unfocusedBorderColor = BlushPink,
                            focusedLabelColor    = DeepRose
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rejectProvider(provider.uid, rejectReason)
                    rejectTarget = null
                }) {
                    Text(strings.reject, color = Color(0xFFC0392B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text(strings.cancel, color = RoseGold)
                }
            }
        )
    }
}

// ── KYC review tab ────────────────────────────────────────────────────────────

@Composable
internal fun KycReviewTab(
    pending: List<UserDocument>,
    isLoaded: Boolean,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    var rejectTarget by remember { mutableStateOf<UserDocument?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    if (!isLoaded) {
        LoadingBox()
    } else if (pending.isEmpty()) {
        CenteredEmpty(icon = Icons.Default.Badge, title = strings.kycReviewNone, subtext = "")
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pending, key = { it.uid }) { u ->
                KycReviewCard(
                    user      = u,
                    busy      = viewModel.kycReviewInProgress == u.uid,
                    onApprove = { viewModel.approveKyc(u.uid) },
                    onReject  = { rejectReason = ""; rejectTarget = u }
                )
            }
        }
    }

    rejectTarget?.let { u ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text(strings.rejectDialogTitle, color = DeepRose, fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(u.name, color = RoseGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = rejectReason,
                        onValueChange = { rejectReason = it },
                        label         = { Text(strings.rejectionReasonLabel, fontSize = 13.sp) },
                        placeholder   = { Text(strings.rejectReasonHint, fontSize = 12.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        minLines      = 2,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = DeepRose,
                            unfocusedBorderColor = BlushPink,
                            focusedLabelColor    = DeepRose
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectReason.isNotBlank(),
                    onClick = {
                        viewModel.rejectKyc(u.uid, rejectReason)
                        rejectTarget = null
                    }
                ) { Text(strings.reject, color = Color(0xFFC0392B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text(strings.cancel, color = RoseGold)
                }
            }
        )
    }
}

@Composable
private fun KycReviewCard(
    user: UserDocument,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(user.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DeepRose)
            Text(strings.kycReviewTazkiraNo(user.tazkiraNumber), fontSize = 12.sp, color = RoseGold)
            Text("${user.addressProvince} — ${user.addressDetail}", fontSize = 12.sp, color = Color(0xFF777777))

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl(user.tazkiraPhotoUrl) }, shape = RoundedCornerShape(10.dp)) {
                    Text(strings.kycReviewViewTazkira, color = RoseGold, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { openUrl(user.selfiePhotoUrl) }, shape = RoundedCornerShape(10.dp)) {
                    Text(strings.kycReviewViewSelfie, color = RoseGold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = onApprove,
                    enabled  = !busy,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AvailableGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.kycReviewApprove, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick  = onReject,
                    enabled  = !busy,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.kycReviewReject, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PendingProviderCard(
    provider: UserDocument,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val strings = LocalStrings.current
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
                        .size(48.dp)
                        .background(BlushPink, RoundedCornerShape(14.dp))
                ) {
                    Icon(Icons.Default.Person, null, tint = DeepRose, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = provider.name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = DeepRose
                    )
                    Text(
                        text     = provider.phone.ifBlank { strings.noPhoneProvided },
                        fontSize = 12.sp,
                        color    = RoseGold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(UnavailableGrey.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        strings.pending,
                        fontSize   = 11.sp,
                        color      = UnavailableGrey,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BlushPink.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick        = onApprove,
                    modifier       = Modifier.weight(1f),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = AvailableGreen),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(strings.approve, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick        = onReject,
                    modifier       = Modifier.weight(1f),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = UnavailableGrey.copy(alpha = 0.8f)),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(strings.reject, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Tab 1: All Users ──────────────────────────────────────────────────────────

@Composable
internal fun ReportsTab(
    reports: List<com.safebeauty.app.data.firebase.CustomerReportDocument>,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    if (reports.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Flag, null, tint = RoseGold, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(strings.reportsEmpty, fontSize = 14.sp, color = RoseGold, textAlign = TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reports, key = { it.id }) { report ->
            ReportRow(
                report      = report,
                inProgress  = viewModel.reportInProgress == report.id,
                onDismiss   = { viewModel.resolveReport(report.id, suspend = false) },
                onSuspend   = { viewModel.resolveReport(report.id, suspend = true) }
            )
        }
    }
}

@Composable
private fun ReportRow(
    report: com.safebeauty.app.data.firebase.CustomerReportDocument,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onSuspend: () -> Unit
) {
    val strings = LocalStrings.current
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    ElevatedCard(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, null, tint = Color(0xFFB00020), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.customerName.ifBlank { report.customerId }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
                    Text(report.salonName.ifBlank { report.salonId }, fontSize = 12.sp, color = RoseGold)
                }
                if (report.rating > 0) {
                    Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${report.rating}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DeepRose)
                }
            }
            if (report.noShow) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFB00020).copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(strings.rateCustomerNoShow, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB00020))
                }
            }
            if (report.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    report.comment,
                    fontSize  = 13.sp,
                    color     = Color(0xFF555555),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(dateFmt.format(Date(report.createdAt)), fontSize = 11.sp, color = Color(0xFF999999))
            Spacer(Modifier.height(10.dp))
            if (inProgress) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    CircularProgressIndicator(color = DeepRose, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Text(strings.reportDismiss, color = DeepRose, fontSize = 13.sp)
                    }
                    Button(
                        onClick  = onSuspend,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                    ) {
                        Text(strings.reportSuspend, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Tab 10: Support inbox ───────────────────────────────────────────────────────

@Composable
internal fun SupportTab(
    tickets: List<com.safebeauty.app.data.firebase.SupportTicket>,
    viewModel: AdminViewModel,
    onNavigate: (String) -> Unit
) {
    val strings = LocalStrings.current
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    if (tickets.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SupportAgent, null, tint = RoseGold, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(strings.supportInboxEmpty, fontSize = 14.sp, color = RoseGold, textAlign = TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tickets, key = { it.id }) { ticket ->
            ElevatedCard(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ticket.unreadForAdmin) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFB00020)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ticket.userName.ifBlank { ticket.userId }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
                            Text(ticket.userRole, fontSize = 11.sp, color = RoseGold)
                        }
                        Text(dateFmt.format(Date(ticket.updatedAt)), fontSize = 11.sp, color = Color(0xFF999999))
                    }
                    if (ticket.relatedInfo.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(ticket.relatedInfo, fontSize = 13.sp, color = Color(0xFF555555))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick  = {
                                viewModel.markSupportRead(ticket.userId)
                                onNavigate(
                                    Screen.Chat.build(
                                        conversationId = "support_${ticket.userId}",
                                        myUserId       = viewModel.adminId,
                                        myName         = strings.supportTitle,
                                        otherName      = ticket.userName.ifBlank { ticket.userId },
                                        active         = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = DeepRose)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.chat, color = Color.White, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick  = { viewModel.closeSupportTicket(ticket.userId) },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Text(strings.supportClose, color = DeepRose, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
