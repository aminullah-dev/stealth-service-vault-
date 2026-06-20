package com.security.stealthapp.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.navigation.Screen
import com.security.stealthapp.ui.theme.AppLanguage
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
import com.security.stealthapp.util.NotificationHelper
import com.security.stealthapp.viewmodel.DashboardViewModel
import com.security.stealthapp.viewmodel.ExportPhase
import com.security.stealthapp.viewmodel.ExportViewModel
import com.security.stealthapp.viewmodel.LanguageViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Avatar colors cycle through the brand palette based on name's first character
private val avatarColors = listOf(
    Color(0xFFB76E79),
    Color(0xFF9C6B8A),
    Color(0xFFD4A853),
    Color(0xFF8B3A47),
    Color(0xFF6D8B74),
    Color(0xFF7B6FA0),
)

private fun avatarColor(name: String): Color =
    avatarColors[name.first().lowercaseChar().code % avatarColors.size]

private data class BookingIntent(
    val salon: SalonDocument,
    val service: String,
    val dateMs: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenDashboardScreen(
    onLockTriggered: () -> Unit,
    onNavigate: (String) -> Unit      = {},
    viewModel: DashboardViewModel     = hiltViewModel(),
    langVm: LanguageViewModel         = hiltViewModel(),
    exportVm: ExportViewModel         = hiltViewModel()
) {
    val strings     = LocalStrings.current
    val context     = LocalContext.current
    val latestStrings by rememberUpdatedState(strings)

    // Request POST_NOTIFICATIONS permission on Android 13+
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by the OS */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Show a local notification when a booking status changes (PENDING → CONFIRMED/CANCELLED)
    LaunchedEffect(Unit) {
        viewModel.bookingStatusChange.collect { change ->
            val title = latestStrings.bookingUpdatedTitle
            val body  = if (change.newStatus == "CONFIRMED")
                latestStrings.bookingConfirmedText(change.salonName)
            else
                latestStrings.bookingDeclinedText(change.salonName)
            NotificationHelper.showBookingUpdate(context, title, body)
        }
    }

    LaunchedEffect(viewModel.lockTriggered) {
        if (viewModel.lockTriggered) {
            viewModel.resetLockTrigger()
            onLockTriggered()
        }
    }

    // Open share sheet when CSV export completes
    LaunchedEffect(exportVm.phase) {
        if (exportVm.phase == ExportPhase.DONE) {
            val uri = exportVm.shareUri ?: return@LaunchedEffect
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, strings.exportShareTitle))
            exportVm.reset()
        }
    }

    val filteredSalons            by viewModel.filteredSalons.collectAsStateWithLifecycle()
    val myAppointments            by viewModel.myAppointments.collectAsStateWithLifecycle()
    val selectedCategoryIndex     by viewModel.selectedCategoryIndex.collectAsStateWithLifecycle()
    val selectedNeighborhoodIndex by viewModel.selectedNeighborhoodIndex.collectAsStateWithLifecycle()
    val isOffline                 by viewModel.isOffline.collectAsStateWithLifecycle()
    val currentUserName           by viewModel.currentUserName.collectAsStateWithLifecycle()
    val favoriteIds               by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val showFavoritesOnly         by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val broadcasts                by viewModel.broadcasts.collectAsStateWithLifecycle()
    val searchQuery               by viewModel.searchQuery.collectAsStateWithLifecycle()

    val categoryLabels = listOf(
        strings.categoryAll, strings.categoryHair, strings.categoryMakeup,
        strings.categoryNails, strings.categorySkincare, strings.categoryEyebrows
    )
    val neighborhoodLabels = listOf(
        strings.neighborhoodAll,
        strings.neighborhood1, strings.neighborhood3,
        strings.neighborhood6, strings.neighborhood9,
        strings.neighborhood11, strings.neighborhood13
    )

    var showNeighborhoodMenu by remember { mutableStateOf(false) }
    var showBookingsSheet    by remember { mutableStateOf(false) }
    var showLangPicker       by remember { mutableStateOf(false) }
    var showProfileSheet     by remember { mutableStateOf(false) }
    val sheetState           = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var bookingIntent     by remember { mutableStateOf<BookingIntent?>(null) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var showDatePicker    by remember { mutableStateOf(false) }
    var showSlotPicker    by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    // Reschedule + review flow state
    var rescheduleTarget    by remember { mutableStateOf<AppointmentDocument?>(null) }
    var reschedulePickedDate by remember { mutableStateOf<Long?>(null) }
    var showRescheduleDate  by remember { mutableStateOf(false) }
    var showRescheduleTime  by remember { mutableStateOf(false) }
    val rescheduleDateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val rescheduleTimeState = rememberTimePickerState(initialHour = 10, initialMinute = 0)
    var reviewTarget        by remember { mutableStateOf<AppointmentDocument?>(null) }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("SafeBeauty", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DeepRose)
                            Text(strings.taglineCustomer, fontSize = 11.sp, color = RoseGold)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick  = { exportVm.export() },
                            enabled  = exportVm.phase != ExportPhase.WORKING
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = strings.exportTitle,
                                tint               = RoseGold
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavoritesOnly() }) {
                            Icon(
                                imageVector        = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = strings.favorites,
                                tint               = if (showFavoritesOnly) DeepRose else RoseGold
                            )
                        }
                        IconButton(onClick = { showBookingsSheet = true }) {
                            BadgedBox(badge = {
                                if (myAppointments.isNotEmpty()) {
                                    Badge(containerColor = DeepRose) {
                                        Text("${myAppointments.size}", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.CalendarMonth, strings.myBookings, tint = DeepRose)
                            }
                        }
                        IconButton(onClick = { showProfileSheet = true }) {
                            Icon(Icons.Default.Person, strings.myProfile, tint = RoseGold)
                        }
                        IconButton(onClick = { showLangPicker = true }) {
                            Icon(Icons.Default.Language, strings.languagePickerTitle, tint = DeepRose)
                        }
                        IconButton(onClick = { viewModel.triggerLock() }) {
                            Icon(Icons.Default.Lock, strings.lock, tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                // ── Offline banner ────────────────────────────────────────────
                AnimatedVisibility(visible = isOffline) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFC0392B))
                            .padding(vertical = 6.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text       = strings.offlineBanner,
                            color      = Color.White,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // ── Broadcast announcements ───────────────────────────────────
                if (broadcasts.isNotEmpty()) {
                    BroadcastBanner(broadcasts = broadcasts)
                }

                // ── Search bar ────────────────────────────────────────────────
                androidx.compose.material3.OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder   = { Text(strings.searchHint, fontSize = 13.sp, color = RoseGold) },
                    leadingIcon   = {
                        Icon(Icons.Default.Search, null, tint = RoseGold, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon  = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = ChipInactive, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else null,
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors        = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = RoseGold,
                        unfocusedBorderColor = ChipInactive,
                        cursorColor          = RoseGold
                    )
                )

                // ── Category chips ────────────────────────────────────────────
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(categoryLabels) { index, label ->
                        FilterChip(
                            selected = selectedCategoryIndex == index,
                            onClick  = { viewModel.selectCategory(index) },
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

                // ── Neighborhood picker ───────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    OutlinedButton(
                        onClick  = { showNeighborhoodMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ChipInactive),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose)
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(15.dp), tint = RoseGold)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            neighborhoodLabels.getOrElse(selectedNeighborhoodIndex) { strings.neighborhoodAll },
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            color    = DeepRose
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = RoseGold)
                    }
                    DropdownMenu(
                        expanded         = showNeighborhoodMenu,
                        onDismissRequest = { showNeighborhoodMenu = false },
                        modifier         = Modifier.background(ElegantCream)
                    ) {
                        neighborhoodLabels.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text    = { Text(label, fontSize = 13.sp, color = DeepRose) },
                                onClick = { viewModel.selectNeighborhood(index); showNeighborhoodMenu = false }
                            )
                        }
                    }
                }

                // ── Results count ─────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = BlushPink)
                    Text(
                        text = if (filteredSalons.isEmpty()) strings.noProvidersTitle
                               else strings.providersFound(filteredSalons.size),
                        fontSize = 11.sp,
                        color    = RoseGold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = BlushPink)
                }

                // ── Salon list / empty state ──────────────────────────────────
                if (filteredSalons.isEmpty()) {
                    SalonEmptyState(
                        favoritesOnly = showFavoritesOnly,
                        modifier      = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier            = Modifier.fillMaxSize()
                    ) {
                        items(filteredSalons, key = { it.id }) { salon ->
                            SalonCard(
                                salon            = salon,
                                isFavorite       = favoriteIds.contains(salon.id),
                                onToggleFavorite = { viewModel.toggleFavorite(salon.id) },
                                onBook           = { bookingIntent = BookingIntent(salon, ""); showServiceDialog = true }
                            )
                        }
                    }
                }
            }
        }

        // ── Language picker dialog ────────────────────────────────────────────
        if (showLangPicker) {
            LanguagePickerDialog(
                current  = langVm.language.value,
                onPick   = { langVm.setLanguage(it); showLangPicker = false },
                onDismiss = { showLangPicker = false }
            )
        }

        // ── Customer profile sheet ────────────────────────────────────────────
        if (showProfileSheet) {
            val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showProfileSheet = false },
                sheetState       = profileSheetState,
                containerColor   = ElegantCream
            ) {
                CustomerProfileSheetContent(
                    name        = viewModel.editName,
                    onNameChange = { viewModel.onEditNameChanged(it) },
                    onSave      = { viewModel.saveCustomerProfile(); showProfileSheet = false },
                    onDismiss   = { showProfileSheet = false }
                )
            }
        }

        // ── Profile save success ──────────────────────────────────────────────
        if (viewModel.profileSaveSuccess) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissProfileSaveSuccess() },
                icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
                title = { Text(strings.profileSavedCustomer, fontWeight = FontWeight.Bold, color = DeepRose) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissProfileSaveSuccess() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── Step 1: Service selection ─────────────────────────────────────────
        if (showServiceDialog) {
            val salon = bookingIntent?.salon
            AlertDialog(
                onDismissRequest = { showServiceDialog = false; bookingIntent = null },
                title = { Text(strings.chooseService, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        salon?.services?.forEach { service ->
                            Button(
                                onClick = {
                                    showServiceDialog = false
                                    bookingIntent = bookingIntent?.copy(service = service)
                                    showDatePicker  = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = BlushPink)
                            ) {
                                Text(service, fontSize = 14.sp, color = DeepRose, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton  = {},
                dismissButton  = {
                    TextButton(onClick = { showServiceDialog = false; bookingIntent = null }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Step 2: Date picker ───────────────────────────────────────────────
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false; bookingIntent = null },
                confirmButton = {
                    Button(
                        onClick = {
                            showDatePicker = false
                            val selectedDate = datePickerState.selectedDateMillis
                            if (selectedDate != null && bookingIntent != null) {
                                bookingIntent = bookingIntent?.copy(dateMs = selectedDate)
                                viewModel.loadSlotsForDate(bookingIntent!!.salon, selectedDate)
                            }
                            showSlotPicker = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.next, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false; bookingIntent = null }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // ── Step 3: Slot picker ───────────────────────────────────────────────
        if (showSlotPicker) {
            AlertDialog(
                onDismissRequest = { showSlotPicker = false; bookingIntent = null; viewModel.clearSlots() },
                title = { Text(strings.selectTimeSlot, fontWeight = FontWeight.Bold, color = DeepRose) },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        when {
                            viewModel.slotsLoading -> CircularProgressIndicator(color = RoseGold, modifier = Modifier.align(Alignment.Center))
                            viewModel.noWorkingHours || viewModel.availableSlots.isEmpty() -> {
                                Text(strings.noSlotsAvailable, color = RoseGold, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
                            }
                            else -> {
                                val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(viewModel.availableSlots) { slotMs ->
                                        Button(
                                            onClick = {
                                                showSlotPicker = false
                                                val intent = bookingIntent
                                                if (intent != null) {
                                                    viewModel.bookService(intent.salon, intent.service, slotMs)
                                                }
                                                bookingIntent = null
                                                viewModel.clearSlots()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BlushPink)
                                        ) {
                                            Text(timeFmt.format(Date(slotMs)), color = DeepRose, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSlotPicker = false; bookingIntent = null; viewModel.clearSlots() }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Booking confirmation ──────────────────────────────────────────────
        viewModel.bookingConfirmSalonName?.let { salonName ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissConfirmation() },
                icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
                title = { Text(strings.bookingRequestSent, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text(strings.bookingConfirmText(salonName), fontSize = 14.sp, color = Color(0xFF555555)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissConfirmation() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── My Bookings sheet ─────────────────────────────────────────────────
        if (showBookingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBookingsSheet = false },
                sheetState       = sheetState,
                containerColor   = ElegantCream
            ) {
                BookingsSheetContent(
                    appointments = myAppointments,
                    onDismiss    = { showBookingsSheet = false },
                    onChatClick  = { appt ->
                        showBookingsSheet = false
                        onNavigate(
                            Screen.Chat.build(
                                conversationId = "${viewModel.customerId}_${appt.salonId}",
                                myUserId       = viewModel.customerId,
                                myName         = currentUserName,
                                otherName      = appt.salonName
                            )
                        )
                    },
                    onCancelClick     = { appt -> viewModel.cancelAppointment(appt.id) },
                    onRescheduleClick = { appt ->
                        showBookingsSheet    = false
                        rescheduleTarget     = appt
                        reschedulePickedDate = null
                        showRescheduleDate   = true
                    },
                    onReviewClick     = { appt ->
                        showBookingsSheet = false
                        reviewTarget      = appt
                    }
                )
            }
        }

        // ── Reschedule: date picker ───────────────────────────────────────────
        if (showRescheduleDate) {
            DatePickerDialog(
                onDismissRequest = { showRescheduleDate = false; rescheduleTarget = null },
                confirmButton = {
                    Button(
                        onClick = {
                            showRescheduleDate   = false
                            reschedulePickedDate = rescheduleDateState.selectedDateMillis
                            showRescheduleTime   = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.next, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showRescheduleDate = false; rescheduleTarget = null }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                }
            ) {
                DatePicker(state = rescheduleDateState)
            }
        }

        // ── Reschedule: time picker ───────────────────────────────────────────
        if (showRescheduleTime) {
            AlertDialog(
                onDismissRequest = { showRescheduleTime = false; rescheduleTarget = null },
                title = { Text(strings.rescheduleTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        TimePicker(state = rescheduleTimeState)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRescheduleTime = false
                            val target = rescheduleTarget
                            val dateMs = reschedulePickedDate
                            if (target != null && dateMs != null) {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = dateMs
                                    set(Calendar.HOUR_OF_DAY, rescheduleTimeState.hour)
                                    set(Calendar.MINUTE,      rescheduleTimeState.minute)
                                    set(Calendar.SECOND,      0)
                                }
                                viewModel.rescheduleAppointment(target.id, cal.timeInMillis)
                            }
                            rescheduleTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.reschedule, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showRescheduleTime = false; rescheduleTarget = null }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Review dialog ─────────────────────────────────────────────────────
        reviewTarget?.let { appt ->
            ReviewDialog(
                salonName = appt.salonName,
                onSubmit  = { rating, comment ->
                    viewModel.submitReview(appt.salonId, rating, comment)
                    reviewTarget = null
                },
                onDismiss = { reviewTarget = null }
            )
        }

        // ── Review thanks confirmation ────────────────────────────────────────
        if (viewModel.reviewThanksShown) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissReviewThanks() },
                icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
                title = { Text(strings.reviewThanks, fontWeight = FontWeight.Bold, color = DeepRose, textAlign = TextAlign.Center) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissReviewThanks() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }
    }
}

// ── Review dialog ───────────────────────────────────────────────────────────

@Composable
private fun ReviewDialog(
    salonName: String,
    onSubmit: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings    = LocalStrings.current
    var rating     by remember { mutableStateOf(0) }
    var comment    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.rateExperience, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose)
                if (salonName.isNotBlank()) {
                    Text(salonName, fontSize = 13.sp, color = RoseGold)
                }
            }
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector        = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star",
                                tint               = WarmGold,
                                modifier           = Modifier.size(34.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value         = comment,
                    onValueChange = { comment = it },
                    placeholder   = { Text(strings.reviewCommentHint, fontSize = 13.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    minLines      = 2,
                    colors        = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = RoseGold,
                        unfocusedBorderColor = ChipInactive,
                        cursorColor          = RoseGold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment) },
                enabled = rating >= 1,
                colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
            ) { Text(strings.submit, color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = RoseGold) }
        },
        containerColor = ElegantCream
    )
}

// ── Salon card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalonCard(
    salon: SalonDocument,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBook: () -> Unit
) {
    val strings = LocalStrings.current
    val color   = remember(salon.salonName) { avatarColor(salon.salonName) }

    ElevatedCard(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color)
                ) {
                    Text(
                        text       = salon.salonName.first().toString(),
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = salon.salonName,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = DeepRose,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = RoseGold, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text     = salon.district,
                            fontSize = 12.sp,
                            color    = Color(0xFF888888),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(WarmGold.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("%.1f".format(salon.rating), fontSize = 12.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector        = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = strings.favorites,
                        tint               = if (isFavorite) DeepRose else RoseGold,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            if (salon.services.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp)
                ) {
                    salon.services.forEach { service ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(BlushPink.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(service, fontSize = 11.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BlushPink.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (salon.isAvailable) AvailableGreen else UnavailableGrey)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = if (salon.isAvailable) strings.availableNow else strings.notAvailable,
                    fontSize   = 12.sp,
                    color      = if (salon.isAvailable) AvailableGreen else UnavailableGrey,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f)
                )
                Button(
                    onClick        = onBook,
                    enabled        = salon.isAvailable,
                    shape          = RoundedCornerShape(12.dp),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = RoseGold,
                        disabledContainerColor = UnavailableGrey.copy(alpha = 0.25f)
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(strings.book, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

// ── Broadcast banner ──────────────────────────────────────────────────────────

@Composable
private fun BroadcastBanner(broadcasts: List<BroadcastDocument>) {
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
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
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = dateFmt.format(Date(broadcast.createdAt)),
                        fontSize = 11.sp,
                        color    = RoseGold
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun SalonEmptyState(favoritesOnly: Boolean = false, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Box(contentAlignment = Alignment.Center, modifier = modifier.padding(40.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(BlushPink.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (favoritesOnly) Icons.Default.FavoriteBorder else Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = RoseGold,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text       = if (favoritesOnly) strings.noFavoritesTitle else strings.noProvidersTitle,
                fontSize   = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color      = DeepRose,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = if (favoritesOnly) strings.noFavoritesSubtext else strings.noProvidersSubtext,
                fontSize  = 13.sp,
                color     = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Bookings bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingsSheetContent(
    appointments: List<AppointmentDocument>,
    onDismiss: () -> Unit,
    onChatClick: (AppointmentDocument) -> Unit = {},
    onCancelClick: (AppointmentDocument) -> Unit = {},
    onRescheduleClick: (AppointmentDocument) -> Unit = {},
    onReviewClick: (AppointmentDocument) -> Unit = {}
) {
    val strings = LocalStrings.current
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    var cancelTarget by remember { mutableStateOf<AppointmentDocument?>(null) }
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
            Text(strings.myBookings, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DeepRose)
            TextButton(onClick = onDismiss) { Text(strings.close, color = RoseGold) }
        }
        HorizontalDivider(color = BlushPink)
        Spacer(Modifier.height(12.dp))

        if (appointments.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.fillMaxWidth().padding(vertical = 40.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarMonth, null, tint = BlushPink, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(strings.noBookingsTitle, fontSize = 15.sp, color = DeepRose, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.noBookingsSubtext, fontSize = 13.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)
                }
            }
        } else {
            appointments.forEach { appt ->
                Card(
                    shape    = RoundedCornerShape(14.dp),
                    colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(RoseGold)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appt.serviceName, fontWeight = FontWeight.SemiBold, color = DeepRose, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (appt.salonName.isNotBlank()) {
                                    Text(appt.salonName, fontSize = 12.sp, color = RoseGold)
                                }
                                Text(
                                    "📅 ${dateFmt.format(Date(appt.appointmentDate))}",
                                    fontSize = 11.sp,
                                    color    = Color(0xFFAAAAAA)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick  = { onChatClick(appt) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = strings.chat,
                                    tint     = RoseGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            StatusChip(appt.status)
                        }
                        val canReschedule = appt.status == "PENDING" || appt.status == "CONFIRMED"
                        val canReview     = appt.status == "CONFIRMED"
                        val canCancel     = appt.status == "PENDING"
                        if (canReschedule || canReview || canCancel) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (canReschedule) {
                                    OutlinedButton(
                                        onClick        = { onRescheduleClick(appt) },
                                        shape          = RoundedCornerShape(8.dp),
                                        border         = androidx.compose.foundation.BorderStroke(1.dp, ChipInactive),
                                        colors         = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.reschedule, fontSize = 12.sp)
                                    }
                                }
                                if (canReview) {
                                    OutlinedButton(
                                        onClick        = { onReviewClick(appt) },
                                        shape          = RoundedCornerShape(8.dp),
                                        border         = androidx.compose.foundation.BorderStroke(1.dp, WarmGold),
                                        colors         = ButtonDefaults.outlinedButtonColors(contentColor = WarmGold),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.RateReview, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.leaveReview, fontSize = 12.sp)
                                    }
                                }
                                if (canCancel) {
                                    OutlinedButton(
                                        onClick        = { cancelTarget = appt },
                                        shape          = RoundedCornerShape(8.dp),
                                        border         = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC0392B)),
                                        colors         = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC0392B)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(strings.cancelAppointment, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let { appt ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text(strings.cancelConfirmTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
            text  = { Text(strings.cancelConfirmText, fontSize = 14.sp, color = Color(0xFF555555)) },
            confirmButton = {
                Button(
                    onClick = { onCancelClick(appt); cancelTarget = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))
                ) { Text(strings.cancelAppointment, color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) {
                    Text(strings.close, color = RoseGold)
                }
            },
            containerColor = ElegantCream
        )
    }
}

@Composable
private fun StatusChip(status: String) {
    val strings = LocalStrings.current
    val (bg, fg) = when (status) {
        "CONFIRMED" -> Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
        "CANCELLED" -> Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
        else        -> Pair(WarmGold.copy(alpha = 0.15f), WarmGold)
    }
    val label = when (status) {
        "CONFIRMED" -> strings.accept
        "CANCELLED" -> strings.decline
        else        -> strings.pending
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ── Customer profile sheet ────────────────────────────────────────────────────

@Composable
private fun CustomerProfileSheetContent(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(strings.editProfileTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DeepRose)
            TextButton(onClick = onDismiss) { Text(strings.close, color = RoseGold) }
        }
        HorizontalDivider(color = BlushPink)
        androidx.compose.material3.OutlinedTextField(
            value         = name,
            onValueChange = onNameChange,
            label         = { Text(strings.fullName, fontSize = 13.sp) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            colors        = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = RoseGold,
                unfocusedBorderColor = ChipInactive,
                cursorColor          = RoseGold,
                focusedLabelColor    = RoseGold
            )
        )
        Button(
            onClick  = onSave,
            enabled  = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
        ) {
            Text(strings.saveProfile, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Language picker dialog ────────────────────────────────────────────────────

@Composable
fun LanguagePickerDialog(
    current: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(strings.languagePickerTitle, fontWeight = FontWeight.Bold, color = DeepRose, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    Button(
                        onClick = { onPick(lang) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (lang == current) RoseGold else ChipInactive
                        )
                    ) {
                        Text(
                            text       = lang.nativeName,
                            fontSize   = 15.sp,
                            color      = if (lang == current) Color.White else DeepRose,
                            fontWeight = if (lang == current) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = RoseGold) }
        },
        containerColor = ElegantCream
    )
}
