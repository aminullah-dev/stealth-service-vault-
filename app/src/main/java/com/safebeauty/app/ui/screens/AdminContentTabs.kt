package com.safebeauty.app.ui.screens

// Admin content tabs (stats, salons, broadcasts, promos), split out of
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
internal fun StatsTab(stats: SystemStats, isLoaded: Boolean) {
    val strings = LocalStrings.current
    if (!isLoaded) {
        LoadingBox()
        return
    }
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
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
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
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(tint.copy(alpha = 0.85f), tint)
                        ),
                        CircleShape
                    )
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
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
internal fun SalonsTab(salons: List<SalonDocument>, viewModel: AdminViewModel) {
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
                    if (salon.isAvailable) strings.salonOpen else strings.salonClosed,
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
internal fun BroadcastTab(
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
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
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

// ── Tab 8: Promo codes ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromosTab(
    promoCodes: List<com.safebeauty.app.data.firebase.PromoDocument>,
    viewModel: AdminViewModel
) {
    val strings = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = DeepRose,
        unfocusedBorderColor = BlushPink,
        focusedLabelColor    = DeepRose
    )

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Create form ───────────────────────────────────────────────────────
        item {
            ElevatedCard(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalOffer, null, tint = DeepRose, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(strings.promoNewTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepRose)
                    }
                    OutlinedTextField(
                        value         = viewModel.promoCodeInput,
                        onValueChange = { viewModel.promoCodeInput = it.uppercase() },
                        label         = { Text(strings.promoCodeField, fontSize = 13.sp) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = fieldColors
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value         = viewModel.promoPercentInput,
                            onValueChange = { v -> viewModel.promoPercentInput = v.filter(Char::isDigit).take(3) },
                            label         = { Text(strings.promoPercentField, fontSize = 12.sp) },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(12.dp),
                            colors        = fieldColors
                        )
                        OutlinedTextField(
                            value         = viewModel.promoAmountInput,
                            onValueChange = { v -> viewModel.promoAmountInput = v.filter(Char::isDigit).take(7) },
                            label         = { Text(strings.promoAmountField, fontSize = 12.sp) },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(12.dp),
                            colors        = fieldColors
                        )
                    }
                    OutlinedTextField(
                        value         = viewModel.promoMaxUsesInput,
                        onValueChange = { v -> viewModel.promoMaxUsesInput = v.filter(Char::isDigit).take(5) },
                        label         = { Text(strings.promoMaxUsesField, fontSize = 12.sp) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = fieldColors
                    )
                    viewModel.promoErrorMsg?.let { err ->
                        Text(err, fontSize = 12.sp, color = Color(0xFFD32F2F))
                    }
                    Button(
                        onClick  = { viewModel.savePromo() },
                        enabled  = !viewModel.promoSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = DeepRose)
                    ) {
                        if (viewModel.promoSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(strings.promoCreateButton, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Existing codes ────────────────────────────────────────────────────
        item {
            Text(
                strings.promoListTitle,
                fontSize = 13.sp, color = RoseGold, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (promoCodes.isEmpty()) {
            item {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                    Text(strings.promoListEmpty, fontSize = 13.sp, color = RoseGold)
                }
            }
        } else {
            items(promoCodes, key = { it.code }) { promo ->
                PromoRow(promo, onToggle = { viewModel.togglePromo(promo.code, it) })
            }
        }
    }

    if (viewModel.promoSaved) {
        LaunchedEffect(Unit) { viewModel.dismissPromoSaved() }
    }
}

// ── Tab 9: Flagged customer reports ─────────────────────────────────────────────

@Composable
private fun PromoRow(
    promo: com.safebeauty.app.data.firebase.PromoDocument,
    onToggle: (Boolean) -> Unit
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(promo.code, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
                val discount = if (promo.discountPercent > 0) "${promo.discountPercent}%"
                               else "${promo.discountAmount} AFN"
                Text(
                    "$discount · ${strings.promoUsesLabel(promo.usedCount, promo.maxUses)}",
                    fontSize = 12.sp, color = RoseGold
                )
                Text(
                    if (promo.active) strings.promoActive else strings.promoInactive,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (promo.active) AvailableGreen else Color(0xFF999999)
                )
            }
            Switch(
                checked         = promo.active,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AvailableGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFBBBBBB)
                )
            )
        }
    }
}

// ── Tab 5: Finance (commission + payouts) ──────────────────────────────────────

// ── Shared helpers ────────────────────────────────────────────────────────────
