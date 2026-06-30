package com.security.stealthapp.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.security.stealthapp.data.firebase.SalonBadge
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.LoyaltyTier
import com.security.stealthapp.data.firebase.WaitlistEntry
import com.security.stealthapp.data.firebase.badge
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
import coil.compose.AsyncImage
import com.security.stealthapp.util.ImageUtils
import com.security.stealthapp.util.NotificationHelper
import com.security.stealthapp.viewmodel.CheckoutUiState
import com.security.stealthapp.viewmodel.DashboardViewModel
import com.security.stealthapp.viewmodel.ExportPhase
import com.security.stealthapp.viewmodel.ExportViewModel
import com.security.stealthapp.viewmodel.LanguageViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.security.stealthapp.viewmodel.ChangePinViewModel
import com.security.stealthapp.viewmodel.DecoyPinViewModel
import com.security.stealthapp.viewmodel.NotificationCenterViewModel
import androidx.compose.material.icons.filled.Notifications

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
    onNavigate: (String) -> Unit           = {},
    viewModel: DashboardViewModel          = hiltViewModel(),
    langVm: LanguageViewModel              = hiltViewModel(),
    exportVm: ExportViewModel              = hiltViewModel(),
    decoyVm: DecoyPinViewModel             = hiltViewModel(),
    changePinVm: ChangePinViewModel        = hiltViewModel(),
    notifVm: NotificationCenterViewModel   = hiltViewModel()
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

    // Show a local notification when a booking status changes (PENDING → CONFIRMED/CANCELLED).
    // The notification itself is deliberately content-free (neutral title + body) so a
    // salon/service name never lands on the lock screen or shade — the real details are
    // in the in-app Notification Center, behind the PIN.
    LaunchedEffect(Unit) {
        viewModel.bookingStatusChange.collect { _ ->
            NotificationHelper.showBookingUpdate(
                context,
                latestStrings.reminderTitle,
                latestStrings.notifNeutralBody
            )
        }
    }

    // Show a local notification when a waitlist slot becomes available (neutral content).
    LaunchedEffect(Unit) {
        viewModel.waitlistSlotAvailable.collect { _ ->
            NotificationHelper.showBookingUpdate(
                context,
                latestStrings.reminderTitle,
                latestStrings.notifNeutralBody
            )
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
    val myWaitlist                by viewModel.myWaitlist.collectAsStateWithLifecycle()
    val loyaltyPoints             by viewModel.loyaltyPoints.collectAsStateWithLifecycle()
    val loyaltyTier               by viewModel.loyaltyTier.collectAsStateWithLifecycle()
    val recommendedSalons         by viewModel.recommendedSalons.collectAsStateWithLifecycle()
    val reviewsForSalon           by viewModel.reviewsForSalon.collectAsStateWithLifecycle()
    val galleryForSalon           by viewModel.galleryForSalon.collectAsStateWithLifecycle()
    val selectedCategoryIndex     by viewModel.selectedCategoryIndex.collectAsStateWithLifecycle()
    val selectedNeighborhoodIndex by viewModel.selectedNeighborhoodIndex.collectAsStateWithLifecycle()
    val isOffline                 by viewModel.isOffline.collectAsStateWithLifecycle()
    val currentUserName           by viewModel.currentUserName.collectAsStateWithLifecycle()
    val currentUserPhoto          by viewModel.currentUserPhoto.collectAsStateWithLifecycle()
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
    var showSalonDetail      by remember { mutableStateOf<SalonDocument?>(null) }
    val sheetState           = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var bookingIntent     by remember { mutableStateOf<BookingIntent?>(null) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var showDatePicker    by remember { mutableStateOf(false) }
    var showSlotPicker    by remember { mutableStateOf(false) }
    var pendingSlotMs     by remember { mutableStateOf(0L) }
    var showNotesDialog   by remember { mutableStateOf(false) }
    var bookingNotes      by remember { mutableStateOf("") }

    // Feature 1: photo confirmation
    var pendingPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }

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
                        val unreadCount by notifVm.unreadCount.collectAsStateWithLifecycle()
                        IconButton(onClick = { onNavigate(Screen.Notifications.build(viewModel.customerId)) }) {
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

                // ── Recommendations carousel ──────────────────────────────────
                if (recommendedSalons.isNotEmpty() && searchQuery.isBlank()) {
                    RecommendedSection(
                        salons      = recommendedSalons,
                        favoriteIds = favoriteIds,
                        onToggleFav = { viewModel.toggleFavorite(it) },
                        onBook      = { salon ->
                            showSalonDetail = salon
                            viewModel.setActiveSalon(salon.id)
                        }
                    )
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
                                onBook           = { showSalonDetail = salon; viewModel.setActiveSalon(salon.id) }
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
            LaunchedEffect(showProfileSheet) {
                if (showProfileSheet) decoyVm.loadDecoyPinStatus(viewModel.customerId)
            }
            ModalBottomSheet(
                onDismissRequest = { showProfileSheet = false },
                sheetState       = profileSheetState,
                containerColor   = ElegantCream
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    CustomerProfileSheetContent(
                        name            = viewModel.editName,
                        onNameChange    = { viewModel.onEditNameChanged(it) },
                        onSave          = { viewModel.saveCustomerProfile(); showProfileSheet = false },
                        onDismiss       = { showProfileSheet = false },
                        photo           = currentUserPhoto,
                        onPhotoSelected = { bytes -> pendingPhotoBytes = bytes },
                        isUploadingPhoto = viewModel.isUploadingPhoto,
                        appointments    = myAppointments
                    )
                    LoyaltyCard(
                        points   = loyaltyPoints,
                        tier     = loyaltyTier,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
                    )
                    DecoyPinSection(
                        uid      = viewModel.customerId,
                        decoyVm  = decoyVm,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
                    )
                    ChangePinSection(
                        changePinVm = changePinVm,
                        modifier    = Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)
                    )
                }
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

        // ── Photo confirmation dialog ─────────────────────────────────────────
        pendingPhotoBytes?.let { photoBytes ->
            val bitmap = remember(photoBytes) {
                android.graphics.BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            }
            AlertDialog(
                onDismissRequest = { pendingPhotoBytes = null },
                title = { Text(strings.photoConfirmTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.fillMaxWidth()
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap             = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(strings.photoConfirmBody, fontSize = 13.sp, color = Color(0xFF555555), textAlign = TextAlign.Center)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.uploadProfilePhoto(photoBytes)
                            pendingPhotoBytes = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.photoConfirmYes, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPhotoBytes = null }) {
                        Text(strings.photoConfirmRetry, color = RoseGold)
                    }
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
                            val price = salon.pricePerService[service] ?: 0
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
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(service, fontSize = 14.sp, color = DeepRose, fontWeight = FontWeight.SemiBold)
                                    if (price > 0) {
                                        Text("%,d AFN".format(price), fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.Medium)
                                    }
                                }
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.align(Alignment.Center)
                                ) {
                                    Text(strings.noSlotsAvailable, color = RoseGold, textAlign = TextAlign.Center)
                                    val intent = bookingIntent
                                    if (intent != null && intent.dateMs != null) {
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                viewModel.joinWaitlist(intent.salon, intent.dateMs)
                                                showSlotPicker = false
                                                bookingIntent  = null
                                                viewModel.clearSlots()
                                            },
                                            shape  = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                                        ) {
                                            Text(strings.waitlistJoin, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            else -> {
                                val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(viewModel.availableSlots) { slotMs ->
                                        Button(
                                            onClick = {
                                                showSlotPicker = false
                                                pendingSlotMs  = slotMs
                                                showNotesDialog = true
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

        // ── Step 4: Booking notes dialog ─────────────────────────────────────
        if (showNotesDialog && bookingIntent != null) {
            val intent  = bookingIntent!!
            val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
            AlertDialog(
                onDismissRequest = {
                    showNotesDialog = false
                    pendingSlotMs   = 0L
                    bookingNotes    = ""
                    bookingIntent   = null
                    viewModel.clearSlots()
                },
                title = { Text(strings.bookingNotesTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text     = "${intent.service} · ${intent.salon.salonName}",
                            fontSize = 14.sp,
                            color    = DeepRose,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text     = dateFmt.format(Date(pendingSlotMs)),
                            fontSize = 13.sp,
                            color    = RoseGold
                        )
                        OutlinedTextField(
                            value         = bookingNotes,
                            onValueChange = { bookingNotes = it },
                            label         = { Text(strings.bookingNotesHint, fontSize = 13.sp) },
                            maxLines      = 3,
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(12.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = RoseGold,
                                unfocusedBorderColor = ChipInactive,
                                cursorColor          = RoseGold,
                                focusedLabelColor    = RoseGold
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.bookService(intent.salon, intent.service, pendingSlotMs, bookingNotes)
                            showNotesDialog = false
                            pendingSlotMs   = 0L
                            bookingNotes    = ""
                            bookingIntent   = null
                            viewModel.clearSlots()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.confirmBooking, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNotesDialog = false
                        pendingSlotMs   = 0L
                        bookingNotes    = ""
                        bookingIntent   = null
                        viewModel.clearSlots()
                    }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Payment / HesabPay checkout ───────────────────────────────────────
        run {
            val openCheckout: (String) -> Unit = { url ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            when (val state = viewModel.checkout) {
                is CheckoutUiState.Creating -> {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(strings.paymentTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = RoseGold, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(strings.paymentPreparing, fontSize = 14.sp, color = Color(0xFF555555))
                            }
                        },
                        confirmButton = { },
                        containerColor = ElegantCream
                    )
                }

                is CheckoutUiState.AwaitingPayment -> {
                    // Open the HesabPay checkout page once when we enter this state.
                    LaunchedEffect(state.session.paymentId) {
                        openCheckout(state.session.checkoutUrl)
                    }
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(strings.paymentTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "${strings.paymentAmount}: ${state.session.amount} AFN",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepRose
                                )
                                Text(strings.paymentOpenInstruction, fontSize = 13.sp, color = Color(0xFF555555))
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = RoseGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(strings.paymentWaiting, fontSize = 13.sp, color = RoseGold)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { openCheckout(state.session.checkoutUrl) }) {
                                Text(strings.paymentOpenAgain, color = RoseGold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.cancelCheckout() }) {
                                Text(strings.cancel, color = RoseGold)
                            }
                        },
                        containerColor = ElegantCream
                    )
                }

                is CheckoutUiState.Failed -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.cancelCheckout() },
                        title = { Text(strings.paymentTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                        text = { Text(strings.paymentFailed, fontSize = 14.sp, color = Color(0xFF555555)) },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.cancelCheckout() },
                                colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                            ) { Text(strings.ok, color = Color.White) }
                        },
                        containerColor = ElegantCream
                    )
                }

                is CheckoutUiState.Paid -> {
                    // Reset checkout; the booking-confirmation dialog (driven by
                    // bookingConfirmSalonName) shows the success message.
                    LaunchedEffect(Unit) { viewModel.cancelCheckout() }
                }

                CheckoutUiState.Idle -> { /* nothing */ }
            }
        }

        // ── Waitlist join confirmation ────────────────────────────────────────
        viewModel.waitlistJoinedSalonName?.let { salonName ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissWaitlistJoined() },
                icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
                title = { Text(strings.waitlistJoined, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text(strings.waitlistJoinedText(salonName), fontSize = 14.sp, color = Color(0xFF555555)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissWaitlistJoined() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
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
                    waitlistEntries = myWaitlist,
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
                    },
                    onLeaveWaitlist   = { entryId -> viewModel.leaveWaitlist(entryId) },
                    onDismissWaitlistSlot = { entryId -> viewModel.dismissWaitlistSlot(entryId) }
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

        // ── Salon detail sheet ────────────────────────────────────────────────
        showSalonDetail?.let { salon ->
            val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSalonDetail = null },
                sheetState       = detailSheetState,
                containerColor   = ElegantCream
            ) {
                SalonDetailSheetContent(
                    salon            = salon,
                    reviews          = reviewsForSalon,
                    gallery          = galleryForSalon,
                    isFavorite       = favoriteIds.contains(salon.id),
                    onToggleFavorite = { viewModel.toggleFavorite(salon.id) },
                    onBook = {
                        showSalonDetail   = null
                        bookingIntent     = BookingIntent(salon, "")
                        showServiceDialog = true
                    },
                    onDismiss = { showSalonDetail = null }
                )
            }
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
                    val cardBadge = remember(salon.id, salon.isVerified, salon.rating, salon.confirmedCount) { salon.badge() }
                    if (cardBadge != SalonBadge.NONE) {
                        Spacer(Modifier.height(5.dp))
                        SalonBadgeChip(badge = cardBadge)
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
                        val price = salon.pricePerService[service] ?: 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(BlushPink.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            if (price > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(service, fontSize = 11.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                                    Text("·", fontSize = 11.sp, color = RoseGold)
                                    Text("%,d AFN".format(price), fontSize = 10.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Text(service, fontSize = 11.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                            }
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

// ── Recommended for You section ──────────────────────────────────────────────

@Composable
private fun RecommendedSection(
    salons: List<SalonDocument>,
    favoriteIds: Set<String>,
    onToggleFav: (String) -> Unit,
    onBook: (SalonDocument) -> Unit
) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier              = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(16.dp))
            Column {
                Text(strings.recommendedTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DeepRose)
                Text(strings.recommendedSubtitle, fontSize = 10.sp, color = UnavailableGrey)
            }
        }
        LazyRow(
            contentPadding      = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(salons, key = { "rec_${it.id}" }) { salon ->
                RecommendedSalonCard(
                    salon       = salon,
                    isFavorite  = favoriteIds.contains(salon.id),
                    onToggleFav = { onToggleFav(salon.id) },
                    onBook      = { onBook(salon) }
                )
            }
        }
    }
}

@Composable
private fun RecommendedSalonCard(
    salon: SalonDocument,
    isFavorite: Boolean,
    onToggleFav: () -> Unit,
    onBook: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        modifier  = Modifier.width(180.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        onClick   = onBook
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    salon.salonName,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp,
                    color      = DeepRose,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFav, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        tint     = if (isFavorite) DeepRose else ChipInactive,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = RoseGold, modifier = Modifier.size(11.dp))
                Text(salon.district, fontSize = 10.sp, color = UnavailableGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (salon.rating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(11.dp))
                    Text("%.1f".format(salon.rating), fontSize = 10.sp, color = UnavailableGrey)
                }
            }
            Button(
                onClick  = onBook,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RoseGold),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(strings.book, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
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
    waitlistEntries: List<WaitlistEntry> = emptyList(),
    onDismiss: () -> Unit,
    onChatClick: (AppointmentDocument) -> Unit = {},
    onCancelClick: (AppointmentDocument) -> Unit = {},
    onRescheduleClick: (AppointmentDocument) -> Unit = {},
    onReviewClick: (AppointmentDocument) -> Unit = {},
    onLeaveWaitlist: (String) -> Unit = {},
    onDismissWaitlistSlot: (String) -> Unit = {}
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
            val now      = remember { System.currentTimeMillis() }
            val upcoming = remember(appointments) {
                appointments.filter { it.status != "CANCELLED" && it.appointmentDate > now }
                    .sortedBy { it.appointmentDate }
            }
            val past = remember(appointments) {
                appointments.filter { it.status == "CANCELLED" || it.appointmentDate <= now }
                    .sortedByDescending { it.appointmentDate }
            }

            if (upcoming.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(AvailableGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.bookingsUpcoming, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRose)
                }
                upcoming.forEach { appt -> BookingCard(appt, dateFmt, onChatClick, onRescheduleClick, onReviewClick, { cancelTarget = appt }) }
            }

            if (past.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(UnavailableGrey)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.bookingsPast, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF888888))
                }
                past.forEach { appt -> BookingCard(appt, dateFmt, onChatClick, onRescheduleClick, onReviewClick, { cancelTarget = appt }) }
            }
        }

        // ── Waitlist entries ──────────────────────────────────────────────────
        if (waitlistEntries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BlushPink)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WarmGold))
                Spacer(Modifier.width(8.dp))
                Text(strings.waitlistTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRose)
            }
            val dateFmtShort = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
            waitlistEntries.forEach { entry ->
                WaitlistCard(
                    entry             = entry,
                    dateFmt           = dateFmtShort,
                    onLeave           = { onLeaveWaitlist(entry.id) },
                    onDismissSlot     = { onDismissWaitlistSlot(entry.id) }
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingCard(
    appt: AppointmentDocument,
    dateFmt: SimpleDateFormat,
    onChatClick: (AppointmentDocument) -> Unit,
    onRescheduleClick: (AppointmentDocument) -> Unit,
    onReviewClick: (AppointmentDocument) -> Unit,
    onCancelClick: () -> Unit
) {
    val strings       = LocalStrings.current
    val canReschedule = appt.status == "PENDING" || appt.status == "CONFIRMED"
    val canReview     = appt.status == "CONFIRMED"
    val canCancel     = appt.status == "PENDING"

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
                        .background(
                            when (appt.status) {
                                "CONFIRMED" -> AvailableGreen
                                "CANCELLED" -> UnavailableGrey
                                else        -> RoseGold
                            }
                        )
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
                    Icon(Icons.Default.Chat, contentDescription = strings.chat, tint = RoseGold, modifier = Modifier.size(20.dp))
                }
                StatusChip(appt.status)
            }
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
                            onClick        = onCancelClick,
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
    onDismiss: () -> Unit,
    photo: String = "",
    onPhotoSelected: (ByteArray) -> Unit = {},
    isUploadingPhoto: Boolean = false,
    appointments: List<AppointmentDocument> = emptyList()
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = ImageUtils.uriToCompressedBytes(context, uri)) {
                is ImageUtils.BytesResult.Success  -> onPhotoSelected(result.bytes)
                is ImageUtils.BytesResult.TooLarge -> { /* optionally surface error */ }
                is ImageUtils.BytesResult.Failed   -> { /* optionally surface error */ }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
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

        // ── Profile photo ─────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (photo.isNotBlank()) {
                    if (photo.startsWith("http")) {
                        AsyncImage(
                            model              = photo,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        val bitmap = remember(photo) { ImageUtils.base64ToBitmap(photo) }
                        if (bitmap != null) {
                            Image(
                                bitmap             = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            ProfileInitialsAvatar(name = name, size = 88)
                        }
                    }
                } else {
                    ProfileInitialsAvatar(name = name, size = 88)
                }
                if (isUploadingPhoto) {
                    CircularProgressIndicator(
                        color    = RoseGold,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    IconButton(
                        onClick  = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(RoseGold)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // ── Name field ────────────────────────────────────────────────
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

        // ── Booking history ───────────────────────────────────────────
        val historyItems = remember(appointments) {
            appointments
                .filter { it.status == "CONFIRMED" || it.status == "CANCELLED" }
                .sortedByDescending { it.appointmentDate }
                .take(10)
        }
        if (historyItems.isNotEmpty()) {
            val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
            Spacer(Modifier.height(4.dp))
            Text(
                text       = strings.bookingHistoryTitle,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = DeepRose
            )
            HorizontalDivider(color = BlushPink)
            historyItems.forEach { appt ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = appt.serviceName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            color      = DeepRose,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (appt.salonName.isNotBlank()) {
                            Text(appt.salonName, fontSize = 11.sp, color = RoseGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            dateFmt.format(Date(appt.appointmentDate)),
                            fontSize = 10.sp,
                            color    = Color(0xFFAAAAAA)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val (chipBg, chipFg) = if (appt.status == "CONFIRMED")
                        Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
                    else
                        Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text       = if (appt.status == "CONFIRMED") strings.accept else strings.decline,
                            fontSize   = 10.sp,
                            color      = chipFg,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInitialsAvatar(name: String, size: Int) {
    val initial = if (name.isNotBlank()) name.first().toString().uppercase() else "?"
    val color   = remember(name) { if (name.isNotBlank()) avatarColor(name) else Color(0xFFB76E79) }
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    ) {
        Text(
            text       = initial,
            fontSize   = (size / 2.5).sp,
            fontWeight = FontWeight.Bold,
            color      = Color.White
        )
    }
}

// ── Salon detail sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalonDetailSheetContent(
    salon: SalonDocument,
    reviews: List<ReviewDocument>,
    gallery: List<GalleryImageDocument>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBook: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val color   = remember(salon.salonName) { avatarColor(salon.salonName) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp)
    ) {
        // ── Header row ────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(strings.salonDetailsTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DeepRose)
            TextButton(onClick = onDismiss) { Text(strings.close, color = RoseGold) }
        }
        HorizontalDivider(color = BlushPink)
        Spacer(Modifier.height(16.dp))

        // ── Identity block ────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
            ) {
                Text(
                    text       = salon.salonName.first().toString(),
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(salon.salonName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DeepRose)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = RoseGold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(salon.district, fontSize = 12.sp, color = Color(0xFF888888))
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(WarmGold.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("%.1f".format(salon.rating), fontSize = 12.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                    if (reviews.isNotEmpty()) {
                        Text("  (${reviews.size})", fontSize = 11.sp, color = Color(0xFF999999))
                    }
                }
                val detailBadge = remember(salon.id, salon.isVerified, salon.rating, salon.confirmedCount) { salon.badge() }
                if (detailBadge != SalonBadge.NONE) {
                    Spacer(Modifier.height(6.dp))
                    SalonBadgeChip(badge = detailBadge)
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector        = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = strings.favorites,
                    tint               = if (isFavorite) DeepRose else RoseGold,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Today's hours ─────────────────────────────────────────────
        if (salon.workingHours.isNotEmpty()) {
            val todayDow = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
            val todayWh  = salon.workingHours.find { it.dayOfWeek == todayDow }
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DashboardSurface)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(strings.todayHours, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DeepRose, modifier = Modifier.weight(1f))
                    if (todayWh != null && todayWh.isOpen) {
                        val openStr  = "%02d:%02d".format(todayWh.openHour,  todayWh.openMinute)
                        val closeStr = "%02d:%02d".format(todayWh.closeHour, todayWh.closeMinute)
                        Text("$openStr – $closeStr", fontSize = 13.sp, color = AvailableGreen, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(strings.closedThisDay, fontSize = 13.sp, color = UnavailableGrey)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Portfolio / sample work ───────────────────────────────────
        if (gallery.isNotEmpty()) {
            Text(strings.portfolioTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(gallery, key = { it.id }) { image ->
                    val imageModifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BlushPink)
                    if (image.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model              = image.imageUrl,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = imageModifier
                        )
                    } else {
                        val bitmap = remember(image.id) { ImageUtils.base64ToBitmap(image.imageBase64) }
                        if (bitmap != null) {
                            Image(
                                bitmap             = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = imageModifier
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Services ──────────────────────────────────────────────────
        if (salon.services.isNotEmpty()) {
            Text(strings.sectionServices, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                salon.services.forEach { service ->
                    val price = salon.pricePerService[service] ?: 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BlushPink.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (price > 0) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(service, fontSize = 12.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                                Text("·", fontSize = 12.sp, color = RoseGold)
                                Text("%,d AFN".format(price), fontSize = 11.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text(service, fontSize = 12.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Book button ───────────────────────────────────────────────
        Button(
            onClick  = onBook,
            enabled  = salon.isAvailable,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = RoseGold,
                disabledContainerColor = UnavailableGrey.copy(alpha = 0.25f)
            )
        ) {
            Text(strings.book, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(22.dp))

        // ── Reviews ───────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text(strings.reviews, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
            if (salon.rating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("%.1f".format(salon.rating), fontSize = 13.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(color = BlushPink, modifier = Modifier.padding(vertical = 8.dp))

        if (reviews.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            ) {
                Text(strings.noReviewsYet, fontSize = 14.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reviews.take(20).forEach { review -> ReviewCard(review) }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewDocument) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    text       = review.customerName.ifBlank { "—" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = DeepRose
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector        = if (star <= review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint               = WarmGold,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
            }
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(review.comment, fontSize = 12.sp, color = Color(0xFF555555))
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text     = dateFmt.format(Date(review.createdAt)),
                fontSize = 10.sp,
                color    = Color(0xFFAAAAAA)
            )
            if (review.providerReply.isNotBlank()) {
                val strings = LocalStrings.current
                Spacer(Modifier.height(8.dp))
                Column(
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
                        .padding(8.dp)
                ) {
                    Text(strings.providerReplied, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, color = RoseGold)
                    Spacer(Modifier.height(2.dp))
                    Text(review.providerReply, fontSize = 11.sp, color = Color(0xFF444444))
                }
            }
        }
    }
}

// ── Waitlist card ──────────────────────────────────────────────────────────────

@Composable
private fun WaitlistCard(
    entry: WaitlistEntry,
    dateFmt: SimpleDateFormat,
    onLeave: () -> Unit,
    onDismissSlot: () -> Unit
) {
    val strings        = LocalStrings.current
    val isSlotAvail    = entry.status == "SLOT_AVAILABLE"
    val accentColor    = if (isSlotAvail) AvailableGreen else WarmGold
    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isSlotAvail) AvailableGreen.copy(alpha = 0.08f) else DashboardSurface
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp).height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.salonName, fontWeight = FontWeight.SemiBold, color = DeepRose, fontSize = 13.sp)
                Text(
                    "📅 ${dateFmt.format(Date(entry.requestedDate))}",
                    fontSize = 11.sp, color = Color(0xFFAAAAAA)
                )
                if (isSlotAvail) {
                    Text(strings.waitlistSlotAvailableTitle, fontSize = 11.sp, color = AvailableGreen, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(strings.waitlistWaiting, fontSize = 11.sp, color = WarmGold)
                }
            }
            if (isSlotAvail) {
                OutlinedButton(
                    onClick        = onDismissSlot,
                    shape          = RoundedCornerShape(8.dp),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, AvailableGreen),
                    colors         = ButtonDefaults.outlinedButtonColors(contentColor = AvailableGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(strings.waitlistDismiss, fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick        = onLeave,
                    shape          = RoundedCornerShape(8.dp),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, ChipInactive),
                    colors         = ButtonDefaults.outlinedButtonColors(contentColor = UnavailableGrey),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(strings.waitlistLeave, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Salon badge chip ──────────────────────────────────────────────────────────

@Composable
private fun SalonBadgeChip(badge: SalonBadge, modifier: Modifier = Modifier) {
    if (badge == SalonBadge.NONE) return
    val strings = LocalStrings.current
    val (label, color) = when (badge) {
        SalonBadge.VERIFIED -> Pair(strings.badgeVerified, Color(0xFF4CAF50))
        SalonBadge.GOLD     -> Pair(strings.badgeGold,     Color(0xFFD4A853))
        SalonBadge.SILVER   -> Pair(strings.badgeSilver,   Color(0xFF9E9E9E))
        SalonBadge.NONE     -> Pair("",                    Color.Transparent)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ── Customer Loyalty card ─────────────────────────────────────────────────────

@Composable
private fun LoyaltyCard(points: Int, tier: LoyaltyTier, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current

    val (tierLabel, tierColor, nextTarget) = when (tier) {
        LoyaltyTier.NEWCOMER -> Triple(strings.loyaltyTierNewcomer, Color(0xFFCD7F32), 50)
        LoyaltyTier.REGULAR  -> Triple(strings.loyaltyTierRegular,  Color(0xFF9E9E9E), 150)
        LoyaltyTier.VIP      -> Triple(strings.loyaltyTierVIP,      WarmGold,          150)
    }
    val progress = when (tier) {
        LoyaltyTier.NEWCOMER -> points / 50f
        LoyaltyTier.REGULAR  -> (points - 50) / 100f
        LoyaltyTier.VIP      -> 1f
    }.coerceIn(0f, 1f)

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier             = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Star, null, tint = tierColor, modifier = Modifier.size(20.dp))
                    Text(strings.loyaltyTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRose)
                }
                Box(
                    modifier          = Modifier
                        .background(tierColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(tierLabel, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$points", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = tierColor)
                Text(strings.loyaltyPtsUnit, fontSize = 13.sp, color = tierColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            LinearProgressIndicator(
                progress          = { progress },
                modifier          = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color             = tierColor,
                trackColor        = tierColor.copy(alpha = 0.18f),
                strokeCap         = androidx.compose.ui.graphics.StrokeCap.Round
            )
            val hintText = if (tier == LoyaltyTier.VIP) "★ ${strings.loyaltyTierVIP}"
                           else strings.loyaltyNextTier(nextTarget - points)
            Text(hintText, fontSize = 11.sp, color = UnavailableGrey)
            Text(strings.loyaltyEarnHint, fontSize = 10.sp, color = UnavailableGrey)
        }
    }
}

// ── Decoy PIN section (used in customer & provider profile sheets) ────────────

@Composable
fun DecoyPinSection(
    uid: String,
    decoyVm: DecoyPinViewModel,
    modifier: Modifier = Modifier
) {
    val strings     = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) decoyVm.loadDecoyPinStatus(uid)
    }

    Card(
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = RoseGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.decoyPinTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                    Text(strings.decoyPinSubtitle, fontSize = 11.sp, color = Color(0xFF888888))
                }
            }
            if (decoyVm.hasDecoyPin) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(start = 30.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.decoyPinEnabled, fontSize = 12.sp, color = AvailableGreen)
                }
            }
            Button(
                onClick  = { decoyVm.openDialog() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (decoyVm.hasDecoyPin) ChipInactive else RoseGold
                )
            ) {
                Text(
                    text       = if (decoyVm.hasDecoyPin) strings.decoyPinChange else strings.decoyPinSet,
                    fontSize   = 14.sp,
                    color      = if (decoyVm.hasDecoyPin) DeepRose else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ── Set decoy PIN dialog ──────────────────────────────────────────────────
    if (decoyVm.showDialog) {
        AlertDialog(
            onDismissRequest = { decoyVm.dismissDialog() },
            title = {
                Text(strings.decoyPinDialogTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepRose)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.decoyPinDialogText, fontSize = 12.sp, color = Color(0xFF666666))
                    OutlinedTextField(
                        value           = decoyVm.newPin,
                        onValueChange   = { decoyVm.newPin = it.filter { c -> c.isDigit() } },
                        label           = { Text(strings.decoyPinNewPin, fontSize = 12.sp) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(12.dp),
                        colors          = fieldColors
                    )
                    OutlinedTextField(
                        value           = decoyVm.confirmPin,
                        onValueChange   = { decoyVm.confirmPin = it.filter { c -> c.isDigit() } },
                        label           = { Text(strings.decoyPinConfirm, fontSize = 12.sp) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(12.dp),
                        colors          = fieldColors
                    )
                    decoyVm.errorMessage?.let { err ->
                        Text(err, fontSize = 12.sp, color = Color(0xFFD32F2F))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick  = { decoyVm.save(uid, strings.decoyPinMismatch, strings.decoyPinSameAsReal) },
                    enabled  = decoyVm.newPin.length >= 6 && !decoyVm.isSaving,
                    colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) {
                    if (decoyVm.isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(strings.saveProfile, color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { decoyVm.dismissDialog() }) {
                    Text(strings.cancel, color = RoseGold)
                }
            },
            containerColor = ElegantCream
        )
    }

    // ── Save success confirmation ─────────────────────────────────────────────
    if (decoyVm.saveSuccess) {
        AlertDialog(
            onDismissRequest = { decoyVm.dismissSuccess() },
            icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
            title = { Text(strings.decoyPinSaved, fontWeight = FontWeight.Bold, color = DeepRose) },
            confirmButton = {
                Button(
                    onClick = { decoyVm.dismissSuccess() },
                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) { Text(strings.ok, color = Color.White) }
            },
            containerColor = ElegantCream
        )
    }
}

// ── Change PIN section (used in customer & provider profile sheets) ───────────

@Composable
fun ChangePinSection(
    changePinVm: ChangePinViewModel,
    modifier: Modifier = Modifier
) {
    val strings     = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    Card(
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = RoseGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(strings.changePinTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
            }
            ChangePinField(
                value         = changePinVm.currentPin,
                onValueChange = { changePinVm.currentPin = it.filter(Char::isDigit) },
                label         = strings.changePinCurrentPin,
                fieldColors   = fieldColors
            )
            ChangePinField(
                value         = changePinVm.newPin,
                onValueChange = { changePinVm.newPin = it.filter(Char::isDigit) },
                label         = strings.changePinNewPin,
                fieldColors   = fieldColors
            )
            ChangePinField(
                value         = changePinVm.confirmPin,
                onValueChange = { changePinVm.confirmPin = it.filter(Char::isDigit) },
                label         = strings.changePinConfirmNew,
                fieldColors   = fieldColors
            )
            if (changePinVm.state is ChangePinViewModel.State.Error) {
                Text(
                    (changePinVm.state as ChangePinViewModel.State.Error).message,
                    fontSize = 12.sp,
                    color    = Color(0xFFD32F2F)
                )
            }
            Button(
                onClick  = { changePinVm.changePin() },
                enabled  = changePinVm.state !is ChangePinViewModel.State.Loading,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
            ) {
                if (changePinVm.state is ChangePinViewModel.State.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(strings.changePinTitle, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (changePinVm.state is ChangePinViewModel.State.Success) {
        AlertDialog(
            onDismissRequest = { changePinVm.dismissState() },
            icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
            title = { Text(strings.changePinSaved, fontWeight = FontWeight.Bold, color = DeepRose) },
            confirmButton = {
                Button(
                    onClick = { changePinVm.dismissState() },
                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) { Text(strings.ok, color = Color.White) }
            },
            containerColor = ElegantCream
        )
    }
}

@Composable
private fun ChangePinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fieldColors: androidx.compose.material3.TextFieldColors
) {
    OutlinedTextField(
        value                = value,
        onValueChange        = onValueChange,
        label                = { Text(label, fontSize = 12.sp) },
        singleLine           = true,
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier             = Modifier.fillMaxWidth(),
        shape                = RoundedCornerShape(12.dp),
        colors               = fieldColors
    )
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
