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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import com.security.stealthapp.data.firebase.UserDocument
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
    val stats            by viewModel.stats.collectAsStateWithLifecycle()
    val broadcasts       by viewModel.broadcasts.collectAsStateWithLifecycle()
    var showLangPicker   by remember { mutableStateOf(false) }
    var selectedTab      by remember { mutableIntStateOf(0) }

    val tabs = listOf(strings.approvalQueueSubtitle, strings.tabStats, strings.tabBroadcast)

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
                    1 -> StatsTab(stats)
                    2 -> BroadcastTab(broadcasts, viewModel)
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

// ── Tab 1: Stats ──────────────────────────────────────────────────────────────

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

// ── Tab 2: Broadcast ──────────────────────────────────────────────────────────

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
