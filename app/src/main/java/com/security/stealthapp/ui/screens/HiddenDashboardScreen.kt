package com.security.stealthapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.data.db.entities.BookingEntry
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.BlushPink
import com.security.stealthapp.ui.theme.CardBorder
import com.security.stealthapp.ui.theme.ChipActive
import com.security.stealthapp.ui.theme.ChipInactive
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.UnavailableGrey
import com.security.stealthapp.ui.theme.WarmGold
import com.security.stealthapp.viewmodel.DashboardViewModel
import com.security.stealthapp.viewmodel.ServiceProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenDashboardScreen(
    onLockTriggered: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.lockTriggered) {
        if (viewModel.lockTriggered) {
            viewModel.resetLockTrigger()
            onLockTriggered()
        }
    }

    val activeBookings by viewModel.activeBookings.collectAsStateWithLifecycle()
    var showNeighborhoodMenu by remember { mutableStateOf(false) }
    var showBookingsSheet    by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text       = "SafeBeauty",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = DeepRose
                            )
                            Text(
                                text     = "Private · Discreet · Trusted",
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    actions = {
                        // Bookings badge
                        IconButton(onClick = { showBookingsSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (activeBookings.isNotEmpty()) {
                                        Badge(containerColor = DeepRose) {
                                            Text("${activeBookings.size}", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "My Bookings", tint = DeepRose)
                            }
                        }
                        // Lock — returns to Disguise screen
                        IconButton(onClick = { viewModel.triggerLock() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock vault", tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                // ── Category filter chips ─────────────────────────────────
                LazyRow(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.categories) { cat ->
                        val selected = viewModel.selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick  = { viewModel.selectCategory(cat) },
                            label    = { Text(cat, fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor    = ChipActive,
                                selectedLabelColor        = Color.White,
                                containerColor            = ChipInactive,
                                labelColor                = DeepRose
                            )
                        )
                    }
                }

                // ── Neighborhood dropdown ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = { showNeighborhoodMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ChipInactive),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text     = viewModel.selectedNeighborhood,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded        = showNeighborhoodMenu,
                        onDismissRequest = { showNeighborhoodMenu = false },
                        modifier         = Modifier.background(ElegantCream)
                    ) {
                        viewModel.neighborhoods.forEach { hood ->
                            DropdownMenuItem(
                                text    = { Text(hood, fontSize = 13.sp, color = DeepRose) },
                                onClick = {
                                    viewModel.selectNeighborhood(hood)
                                    showNeighborhoodMenu = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = BlushPink, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))

                // ── Results count ─────────────────────────────────────────
                Text(
                    text     = "${viewModel.filteredProviders.size} providers found",
                    fontSize = 12.sp,
                    color    = RoseGold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                // ── Provider cards list ───────────────────────────────────
                LazyColumn(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.fillMaxSize()
                ) {
                    items(viewModel.filteredProviders, key = { it.id }) { provider ->
                        ProviderCard(
                            provider = provider,
                            onBook   = { viewModel.bookProvider(provider) }
                        )
                    }
                }
            }
        }

        // ── Booking confirmation dialog ────────────────────────────────────
        viewModel.bookingConfirmation?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissConfirmation() },
                icon  = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AvailableGreen,
                        modifier = Modifier.size(40.dp)
                    )
                },
                title = { Text("Booking Request Sent", fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = {
                    Text(
                        text     = message,
                        fontSize = 14.sp,
                        color    = Color(0xFF555555)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissConfirmation() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text("OK", color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── My Bookings bottom sheet ───────────────────────────────────────
        if (showBookingsSheet) {
            ModalBottomSheet(
                onDismissRequest  = { showBookingsSheet = false },
                sheetState        = sheetState,
                containerColor    = ElegantCream
            ) {
                BookingsSheetContent(
                    bookings  = activeBookings,
                    onDismiss = { showBookingsSheet = false }
                )
            }
        }
    }
}

// ── Provider card ─────────────────────────────────────────────────────────────

@Composable
private fun ProviderCard(
    provider: ServiceProvider,
    onBook: () -> Unit
) {
    ElevatedCard(
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar circle with first letter
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BlushPink)
                ) {
                    Text(
                        text       = provider.name.first().toString(),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = DeepRose
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = provider.name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = DeepRose,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = provider.speciality,
                        fontSize = 12.sp,
                        color    = RoseGold
                    )
                }

                // Category badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipInactive)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(provider.category, fontSize = 11.sp, color = DeepRose, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint     = RoseGold,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text     = provider.neighborhood,
                    fontSize = 12.sp,
                    color    = Color(0xFF888888),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                // Star rating
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint     = WarmGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text     = "%.1f".format(provider.rating),
                        fontSize = 13.sp,
                        color    = Color(0xFF555555),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Availability dot + label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (provider.isAvailable) AvailableGreen else UnavailableGrey)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = if (provider.isAvailable) "Available" else "Busy",
                        fontSize = 12.sp,
                        color    = if (provider.isAvailable) AvailableGreen else UnavailableGrey
                    )
                }

                // Book button
                Button(
                    onClick  = onBook,
                    enabled  = provider.isAvailable,
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = RoseGold,
                        disabledContainerColor = UnavailableGrey.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text     = "Book",
                        fontSize = 13.sp,
                        color    = Color.White
                    )
                }
            }
        }
    }
}

// ── Bookings bottom sheet content ─────────────────────────────────────────────

@Composable
private fun BookingsSheetContent(
    bookings: List<BookingEntry>,
    onDismiss: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = "My Bookings",
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = DeepRose
            )
            TextButton(onClick = onDismiss) {
                Text("Close", color = RoseGold)
            }
        }

        HorizontalDivider(color = BlushPink)
        Spacer(Modifier.height(8.dp))

        if (bookings.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            ) {
                Text(
                    text     = "No bookings yet.\nDiscover a provider and tap Book.",
                    fontSize = 14.sp,
                    color    = Color(0xFFAAAAAA),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            bookings.forEach { booking ->
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text       = booking.providerName,
                                fontWeight = FontWeight.SemiBold,
                                color      = DeepRose,
                                fontSize   = 14.sp
                            )
                            BookingStatusChip(booking.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text     = "${booking.serviceCategory} · ${booking.neighborhood}",
                            fontSize = 12.sp,
                            color    = RoseGold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text     = "Scheduled: ${dateFmt.format(Date(booking.scheduledTime))}",
                            fontSize = 11.sp,
                            color    = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingStatusChip(status: String) {
    val (bg, fg) = when (status) {
        "CONFIRMED"  -> Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
        "COMPLETED"  -> Pair(Color(0xFF1976D2).copy(alpha = 0.12f), Color(0xFF1976D2))
        "CANCELLED"  -> Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
        else         -> Pair(WarmGold.copy(alpha = 0.15f), WarmGold) // PENDING
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text       = status.lowercase().replaceFirstChar { it.uppercaseChar() },
            fontSize   = 11.sp,
            color      = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}
