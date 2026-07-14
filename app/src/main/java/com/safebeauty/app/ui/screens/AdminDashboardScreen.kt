package com.safebeauty.app.ui.screens

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLockTriggered: () -> Unit,
    onNavigate: (String) -> Unit = {},
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
    val approvalsLoaded  by viewModel.approvalsLoaded.collectAsStateWithLifecycle()
    val allUsers         by viewModel.allUsers.collectAsStateWithLifecycle()
    val usersLoaded      by viewModel.usersLoaded.collectAsStateWithLifecycle()
    val allSalons        by viewModel.allSalons.collectAsStateWithLifecycle()
    val stats            by viewModel.stats.collectAsStateWithLifecycle()
    val statsLoaded      by viewModel.statsLoaded.collectAsStateWithLifecycle()
    val broadcasts       by viewModel.broadcasts.collectAsStateWithLifecycle()
    val commissionPercent by viewModel.commissionPercent.collectAsStateWithLifecycle()
    val providerBalances by viewModel.providerBalances.collectAsStateWithLifecycle()
    val payouts          by viewModel.payouts.collectAsStateWithLifecycle()
    val refundRequests   by viewModel.pendingRefundRequests.collectAsStateWithLifecycle()
    val promoCodes       by viewModel.promoCodes.collectAsStateWithLifecycle()
    val kycPending       by viewModel.kycPending.collectAsStateWithLifecycle()
    val kycLoaded        by viewModel.kycLoaded.collectAsStateWithLifecycle()
    var showLangPicker   by remember { mutableStateOf(false) }
    var selectedTab      by remember { mutableIntStateOf(0) }

    val flaggedReports   by viewModel.flaggedReports.collectAsStateWithLifecycle()
    val supportTickets   by viewModel.supportTickets.collectAsStateWithLifecycle()

    val tabs = listOf(
        strings.approvalQueueSubtitle, strings.tabKyc, strings.tabUsers,
        strings.tabSalons, strings.tabStats, strings.tabBroadcast, strings.tabFinance,
        strings.tabPromos, strings.tabReports, strings.tabSupport
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
                        IconButton(onClick = { onNavigate(Screen.Support.route) }) {
                            Icon(Icons.Default.SupportAgent, contentDescription = strings.supportTitle, tint = RoseGold)
                        }
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
            Column(modifier = Modifier.fillMaxSize().background(Gradients.ScreenBg).padding(padding)) {

                ScrollableTabRow(
                    selectedTabIndex  = selectedTab,
                    containerColor    = Color.Transparent,
                    contentColor      = DeepRose,
                    edgePadding       = 12.dp,
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
                    0 -> ApprovalsTab(pendingProviders, approvalsLoaded, viewModel)
                    1 -> KycReviewTab(kycPending, kycLoaded, viewModel)
                    2 -> UsersTab(allUsers, usersLoaded, viewModel)
                    3 -> SalonsTab(allSalons, viewModel)
                    4 -> StatsTab(stats, statsLoaded)
                    5 -> BroadcastTab(broadcasts, viewModel)
                    6 -> FinanceTab(commissionPercent, providerBalances, payouts, refundRequests, viewModel)
                    7 -> PromosTab(promoCodes, viewModel)
                    8 -> ReportsTab(flaggedReports, viewModel)
                    9 -> SupportTab(supportTickets, viewModel, onNavigate)
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

// ── Shared loading indicator ───────────────────────────────────────────────────
// Distinguishes "the first Firestore snapshot hasn't arrived yet" from a
// genuinely empty result — both used to render the exact same empty state,
// which made a slow/broken connection indistinguishable from "no data".

@Composable
internal fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DeepRose, strokeWidth = 2.dp)
    }
}

// ── Tab 0: Approvals ──────────────────────────────────────────────────────────

@Composable
internal fun CenteredEmpty(icon: ImageVector, title: String, subtext: String) {
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
