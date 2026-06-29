package com.security.stealthapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.data.firebase.BroadcastDocument
import com.security.stealthapp.data.firebase.PayoutDocument
import com.security.stealthapp.data.firebase.ProviderBalance
import com.security.stealthapp.data.firebase.SalonBadge
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.UserDocument
import com.security.stealthapp.data.firebase.badge
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.BlushPink
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.UnavailableGrey
import com.security.stealthapp.viewmodel.AdminViewModel
import com.security.stealthapp.viewmodel.LanguageViewModel
import com.security.stealthapp.viewmodel.SystemStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLockTriggered: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
    langVm: LanguageViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.lockTriggered) {
        if (viewModel.lockTriggered) {
            viewModel.resetLockTrigger()
            onLockTriggered()
        }
    }

    val strings          = LocalStrings.current
    val currentLanguage  by langVm.language.collectAsStateWithLifecycle()
    val pendingProviders by viewModel.pendingProviders.collectAsStateWithLifecycle()
    val allUsers         by viewModel.allUsers.collectAsStateWithLifecycle()
    val allSalons        by viewModel.allSalons.collectAsStateWithLifecycle()
    val stats            by viewModel.stats.collectAsStateWithLifecycle()
    val broadcasts       by viewModel.broadcasts.collectAsStateWithLifecycle()
    val commissionPercent by viewModel.commissionPercent.collectAsStateWithLifecycle()
    val providerBalances by viewModel.providerBalances.collectAsStateWithLifecycle()
    val payouts          by viewModel.payouts.collectAsStateWithLifecycle()
    var showLangPicker   by remember { mutableStateOf(false) }
    var selectedTab      by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        strings.approvalQueueSubtitle, strings.tabUsers,
        strings.tabSalons, strings.tabStats, strings.tabBroadcast, strings.tabFinance
    )

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text       = strings.adminPanelTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = DeepRose
                            )
                            Text(
                                text     = tabs[selectedTab],
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    navigationIcon = {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint     = RoseGold,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    actions = {
                        IconButton(onClick = { showLangPicker = true }) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = RoseGold)
                        }
                        IconButton(onClick = { viewModel.triggerLock() }) {
                            Icon(Icons.Default.Lock, contentDescription = strings.lock, tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                TabRow(
                    selectedTabIndex  = selectedTab,
                    containerColor    = ElegantCream,
                    contentColor      = DeepRose,
                    indicator         = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = DeepRose
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            text     = {
                                Text(
                                    text     = title,
                                    fontSize = 13.sp,
                                    color    = if (selectedTab == i) DeepRose else RoseGold
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> ApprovalsTab(pendingProviders, viewModel)
                    1 -> UsersTab(allUsers, viewModel)
                    2 -> SalonsTab(allSalons, viewModel)
                    3 -> StatsTab(stats)
                    4 -> BroadcastTab(broadcasts, viewModel)
                    5 -> FinanceTab(commissionPercent, providerBalances, payouts, viewModel)
                }
            }
        }

        if (showLangPicker) {
            LanguagePickerDialog(
                current   = currentLanguage,
                onPick    = { langVm.setLanguage(it); showLangPicker = false },
                onDismiss = { showLangPicker = false }
            )
        }
    }
}

// ── Tab 0: Approvals ──────────────────────────────────────────────────────────

@Composable
private fun ApprovalsTab(
    pendingProviders: List<UserDocument>,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    if (pendingProviders.isEmpty()) {
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
                    onReject  = { viewModel.rejectProvider(provider.uid) }
                )
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
                        text     = provider.phone.ifBlank { "No phone provided" },
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
private fun UsersTab(users: List<UserDocument>, viewModel: AdminViewModel) {
    val strings = LocalStrings.current
    var deleteTarget by remember { mutableStateOf<UserDocument?>(null) }

    if (users.isEmpty()) {
        CenteredEmpty(Icons.Default.Group, strings.statsTotalUsers, "")
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.uid }) { user ->
                UserRow(
                    user        = user,
                    onDelete    = { deleteTarget = user },
                    onSuspend   = { viewModel.suspendUser(user.uid) },
                    onUnsuspend = { viewModel.unsuspendUser(user.uid) }
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text(strings.deleteUserConfirmTitle, color = DeepRose) },
            text             = { Text(strings.deleteUserConfirmText, color = RoseGold) },
            confirmButton    = {
                TextButton(onClick = {
                    viewModel.deleteUser(target.uid, target.role == "PROVIDER")
                    deleteTarget = null
                }) {
                    Text(strings.deleteUser, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(strings.cancel, color = RoseGold)
                }
            }
        )
    }
}

@Composable
private fun UserRow(
    user: UserDocument,
    onDelete: () -> Unit,
    onSuspend: () -> Unit,
    onUnsuspend: () -> Unit
) {
    val strings = LocalStrings.current
    val roleColor = when (user.role) {
        "ADMIN"    -> Color(0xFF7B6FA0)
        "PROVIDER" -> AvailableGreen
        else       -> RoseGold
    }
    val statusColor = when (user.status) {
        "APPROVED"  -> AvailableGreen
        "REJECTED"  -> UnavailableGrey
        "SUSPENDED" -> Color(0xFFE67E22)
        else        -> Color(0xFFE67E22)
    }

    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier             = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(40.dp)
                    .background(roleColor.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Person, null, tint = roleColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleBadge(user.role, roleColor)
                    StatusBadge(user.status, statusColor)
                }
            }
            if (user.role != "ADMIN") {
                if (user.status == "SUSPENDED") {
                    IconButton(onClick = onUnsuspend, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.LockOpen, strings.unsuspendUser, tint = AvailableGreen, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onSuspend, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Block, strings.suspendUser, tint = Color(0xFFE67E22), modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, strings.deleteUser, tint = UnavailableGrey, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(role, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(status: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(status, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Tab 2: Stats ──────────────────────────────────────────────────────────────

@Composable
private fun StatsTab(stats: SystemStats) {
    val strings = LocalStrings.current
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                strings.statsTotalUsers,
                fontSize   = 13.sp,
                color      = RoseGold,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                AdminStatCard(
                    icon    = Icons.Default.Group,
                    label   = strings.statsTotalUsers,
                    value   = "${stats.totalUsers}",
                    tint    = DeepRose,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    icon    = Icons.Default.QueryStats,
                    label   = strings.statsPendingApprovals,
                    value   = "${stats.pendingApprovals}",
                    tint    = Color(0xFFE67E22),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                AdminStatCard(
                    icon    = Icons.Default.CheckCircle,
                    label   = strings.statsProviders,
                    value   = "${stats.providers}",
                    tint    = AvailableGreen,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    icon    = Icons.Default.Person,
                    label   = strings.statsCustomers,
                    value   = "${stats.customers}",
                    tint    = RoseGold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                AdminStatCard(
                    icon    = Icons.Default.Store,
                    label   = strings.totalSalons,
                    value   = "${stats.totalSalons}",
                    tint    = DeepRose,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    icon    = Icons.Default.Block,
                    label   = strings.statsSuspended,
                    value   = "${stats.suspendedUsers}",
                    tint    = Color(0xFFE67E22),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AdminStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(20.dp).fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(52.dp)
                    .background(tint.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text       = value,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = tint
            )
            Text(
                text      = label,
                fontSize  = 11.sp,
                color     = RoseGold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Tab 2: Salons ─────────────────────────────────────────────────────────────

@Composable
private fun SalonsTab(salons: List<SalonDocument>, viewModel: AdminViewModel) {
    val strings = LocalStrings.current
    if (salons.isEmpty()) {
        CenteredEmpty(Icons.Default.Store, strings.noSalonsYet, "")
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(salons, key = { it.id }) { salon ->
                SalonAdminRow(
                    salon           = salon,
                    onVerifyToggle  = { viewModel.verifySalon(salon.id, !salon.isVerified) }
                )
            }
        }
    }
}

@Composable
private fun SalonAdminRow(salon: SalonDocument, onVerifyToggle: () -> Unit) {
    val strings    = LocalStrings.current
    val availColor = if (salon.isAvailable) AvailableGreen else UnavailableGrey
    val badge      = salon.badge()
    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .background(BlushPink, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Store, null, tint = DeepRose, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(salon.salonName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                    if (badge != SalonBadge.NONE) {
                        val (badgeLabel, badgeColor) = when (badge) {
                            SalonBadge.VERIFIED -> Pair(strings.badgeVerified, Color(0xFF4CAF50))
                            SalonBadge.GOLD     -> Pair(strings.badgeGold,     Color(0xFFD4A853))
                            SalonBadge.SILVER   -> Pair(strings.badgeSilver,   Color(0xFF9E9E9E))
                            SalonBadge.NONE     -> Pair("", Color.Transparent)
                        }
                        Box(
                            modifier = Modifier
                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(badgeLabel, fontSize = 9.sp, color = badgeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(salon.district, fontSize = 12.sp, color = RoseGold)
                if (salon.services.isNotEmpty()) {
                    Text(
                        salon.services.take(3).joinToString(" · "),
                        fontSize = 11.sp,
                        color    = RoseGold.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            // Verify toggle
            IconButton(onClick = onVerifyToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = if (salon.isVerified) strings.badgeUnverify else strings.badgeVerifyToggle,
                    tint   = if (salon.isVerified) Color(0xFF4CAF50) else UnavailableGrey.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .background(availColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (salon.isAvailable) "OPEN" else "CLOSED",
                    fontSize   = 10.sp,
                    color      = availColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Tab 3: Broadcast ──────────────────────────────────────────────────────────

@Composable
private fun BroadcastTab(
    broadcasts: List<BroadcastDocument>,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    val fmt     = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value       = viewModel.broadcastText,
                        onValueChange = { viewModel.broadcastText = it },
                        label       = { Text(strings.broadcastHint, fontSize = 13.sp) },
                        modifier    = Modifier.fillMaxWidth(),
                        minLines    = 3,
                        maxLines    = 6,
                        shape       = RoundedCornerShape(12.dp),
                        colors      = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = DeepRose,
                            unfocusedBorderColor = BlushPink,
                            focusedLabelColor    = DeepRose
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = { viewModel.sendBroadcast() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = viewModel.broadcastText.isNotBlank(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = DeepRose)
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.broadcastSend, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (broadcasts.isEmpty()) {
            item {
                CenteredEmpty(
                    icon    = Icons.Default.Campaign,
                    title   = strings.broadcastNone,
                    subtext = ""
                )
            }
        } else {
            items(broadcasts, key = { it.id }) { broadcast ->
                BroadcastCard(broadcast, fmt.format(Date(broadcast.createdAt)))
            }
        }
    }
}

@Composable
private fun BroadcastCard(broadcast: BroadcastDocument, timeLabel: String) {
    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BlushPink)
            ) {
                Icon(Icons.Default.Campaign, null, tint = DeepRose, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(broadcast.message, fontSize = 14.sp, color = DeepRose)
                Spacer(Modifier.height(4.dp))
                Text(timeLabel, fontSize = 11.sp, color = RoseGold)
            }
        }
    }
}

// ── Tab 5: Finance (commission + payouts) ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceTab(
    commissionPercent: Double,
    balances: List<ProviderBalance>,
    payouts: List<PayoutDocument>,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    val payoutFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    var confirmPayout by remember { mutableStateOf<ProviderBalance?>(null) }

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
        item {
            Text(
                strings.financeBalancesTitle,
                fontSize   = 13.sp,
                color      = RoseGold,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(top = 4.dp)
            )
        }

        if (balances.isEmpty()) {
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
            items(balances, key = { it.providerId }) { balance ->
                ProviderBalanceRow(
                    balance    = balance,
                    isPayingOut = viewModel.payoutInProgress == balance.providerId,
                    onMarkPaid = { confirmPayout = balance }
                )
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

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun CenteredEmpty(icon: ImageVector, title: String, subtext: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .background(BlushPink.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(icon, null, tint = RoseGold, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DeepRose)
            if (subtext.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(subtext, fontSize = 13.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)
            }
        }
    }
}
