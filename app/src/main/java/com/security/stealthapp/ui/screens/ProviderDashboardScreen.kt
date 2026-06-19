package com.security.stealthapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.data.firebase.AppointmentDocument
import com.security.stealthapp.navigation.Screen
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
import com.security.stealthapp.viewmodel.LanguageViewModel
import com.security.stealthapp.viewmodel.ProviderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDashboardScreen(
    onLockTriggered: () -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: ProviderViewModel = hiltViewModel(),
    langVm: LanguageViewModel    = hiltViewModel()
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
                    .height(if (appointment.status == "PENDING") 140.dp else 90.dp)
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

        Spacer(Modifier.height(16.dp))
    }
}
