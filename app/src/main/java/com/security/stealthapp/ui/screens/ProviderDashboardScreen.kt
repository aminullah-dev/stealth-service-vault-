package com.security.stealthapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.security.stealthapp.viewmodel.ProviderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDashboardScreen(
    onLockTriggered: () -> Unit,
    viewModel: ProviderViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.lockTriggered) {
        if (viewModel.lockTriggered) {
            viewModel.resetLockTrigger()
            onLockTriggered()
        }
    }

    val salon               by viewModel.salon.collectAsStateWithLifecycle()
    val isAvailable         by viewModel.isAvailable.collectAsStateWithLifecycle()
    val pendingAppointments by viewModel.pendingAppointments.collectAsStateWithLifecycle()
    var selectedTab         by remember { mutableIntStateOf(0) }

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
                                text     = "Provider · Private · Discreet",
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    actions = {
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
                                Text("Requests", fontSize = 14.sp)
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
                                            text     = "${pendingAppointments.size}",
                                            fontSize = 10.sp,
                                            color    = Color.White,
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
                        text     = { Text("My Profile", fontSize = 14.sp) }
                    )
                }

                // ── Tab content ───────────────────────────────────────────
                when (selectedTab) {
                    0 -> BookingRequestsTab(
                        appointments = pendingAppointments,
                        onAccept     = { viewModel.acceptAppointment(it) },
                        onDecline    = { viewModel.declineAppointment(it) }
                    )
                    1 -> ProfileTab(viewModel = viewModel)
                }
            }
        }

        // ── Save success snackbar-style dialog ────────────────────────────
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
                title = { Text("Profile Saved", fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text("Your salon details have been updated.", fontSize = 14.sp, color = Color(0xFF555555)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissSaveSuccess() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text("OK", color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }
    }
}

// ── Availability toggle card ───────────────────────────────────────────────────

@Composable
private fun AvailabilityCard(
    isAvailable: Boolean,
    onToggle: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (isAvailable) AvailableGreen else UnavailableGrey,
        animationSpec = tween(durationMillis = 400),
        label = "statusColor"
    )

    Card(
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    text       = "Availability Status",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = DeepRose
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = if (isAvailable) "Accepting bookings" else "Not accepting bookings",
                    fontSize = 12.sp,
                    color    = statusColor
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked         = isAvailable,
                    onCheckedChange = { onToggle() },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor       = Color.White,
                        checkedTrackColor       = AvailableGreen,
                        uncheckedThumbColor     = Color.White,
                        uncheckedTrackColor     = UnavailableGrey.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

// ── Booking requests tab ──────────────────────────────────────────────────────

@Composable
private fun BookingRequestsTab(
    appointments: List<AppointmentDocument>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    if (appointments.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint     = BlushPink,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = "No pending requests",
                    fontSize  = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color     = DeepRose
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "New booking requests will appear here.",
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
                    onDecline   = { onDecline(appt.id) }
                )
            }
        }
    }
}

@Composable
private fun BookingRequestCard(
    appointment: AppointmentDocument,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
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
                AppointmentStatusBadge(appointment.status)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BlushPink.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))

            Text(
                text     = "Requested for: ${dateFmt.format(Date(appointment.appointmentDate))}",
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
                        onClick   = onAccept,
                        modifier  = Modifier.weight(1f),
                        shape     = RoundedCornerShape(10.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = AvailableGreen),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Accept", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick   = onDecline,
                        modifier  = Modifier.weight(1f),
                        shape     = RoundedCornerShape(10.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = UnavailableGrey.copy(alpha = 0.8f)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Decline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentStatusBadge(status: String) {
    val (bg, fg) = when (status.uppercase()) {
        "CONFIRMED" -> Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
        "CANCELLED" -> Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
        else        -> Pair(WarmGold.copy(alpha = 0.15f), WarmGold)
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

// ── Profile tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(viewModel: ProviderViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text       = "Salon Details",
            fontWeight = FontWeight.Bold,
            fontSize   = 16.sp,
            color      = DeepRose
        )

        // ── District field ────────────────────────────────────────────────
        OutlinedTextField(
            value         = viewModel.editDistrict,
            onValueChange = viewModel::onDistrictChanged,
            label         = { Text("District / Area", fontSize = 13.sp) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = RoseGold,
                unfocusedBorderColor = ChipInactive,
                focusedLabelColor    = RoseGold,
                cursorColor          = RoseGold
            )
        )

        // ── Services section ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text       = "Services Offered",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = DeepRose
            )

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
                Text(
                    text     = "No services added yet.",
                    fontSize = 13.sp,
                    color    = Color(0xFFAAAAAA)
                )
            }

            // Add new service
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = viewModel.newServiceDraft,
                    onValueChange = viewModel::onNewServiceDraftChanged,
                    label         = { Text("Add service…", fontSize = 12.sp) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = RoseGold,
                        unfocusedBorderColor = ChipInactive,
                        focusedLabelColor    = RoseGold,
                        cursorColor          = RoseGold
                    )
                )
                IconButton(
                    onClick  = { viewModel.addService() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.newServiceDraft.isNotBlank()) RoseGold else ChipInactive)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add service",
                        tint               = Color.White
                    )
                }
            }
        }

        HorizontalDivider(color = BlushPink)

        // ── Save button ───────────────────────────────────────────────────
        Button(
            onClick  = { viewModel.saveProfile() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
        ) {
            Text(
                text       = "Save Profile",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
