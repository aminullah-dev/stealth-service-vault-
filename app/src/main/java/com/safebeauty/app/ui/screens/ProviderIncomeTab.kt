package com.safebeauty.app.ui.screens

// Provider income/payout tab, split out of ProviderDashboardScreen.kt to keep
// that file manageable. Same package; IncomeTab is internal so the main screen
// dispatches to it. Int/Long.format() stay in the main file (shared by other tabs).

import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import android.app.TimePickerDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.data.firebase.AppointmentDocument
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.customerRating
import com.safebeauty.app.data.firebase.GalleryImageDocument
import com.safebeauty.app.data.firebase.ReviewDocument
import com.safebeauty.app.navigation.Screen
import com.safebeauty.app.util.AnnouncementPrefs
import com.safebeauty.app.util.ImageUtils
import com.safebeauty.app.ui.theme.AvailableGreen
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.ChipActive
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.Gradients
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.ui.theme.WarmGold
import com.safebeauty.app.util.formatIsolated
import com.safebeauty.app.viewmodel.LanguageViewModel
import com.safebeauty.app.viewmodel.ProviderAnalytics
import com.safebeauty.app.viewmodel.ProviderViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.safebeauty.app.viewmodel.ChangePinViewModel
import com.safebeauty.app.viewmodel.NotificationCenterViewModel
import androidx.compose.material.icons.filled.Notifications
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun IncomeTab(viewModel: ProviderViewModel) {
    val strings          = LocalStrings.current
    val analytics        by viewModel.analytics.collectAsStateWithLifecycle()
    val estimatedRevenue by viewModel.estimatedRevenue.collectAsStateWithLifecycle()
    val owedBalance      by viewModel.owedBalance.collectAsStateWithLifecycle()
    val myPayouts        by viewModel.myPayouts.collectAsStateWithLifecycle()
    val prices           = viewModel.editPrices
    val payoutFmt         = remember { java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()) }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Real owed balance — what the platform actually owes this provider,
        // net of commission, distinct from the price-based estimate below ─────
        item {
            // Gradient hero card — the provider's earnings are the emotional
            // centre of this tab, so it gets the strongest treatment on the screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF57B36B), Color(0xFF2E7D46))
                        )
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(strings.incomeOwedBalance, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White.copy(alpha = 0.92f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text       = "${owedBalance.format()} ${strings.incomeAFN}",
                        fontSize   = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(strings.incomeOwedHint, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        if (myPayouts.isNotEmpty()) {
            item {
                Text(
                    strings.incomePayoutHistory,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = RoseGold,
                    modifier   = Modifier.padding(top = 2.dp)
                )
            }
            items(myPayouts, key = { it.id }) { payout ->
                ElevatedCard(
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                    elevation = CardDefaults.elevatedCardElevation(1.dp),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                payoutFmt.formatIsolated(java.util.Date(payout.createdAt)),
                                fontSize = 12.sp, color = RoseGold
                            )
                        }
                        Text(
                            "${payout.amount.format()} ${strings.incomeAFN}",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRose
                        )
                    }
                }
            }
        }

        // ── Revenue summary (estimate, from self-entered prices) ──────────────
        item {
            ElevatedCard(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                elevation = CardDefaults.elevatedCardElevation(4.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(24.dp).fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(60.dp)
                            .background(DeepRose.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = DeepRose, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text       = "${estimatedRevenue.format()} ${strings.incomeAFN}",
                        fontSize   = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color      = DeepRose
                    )
                    Text(
                        text     = strings.incomeEstimatedRevenue,
                        fontSize = 13.sp,
                        color    = RoseGold
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        MiniStatCard(
                            label    = strings.analyticsConfirmed,
                            value    = "${analytics.confirmed}",
                            tint     = AvailableGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatCard(
                            label    = strings.analyticsCancelled,
                            value    = "${analytics.cancelled}",
                            tint     = UnavailableGrey,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatCard(
                            label    = strings.pending,
                            value    = "${analytics.pending}",
                            tint     = WarmGold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── Per-service breakdown ──────────────────────────────────────────
        if (analytics.confirmedByService.isEmpty()) {
            item {
                CenteredProviderEmpty(
                    icon    = Icons.Default.AttachMoney,
                    title   = strings.noDataYet,
                    subtext = strings.incomeNoData
                )
            }
        } else {
            item {
                Text(
                    strings.analyticsByService,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = DeepRose
                )
            }
            items(analytics.confirmedByService.entries.toList(), key = { it.key }) { (service, count) ->
                val price = prices[service] ?: 0
                val revenue = price * count
                ElevatedCard(
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                    elevation = CardDefaults.elevatedCardElevation(1.dp),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        modifier              = Modifier.padding(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(44.dp)
                                .background(BlushPink, RoundedCornerShape(12.dp))
                        ) {
                            Text("$count", fontWeight = FontWeight.Bold, color = DeepRose, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(service, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                            Text("${strings.incomeConfirmedCount}: $count", fontSize = 12.sp, color = RoseGold)
                        }
                        if (revenue > 0) {
                            Text(
                                "${revenue.format()} ${strings.incomeAFN}",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp,
                                color      = AvailableGreen
                            )
                        }
                    }
                }
            }
        }

        // ── Set prices hint ────────────────────────────────────────────────
        if (prices.values.all { it == 0 }) {
            item {
                ElevatedCard(
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = BlushPink.copy(alpha = 0.3f)),
                    elevation = CardDefaults.elevatedCardElevation(0.dp),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(14.dp)
                    ) {
                        Icon(Icons.Default.AttachMoney, null, tint = RoseGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.incomeNoData, fontSize = 13.sp, color = RoseGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = tint.copy(alpha = 0.08f)),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier  = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(10.dp).fillMaxWidth()
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = tint)
            Text(label, fontSize = 10.sp, color = tint.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CenteredProviderEmpty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtext: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth().padding(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(72.dp)
                .background(BlushPink.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(icon, null, tint = RoseGold, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DeepRose, textAlign = TextAlign.Center)
        if (subtext.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(subtext, fontSize = 12.sp, color = RoseGold, textAlign = TextAlign.Center)
        }
    }
}
