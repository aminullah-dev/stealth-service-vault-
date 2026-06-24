package com.security.stealthapp.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.security.stealthapp.data.firebase.AppointmentDocument
import com.security.stealthapp.data.firebase.BroadcastDocument
import com.security.stealthapp.data.firebase.GalleryImageDocument
import com.security.stealthapp.data.firebase.ReviewDocument
import com.security.stealthapp.navigation.Screen
import com.security.stealthapp.util.ImageUtils
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.BlushPink
import com.security.stealthapp.ui.theme.ChipActive
import com.security.stealthapp.ui.theme.ChipInactive
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.UnavailableGrey
import com.security.stealthapp.ui.theme.WarmGold
import com.security.stealthapp.viewmodel.DecoyPinViewModel
import com.security.stealthapp.viewmodel.LanguageViewModel
import com.security.stealthapp.viewmodel.ProviderAnalytics
import com.security.stealthapp.viewmodel.ProviderViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.security.stealthapp.viewmodel.ChangePinViewModel
import com.security.stealthapp.viewmodel.NotificationCenterViewModel
import androidx.compose.material.icons.filled.Notifications
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDashboardScreen(
    onLockTriggered: () -> Unit,
    onNavigate: (String) -> Unit         = {},
    viewModel: ProviderViewModel         = hiltViewModel(),
    langVm: LanguageViewModel            = hiltViewModel(),
    notifVm: NotificationCenterViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.lockTriggered) {
        if (viewModel.lockTriggered) {
            viewModel.resetLockTrigger()
            onLockTriggered()
        }
    }

    val strings             = LocalStrings.current
    val currentLanguage     by langVm.language.collectAsStateWithLifecycle()
    val salon               by viewModel.salon.collectAsStateWithLifecycle()
    val isAvailable         by viewModel.isAvailable.collectAsStateWithLifecycle()
    val pendingAppointments by viewModel.pendingAppointments.collectAsStateWithLifecycle()
    val allAppointments     by viewModel.allAppointments.collectAsStateWithLifecycle()
    val analytics           by viewModel.analytics.collectAsStateWithLifecycle()
    val broadcasts          by viewModel.broadcasts.collectAsStateWithLifecycle()
    val reviews             by viewModel.reviews.collectAsStateWithLifecycle()
    var selectedTab         by remember { mutableIntStateOf(0) }
    var showLangPicker      by remember { mutableStateOf(false) }

    val salonName = salon?.salonName ?: "My Salon"

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text       = salonName,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = DeepRose
                            )
                            Text(
                                text     = strings.taglineProvider,
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    actions = {
                        val unreadCount by notifVm.unreadCount.collectAsStateWithLifecycle()
                        IconButton(onClick = { onNavigate(Screen.Notifications.build(viewModel.providerId)) }) {
                            BadgedBox(badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = DeepRose) {
                                        Text("$unreadCount", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Notifications, strings.notificationCenterTitle, tint = RoseGold)
                            }
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                // ── Availability status card ──────────────────────────────
                AvailabilityCard(
                    isAvailable = isAvailable,
                    onToggle    = { viewModel.toggleAvailability() }
                )

                // ── Broadcast announcements ───────────────────────────────
                if (broadcasts.isNotEmpty()) {
                    ProviderBroadcastBanner(broadcasts = broadcasts)
                }

                Spacer(Modifier.height(4.dp))

                // ── Tabs ──────────────────────────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = ElegantCream,
                    contentColor     = DeepRose,
                    indicator        = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = RoseGold
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(strings.tabRequests, fontSize = 14.sp)
                                if (pendingAppointments.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(DeepRose)
                                    ) {
                                        Text(
                                            text       = "${pendingAppointments.size}",
                                            fontSize   = 10.sp,
                                            color      = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        text     = { Text(strings.tabMyProfile, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick  = { selectedTab = 2 },
                        text     = { Text(strings.tabAnalytics, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick  = { selectedTab = 3 },
                        text     = { Text(strings.tabIncome, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick  = { selectedTab = 4 },
                        text     = { Text(strings.tabCalendar, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick  = { selectedTab = 5 },
                        text     = { Text(strings.reviews, fontSize = 14.sp) }
                    )
                }

                // ── Tab content ───────────────────────────────────────────
                when (selectedTab) {
                    0 -> BookingRequestsTab(
                        appointments = pendingAppointments,
                        salonId      = salon?.id ?: "",
                        providerName = salon?.salonName ?: "",
                        providerId   = viewModel.providerId,
                        onAccept     = { viewModel.acceptAppointment(it) },
                        onDecline    = { viewModel.declineAppointment(it) },
                        onNavigate   = onNavigate
                    )
                    1 -> ProfileTab(viewModel = viewModel)
                    2 -> AnalyticsTab(analytics = analytics)
                    3 -> IncomeTab(viewModel = viewModel)
                    4 -> CalendarTab(allAppointments = allAppointments)
                    5 -> ReviewsTab(
                        reviews  = reviews,
                        onReply  = { id, text -> viewModel.replyToReview(id, text) }
                    )
                }
            }
        }

        // ── Save success dialog ───────────────────────────────────────────
        if (viewModel.showSaveSuccess) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.dismissSaveSuccess() },
                icon = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = AvailableGreen,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(strings.profileSavedTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text(strings.profileSavedText, fontSize = 14.sp, color = Color(0xFF555555)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissSaveSuccess() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
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

// ── Broadcast banner ──────────────────────────────────────────────────────────

@Composable
private fun ProviderBroadcastBanner(broadcasts: List<BroadcastDocument>) {
    val dateFmt = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8F0))
            .padding(vertical = 8.dp)
    ) {
        broadcasts.forEach { broadcast ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlushPink.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("📢", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = broadcast.message,
                        fontSize   = 13.sp,
                        color      = DeepRose,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = dateFmt.format(java.util.Date(broadcast.createdAt)),
                        fontSize = 11.sp,
                        color    = RoseGold
                    )
                }
            }
        }
    }
}

// ── Availability toggle card ───────────────────────────────────────────────────

@Composable
private fun AvailabilityCard(isAvailable: Boolean, onToggle: () -> Unit) {
    val strings = LocalStrings.current
    val statusColor by animateColorAsState(
        targetValue   = if (isAvailable) AvailableGreen else UnavailableGrey,
        animationSpec = tween(durationMillis = 400),
        label         = "statusColor"
    )
    val cardBg by animateColorAsState(
        targetValue   = if (isAvailable) AvailableGreen.copy(alpha = 0.08f) else UnavailableGrey.copy(alpha = 0.06f),
        animationSpec = tween(durationMillis = 400),
        label         = "cardBg"
    )

    ElevatedCard(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text       = if (isAvailable) strings.acceptingBookings else strings.closedForBookings,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = DeepRose
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = if (isAvailable) strings.customersCanSeeYou else strings.youAreHidden,
                        fontSize = 12.sp,
                        color    = statusColor
                    )
                }
            }
            Switch(
                checked         = isAvailable,
                onCheckedChange = { onToggle() },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = AvailableGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = UnavailableGrey.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// ── Booking requests tab ──────────────────────────────────────────────────────

@Composable
private fun BookingRequestsTab(
    appointments: List<AppointmentDocument>,
    salonId: String,
    providerName: String,
    providerId: String,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val strings = LocalStrings.current
    if (appointments.isEmpty()) {
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
                        .clip(CircleShape)
                        .background(BlushPink.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint     = RoseGold,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text       = strings.noPendingRequests,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = DeepRose
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = strings.noPendingRequestsSubtext,
                    fontSize  = 13.sp,
                    color     = Color(0xFFAAAAAA),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier            = Modifier.fillMaxSize()
        ) {
            items(appointments, key = { it.id }) { appt ->
                BookingRequestCard(
                    appointment = appt,
                    onAccept    = { onAccept(appt.id) },
                    onDecline   = { onDecline(appt.id) },
                    onChat      = {
                        if (salonId.isNotBlank()) {
                            onNavigate(
                                Screen.Chat.build(
                                    conversationId = "${appt.customerId}_$salonId",
                                    myUserId       = providerId,
                                    myName         = providerName,
                                    otherName      = appt.customerName
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BookingRequestCard(
    appointment: AppointmentDocument,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onChat: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    ElevatedCard(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(if (appointment.status == "PENDING") 160.dp else 100.dp)
                    .background(
                        when (appointment.status.uppercase()) {
                            "CONFIRMED" -> AvailableGreen
                            "CANCELLED" -> UnavailableGrey
                            else        -> WarmGold
                        }
                    )
            )
            Column(modifier = Modifier.padding(14.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlushPink)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint     = DeepRose,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = appointment.customerName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            color      = DeepRose
                        )
                        Text(
                            text     = appointment.serviceName,
                            fontSize = 13.sp,
                            color    = RoseGold
                        )
                        if (appointment.customerPhone.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint     = Color(0xFF888888),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text     = appointment.customerPhone,
                                    fontSize = 12.sp,
                                    color    = Color(0xFF666666),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    val context = LocalContext.current
                    if (appointment.customerPhone.isNotBlank()) {
                        IconButton(
                            onClick  = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:${appointment.customerPhone}")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Call",
                                tint     = AvailableGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick  = onChat,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            tint     = RoseGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    ProviderStatusBadge(appointment.status)
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BlushPink.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                Text(
                    text     = "${strings.requestedAt} ${dateFmt.format(Date(appointment.appointmentDate))}",
                    fontSize = 12.sp,
                    color    = Color(0xFF888888)
                )

                if (appointment.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Edit, null, tint = RoseGold, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text      = appointment.notes,
                            fontSize  = 12.sp,
                            color     = Color(0xFF666666),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines  = 3,
                            overflow  = TextOverflow.Ellipsis
                        )
                    }
                }

                if (appointment.status == "PENDING") {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick        = onAccept,
                            modifier       = Modifier.weight(1f),
                            shape          = RoundedCornerShape(10.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = AvailableGreen),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(strings.accept, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick        = onDecline,
                            modifier       = Modifier.weight(1f),
                            shape          = RoundedCornerShape(10.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = UnavailableGrey.copy(alpha = 0.8f)),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(strings.decline, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusBadge(status: String) {
    val strings = LocalStrings.current
    val (bg, fg) = when (status.uppercase()) {
        "CONFIRMED" -> Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
        "CANCELLED" -> Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
        else        -> Pair(WarmGold.copy(alpha = 0.15f), WarmGold)
    }
    val label = when (status.uppercase()) {
        "CONFIRMED" -> strings.accept
        "CANCELLED" -> strings.decline
        else        -> strings.pending
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 11.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ── Profile tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(viewModel: ProviderViewModel) {
    val strings     = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Location card ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardSurface)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.sectionLocation, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
                HorizontalDivider(color = BlushPink)
                OutlinedTextField(
                    value         = viewModel.editDistrict,
                    onValueChange = viewModel::onDistrictChanged,
                    label         = { Text(strings.districtArea, fontSize = 13.sp) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors
                )
            }
        }

        // ── Services card ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardSurface)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.sectionServices, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
                HorizontalDivider(color = BlushPink)

                if (viewModel.editServices.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        viewModel.editServices.forEach { service ->
                            InputChip(
                                selected     = false,
                                onClick      = {},
                                label        = { Text(service, fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick  = { viewModel.removeService(service) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $service",
                                            modifier           = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor         = ChipInactive,
                                    labelColor             = DeepRose,
                                    trailingIconColor      = RoseGold,
                                    selectedContainerColor = ChipActive,
                                )
                            )
                        }
                    }
                } else {
                    Text(strings.noServicesAdded, fontSize = 13.sp, color = Color(0xFFAAAAAA))
                }

                // ── Price per service ──────────────────────────────────────
                if (viewModel.editServices.isNotEmpty()) {
                    Text(
                        strings.incomePriceLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = RoseGold,
                        modifier   = Modifier.padding(top = 4.dp)
                    )
                    viewModel.editServices.forEach { service ->
                        var priceText by remember(service) {
                            mutableStateOf((viewModel.editPrices[service] ?: 0).toString())
                        }
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                service,
                                fontSize = 13.sp,
                                color    = DeepRose,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value         = priceText,
                                onValueChange = { v ->
                                    priceText = v.filter { it.isDigit() }
                                    viewModel.setPriceForService(service, priceText.toIntOrNull() ?: 0)
                                },
                                singleLine        = true,
                                keyboardOptions   = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder       = { Text(strings.incomePriceHint, fontSize = 12.sp) },
                                modifier          = Modifier.width(110.dp),
                                shape             = RoundedCornerShape(10.dp),
                                colors            = fieldColors,
                                suffix            = { Text(strings.incomeAFN, fontSize = 11.sp, color = RoseGold) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = viewModel.newServiceDraft,
                        onValueChange = viewModel::onNewServiceDraftChanged,
                        label         = { Text(strings.addServiceLabel, fontSize = 12.sp) },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = fieldColors
                    )
                    IconButton(
                        onClick  = { viewModel.addService() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (viewModel.newServiceDraft.isNotBlank()) RoseGold else ChipInactive)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.addServiceLabel, tint = Color.White)
                    }
                }
            }
        }

        // ── Working hours card ────────────────────────────────────────────
        WorkingHoursSection(viewModel = viewModel)

        // ── Portfolio / sample-work photos ────────────────────────────────
        PortfolioSection(viewModel = viewModel)

        // ── Save button ───────────────────────────────────────────────────
        Button(
            onClick  = { viewModel.saveProfile() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
        ) {
            Text(strings.saveProfile, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // ── Decoy PIN ─────────────────────────────────────────────────────
        val decoyVm: DecoyPinViewModel = hiltViewModel()
        DecoyPinSection(uid = viewModel.providerId, decoyVm = decoyVm)

        // ── Change PIN ────────────────────────────────────────────────────
        val changePinVm: ChangePinViewModel = hiltViewModel()
        ChangePinSection(changePinVm = changePinVm)

        Spacer(Modifier.height(16.dp))
    }
}

// ── Portfolio section ───────────────────────────────────────────────────────────

@Composable
private fun PortfolioSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val gallery by viewModel.gallery.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.photoError = null
        viewModel.isUploadingPhoto = true
        scope.launch {
            when (val result = ImageUtils.uriToCompressedBytes(context, uri)) {
                is ImageUtils.BytesResult.Success  -> viewModel.addGalleryImage(result.bytes)
                is ImageUtils.BytesResult.TooLarge -> {
                    viewModel.isUploadingPhoto = false
                    viewModel.photoError = strings.photoTooLarge
                }
                is ImageUtils.BytesResult.Failed   -> {
                    viewModel.isUploadingPhoto = false
                    viewModel.photoError = strings.photoUploadFailed
                }
            }
        }
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(strings.portfolioTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            HorizontalDivider(color = BlushPink)

            if (gallery.isEmpty() && !viewModel.isUploadingPhoto) {
                Text(strings.noPhotosYet, fontSize = 13.sp, color = Color(0xFFAAAAAA))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (viewModel.isUploadingPhoto) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BlushPink)
                            ) {
                                CircularProgressIndicator(
                                    color       = RoseGold,
                                    strokeWidth = 2.dp,
                                    modifier    = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    items(gallery, key = { it.id }) { image ->
                        ProviderGalleryThumb(
                            image    = image,
                            onDelete = { viewModel.deleteGalleryImage(image.id) },
                            deleteCd = strings.deletePhoto
                        )
                    }
                }
            }

            viewModel.photoError?.let { err ->
                Text(err, fontSize = 12.sp, color = Color(0xFFD32F2F))
            }

            Button(
                onClick  = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled  = !viewModel.isUploadingPhoto,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (viewModel.isUploadingPhoto) strings.uploadingPhoto else strings.addPhoto,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProviderGalleryThumb(
    image: GalleryImageDocument,
    onDelete: () -> Unit,
    deleteCd: String
) {
    val thumbModifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(12.dp))
    Box(modifier = Modifier.size(96.dp)) {
        if (image.imageUrl.isNotBlank()) {
            AsyncImage(
                model              = image.imageUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = thumbModifier
            )
        } else {
            val bitmap = remember(image.id) { ImageUtils.base64ToBitmap(image.imageBase64) }
            if (bitmap != null) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = thumbModifier
                )
            } else {
                Box(modifier = thumbModifier.background(BlushPink))
            }
        }
        // Delete badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xCC000000))
                .clickable { onDelete() }
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = deleteCd,
                tint               = Color.White,
                modifier           = Modifier.size(14.dp)
            )
        }
    }
}

// ── Analytics tab ─────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsTab(analytics: ProviderAnalytics) {
    val strings = LocalStrings.current
    if (analytics.total == 0) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CalendarMonth, null, tint = BlushPink, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(strings.noDataYet, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DeepRose)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(strings.analyticsTotal,     analytics.total.toString(),     WarmGold,        Modifier.weight(1f))
                StatCard(strings.analyticsConfirmed, analytics.confirmed.toString(), AvailableGreen,  Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(strings.pending,            analytics.pending.toString(),   RoseGold,        Modifier.weight(1f))
                StatCard(strings.analyticsCancelled, analytics.cancelled.toString(), UnavailableGrey, Modifier.weight(1f))
            }
            if (analytics.byService.isNotEmpty()) {
                Text(strings.analyticsByService, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
                HorizontalDivider(color = BlushPink)
                analytics.byService.entries.sortedByDescending { it.value }.forEach { (service, count) ->
                    ServiceBarRow(service = service, count = count, total = analytics.total)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = Color(0xFF888888), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ServiceBarRow(service: String, count: Int, total: Int) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text     = service,
            fontSize = 13.sp,
            color    = DeepRose,
            modifier = Modifier.width(100.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BlushPink.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(RoseGold)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.Bold)
    }
}

// ── Working Hours Section ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkingHoursSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    // Map dayOfWeek constant → display label
    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        7    -> strings.daySat
        1    -> strings.daySun
        2    -> strings.dayMon
        3    -> strings.dayTue
        4    -> strings.dayWed
        5    -> strings.dayThu
        6    -> strings.dayFri
        else -> ""
    }

    // Ordered list: Sat(7), Sun(1), Mon(2), Tue(3), Wed(4), Thu(5), Fri(6)
    val orderedDays = listOf(7, 1, 2, 3, 4, 5, 6)

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.workingHoursTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            HorizontalDivider(color = BlushPink)

            orderedDays.forEach { dow ->
                val wh = viewModel.editWorkingHours.find { it.dayOfWeek == dow }
                if (wh != null) {
                    Column {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text       = dayLabel(dow),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color      = DeepRose,
                                modifier   = Modifier.weight(1f)
                            )
                            if (!wh.isOpen) {
                                Text(
                                    text     = strings.closedThisDay,
                                    fontSize = 12.sp,
                                    color    = Color(0xFFAAAAAA)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Switch(
                                checked         = wh.isOpen,
                                onCheckedChange = { viewModel.toggleDayOpen(dow) },
                                colors          = SwitchDefaults.colors(
                                    checkedThumbColor   = Color.White,
                                    checkedTrackColor   = RoseGold,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCCCCCC)
                                )
                            )
                        }
                        if (wh.isOpen) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            ) {
                                val openLabel  = "%02d:%02d".format(wh.openHour, wh.openMinute)
                                val closeLabel = "%02d:%02d".format(wh.closeHour, wh.closeMinute)

                                Text(strings.openTime, fontSize = 12.sp, color = Color(0xFF888888))
                                TextButton(
                                    onClick = {
                                        TimePickerDialog(context, { _, h, m ->
                                            viewModel.setDayOpenTime(dow, h, m)
                                        }, wh.openHour, wh.openMinute, true).show()
                                    }
                                ) {
                                    Text(openLabel, fontSize = 14.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                                }

                                Text("–", fontSize = 14.sp, color = Color(0xFF888888))

                                Text(strings.closeTime, fontSize = 12.sp, color = Color(0xFF888888))
                                TextButton(
                                    onClick = {
                                        TimePickerDialog(context, { _, h, m ->
                                            viewModel.setDayCloseTime(dow, h, m)
                                        }, wh.closeHour, wh.closeMinute, true).show()
                                    }
                                ) {
                                    Text(closeLabel, fontSize = 14.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BlushPink)

            // Slot duration selector
            Text(strings.slotDurationLabel, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DeepRose)
            val durations = listOf(
                30 to strings.slotDuration30,
                45 to strings.slotDuration45,
                60 to strings.slotDuration60,
                90 to strings.slotDuration90
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                durations.forEach { (minutes, label) ->
                    FilterChip(
                        selected = viewModel.editSlotDuration == minutes,
                        onClick  = { viewModel.setSlotDuration(minutes) },
                        label    = { Text(label, fontSize = 13.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChipActive,
                            selectedLabelColor     = Color.White,
                            containerColor         = ChipInactive,
                            labelColor             = DeepRose
                        )
                    )
                }
            }
        }
    }
}

// ── Income Tab ────────────────────────────────────────────────────────────────

@Composable
private fun IncomeTab(viewModel: ProviderViewModel) {
    val strings          = LocalStrings.current
    val analytics        by viewModel.analytics.collectAsStateWithLifecycle()
    val estimatedRevenue by viewModel.estimatedRevenue.collectAsStateWithLifecycle()
    val prices           = viewModel.editPrices

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Revenue summary ────────────────────────────────────────────────
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
                        Icon(Icons.Default.TrendingUp, null, tint = DeepRose, modifier = Modifier.size(32.dp))
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

private fun Int.format(): String {
    return "%,d".format(this)
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

// ── Provider Calendar Tab ─────────────────────────────────────────────────────

@Composable
private fun CalendarTab(allAppointments: List<AppointmentDocument>) {
    val strings     = LocalStrings.current
    val todayCal    = remember { java.util.Calendar.getInstance() }
    var displayYear  by remember { mutableIntStateOf(todayCal.get(java.util.Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(todayCal.get(java.util.Calendar.MONTH)) }
    var selectedDay  by remember { mutableStateOf<Int?>(null) }

    // Group visible-month appointments by day-of-month
    val appointmentsByDay = remember(allAppointments, displayYear, displayMonth) {
        allAppointments
            .filter { appt ->
                val c = java.util.Calendar.getInstance().apply { timeInMillis = appt.appointmentDate }
                c.get(java.util.Calendar.YEAR)  == displayYear &&
                c.get(java.util.Calendar.MONTH) == displayMonth
            }
            .groupBy { appt ->
                java.util.Calendar.getInstance()
                    .apply { timeInMillis = appt.appointmentDate }
                    .get(java.util.Calendar.DAY_OF_MONTH)
            }
    }

    val selectedDayAppts = selectedDay?.let { appointmentsByDay[it] ?: emptyList() } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── Calendar card ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardSurface)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Month / year header with prev–next arrows
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = {
                        selectedDay = null
                        if (displayMonth == 0) { displayMonth = 11; displayYear-- } else displayMonth--
                    }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = RoseGold)
                    }
                    val monthLabel = remember(displayYear, displayMonth) {
                        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(
                            java.util.Calendar.getInstance().apply { set(displayYear, displayMonth, 1) }.time
                        )
                    }
                    Text(monthLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepRose)
                    IconButton(onClick = {
                        selectedDay = null
                        if (displayMonth == 11) { displayMonth = 0; displayYear++ } else displayMonth++
                    }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = RoseGold)
                    }
                }

                // Day-of-week header row
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { d ->
                        Text(
                            text      = d,
                            fontSize  = 11.sp,
                            color     = Color(0xFF999999),
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                // Day cells
                val firstCal    = remember(displayYear, displayMonth) {
                    java.util.Calendar.getInstance().apply { set(displayYear, displayMonth, 1) }
                }
                val firstDow    = firstCal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0=Sun
                val daysInMonth = firstCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val todayYear   = todayCal.get(java.util.Calendar.YEAR)
                val todayMonth  = todayCal.get(java.util.Calendar.MONTH)
                val todayDay    = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
                val rowCount    = (firstDow + daysInMonth + 6) / 7

                (0 until rowCount).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { col ->
                            val day = row * 7 + col - firstDow + 1
                            if (day in 1..daysInMonth) {
                                val hasAppts   = appointmentsByDay.containsKey(day)
                                val isToday    = displayYear == todayYear && displayMonth == todayMonth && day == todayDay
                                val isSelected = selectedDay == day

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier         = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> RoseGold
                                                isToday    -> BlushPink
                                                else       -> Color.Transparent
                                            }
                                        )
                                        .clickable { selectedDay = if (selectedDay == day) null else day }
                                ) {
                                    Text(
                                        text       = "$day",
                                        fontSize   = 13.sp,
                                        color      = when {
                                            isSelected -> Color.White
                                            isToday    -> DeepRose
                                            else       -> Color(0xFF333333)
                                        },
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign  = TextAlign.Center
                                    )
                                    if (hasAppts) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 3.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else DeepRose)
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Appointment list for selected day ─────────────────────────────
        when {
            selectedDay == null -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Text(strings.calendarTapDay, fontSize = 14.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)
                }
            }
            selectedDayAppts.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Text(strings.calendarNoAppointments, fontSize = 14.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedDayAppts
                        .sortedBy { it.appointmentDate }
                        .forEach { appt -> CalendarAppointmentRow(appt) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CalendarAppointmentRow(appt: AppointmentDocument) {
    val timeFmt = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val context = LocalContext.current

    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BlushPink)
            ) {
                Text(
                    text       = timeFmt.format(java.util.Date(appt.appointmentDate)),
                    fontSize   = 11.sp,
                    color      = DeepRose,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appt.customerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                Text(appt.serviceName,  fontSize   = 12.sp,               color = RoseGold)
                if (appt.customerPhone.isNotBlank()) {
                    Text(appt.customerPhone, fontSize = 11.sp, color = Color(0xFF888888))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                ProviderStatusBadge(appt.status)
                if (appt.customerPhone.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    IconButton(
                        onClick  = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:${appt.customerPhone}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint     = AvailableGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Provider Reviews Tab ─────────────────────────────────────────────────────

@Composable
private fun ReviewsTab(
    reviews: List<ReviewDocument>,
    onReply: (String, String) -> Unit
) {
    val strings = LocalStrings.current
    var expandedReviewId by remember { mutableStateOf<String?>(null) }

    if (reviews.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.noReviewsYet, color = Color(0xFF888888), fontSize = 14.sp)
        }
        return
    }

    val avgRating = reviews.map { it.rating }.average()
    val starCounts = (5 downTo 1).map { star -> star to reviews.count { it.rating == star } }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Rating summary header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F3))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%.1f".format(avgRating),
                            fontSize   = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color      = DeepRose
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            starCounts.forEach { (star, count) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$star", fontSize = 12.sp, color = RoseGold,
                                        modifier = Modifier.width(12.dp))
                                    Icon(Icons.Default.Star, contentDescription = null,
                                        tint = WarmGold, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(6.dp))
                                    LinearProgressIndicator(
                                        progress   = { if (reviews.isNotEmpty()) count.toFloat() / reviews.size else 0f },
                                        modifier   = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color      = WarmGold,
                                        trackColor = Color(0xFFE0C8CF)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("$count", fontSize = 11.sp, color = Color(0xFF888888),
                                        modifier = Modifier.width(20.dp))
                                }
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
                    Text(
                        "${reviews.size} ${strings.reviews}",
                        fontSize = 12.sp, color = Color(0xFF888888),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        // Review cards
        items(reviews, key = { it.id }) { review ->
            ProviderReviewCard(
                review     = review,
                isExpanded = expandedReviewId == review.id,
                onExpand   = {
                    expandedReviewId = if (expandedReviewId == review.id) null else review.id
                },
                onReply    = { text -> onReply(review.id, text) }
            )
        }
    }
}

@Composable
private fun ProviderReviewCard(
    review: ReviewDocument,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onReply: (String) -> Unit
) {
    val strings = LocalStrings.current
    var draftReply by remember(review.id) { mutableStateOf(review.providerReply) }
    val dateStr = remember(review.createdAt) {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(review.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header row: name + date
            Row(
                modifier       = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(review.customerName, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = DeepRose)
                    Text(dateStr, fontSize = 11.sp, color = Color(0xFF888888))
                }
                // Stars
                Row {
                    (1..5).forEach { i ->
                        Icon(
                            if (i <= review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint     = WarmGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            // Comment
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(review.comment, fontSize = 13.sp, color = Color(0xFF333333))
            }
            // Existing reply (collapsed view)
            if (review.providerReply.isNotBlank() && !isExpanded) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 3.dp,
                            color = Color(0xFFE8A0B0),
                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 8.dp,
                                topEnd = 8.dp, bottomEnd = 8.dp)
                        )
                        .background(Color(0xFFFFF0F3), RoundedCornerShape(topStart = 0.dp,
                            bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                        .padding(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.providerReplied, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = RoseGold)
                        Spacer(Modifier.height(2.dp))
                        Text(review.providerReply, fontSize = 12.sp, color = Color(0xFF444444))
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.RateReview, contentDescription = null,
                            tint = RoseGold, modifier = Modifier.size(16.dp))
                    }
                }
            }
            // Reply input (expanded)
            if (isExpanded) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = draftReply,
                    onValueChange = { draftReply = it },
                    placeholder   = { Text(strings.providerReplyHint, fontSize = 13.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    minLines      = 2
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onExpand) {
                        Text(strings.cancel, color = Color(0xFF888888))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onReply(draftReply)
                            onExpand()
                        },
                        enabled = draftReply.isNotBlank(),
                        colors  = ButtonDefaults.buttonColors(containerColor = DeepRose)
                    ) {
                        Text(strings.providerReplySubmit, color = Color.White)
                    }
                }
            } else if (review.providerReply.isBlank()) {
                // Show reply button when no reply yet
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick  = onExpand,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = null,
                        tint = RoseGold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.providerReplySubmit, fontSize = 12.sp, color = RoseGold)
                }
            }
        }
    }
}
