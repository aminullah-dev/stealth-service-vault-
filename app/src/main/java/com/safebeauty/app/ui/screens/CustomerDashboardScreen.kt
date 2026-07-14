package com.safebeauty.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.safebeauty.app.data.firebase.GalleryImageDocument
import com.safebeauty.app.data.firebase.ReviewDocument
import com.safebeauty.app.data.firebase.SalonBadge
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.activeStaff
import com.safebeauty.app.data.firebase.hasLocation
import com.safebeauty.app.data.firebase.LoyaltyTier
import com.safebeauty.app.data.firebase.WaitlistEntry
import com.safebeauty.app.data.firebase.badge
import com.safebeauty.app.navigation.Screen
import com.safebeauty.app.ui.theme.AppLanguage
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
import com.safebeauty.app.ui.theme.RosePetal
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.ui.theme.WarmGold
import coil.compose.AsyncImage
import com.safebeauty.app.util.AnnouncementPrefs
import com.safebeauty.app.util.ImageUtils
import com.safebeauty.app.util.NotificationHelper
import com.safebeauty.app.viewmodel.CheckoutUiState
import com.safebeauty.app.viewmodel.SalonSort
import com.safebeauty.app.viewmodel.DashboardViewModel
import com.safebeauty.app.viewmodel.ExportPhase
import com.safebeauty.app.viewmodel.ExportViewModel
import com.safebeauty.app.viewmodel.LanguageViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.safebeauty.app.viewmodel.ChangePinViewModel
import com.safebeauty.app.viewmodel.NotificationCenterViewModel
import androidx.compose.material.icons.filled.Notifications

// Avatar colors cycle through the brand palette based on name's first character
// Brand-harmonious avatar palette: every pair stays in the rose/gold/plum
// family so a list of salons reads as one designed system rather than a grab
// bag of random hues. Each entry is a (top, bottom) gradient pair.
internal val avatarGradients = listOf(
    Color(0xFFC98490) to Color(0xFF8B3A47),   // rose
    Color(0xFFB08BAB) to Color(0xFF7C5273),   // plum
    Color(0xFFE0BC76) to Color(0xFFB08430),   // gold
    Color(0xFFD79AA4) to Color(0xFFA05661),   // blush
    Color(0xFF9E86B8) to Color(0xFF64517E),   // violet
    Color(0xFFCB9D82) to Color(0xFF96603F),   // bronze
)

internal fun avatarGradient(name: String): Pair<Color, Color> =
    avatarGradients[name.first().lowercaseChar().code % avatarGradients.size]

private data class BookingIntent(
    val salon: SalonDocument,
    val service: String,
    val dateMs: Long? = null,
    // "" = any available stylist (or a solo salon).
    val staffId: String = "",
    val staffName: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDashboardScreen(
    onLockTriggered: () -> Unit,
    onNavigate: (String) -> Unit           = {},
    viewModel: DashboardViewModel          = hiltViewModel(),
    langVm: LanguageViewModel              = hiltViewModel(),
    exportVm: ExportViewModel              = hiltViewModel(),
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

    // Show a local notification when a booking status changes (PENDING → CONFIRMED/CANCELLED)
    LaunchedEffect(Unit) {
        viewModel.bookingStatusChange.collect { change ->
            val title = latestStrings.bookingUpdatedTitle
            val body  = if (change.newStatus == "CONFIRMED") {
                val dateFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                "${change.serviceName} at ${change.salonName}\n${dateFmt.format(Date(change.appointmentDate))}"
            } else {
                latestStrings.bookingDeclinedText(change.salonName)
            }
            NotificationHelper.showBookingUpdate(context, title, body)
        }
    }

    // Show a local notification when a waitlist slot becomes available
    LaunchedEffect(Unit) {
        viewModel.waitlistSlotAvailable.collect { salonName ->
            NotificationHelper.showBookingUpdate(
                context,
                latestStrings.waitlistSlotAvailableTitle,
                latestStrings.waitlistSlotAvailableText(salonName)
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

    val filteredSalons            by viewModel.displayedSalons.collectAsStateWithLifecycle()
    val sortMode                  by viewModel.sortMode.collectAsStateWithLifecycle()
    val minRating                 by viewModel.minRating.collectAsStateWithLifecycle()
    val maxPrice                  by viewModel.maxPrice.collectAsStateWithLifecycle()
    val customerLoc               by viewModel.customerLoc.collectAsStateWithLifecycle()
    var showFilterSheet           by remember { mutableStateOf(false) }
    val filtersActive = sortMode != SalonSort.RECOMMENDED || minRating > 0.0 || maxPrice > 0
    val myAppointments            by viewModel.myAppointments.collectAsStateWithLifecycle()
    val myWaitlist                by viewModel.myWaitlist.collectAsStateWithLifecycle()
    val loyaltyPoints             by viewModel.loyaltyPoints.collectAsStateWithLifecycle()
    val loyaltyTier               by viewModel.loyaltyTier.collectAsStateWithLifecycle()
    val referralCode              by viewModel.referralCode.collectAsStateWithLifecycle()
    val referralCredit            by viewModel.referralCredit.collectAsStateWithLifecycle()
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

    // Location permission for "sort by nearest" — requested only when the user
    // taps the Nearest chip, never up front.
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val loc = com.safebeauty.app.util.LocationHelper.lastKnownLocation(context)
            if (loc != null) {
                viewModel.setCustomerLocation(loc.latitude, loc.longitude)
                viewModel.setSortMode(SalonSort.NEAREST)
            } else {
                Toast.makeText(context, strings.locationUnavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var bookingIntent     by remember { mutableStateOf<BookingIntent?>(null) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var showDatePicker    by remember { mutableStateOf(false) }
    var showSlotPicker    by remember { mutableStateOf(false) }
    var pendingSlotMs     by remember { mutableStateOf(0L) }
    var showNotesDialog   by remember { mutableStateOf(false) }
    var bookingNotes      by remember { mutableStateOf("") }
    var paymentMethod     by remember { mutableStateOf("ONLINE") } // "ONLINE" | "CASH"

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "SafeBeauty",
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Gradients.BrandRose,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Text(" ❀", fontSize = 15.sp, color = RosePetal)
                            }
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
                        IconButton(onClick = { showLangPicker = true }) {
                            Icon(Icons.Default.Language, strings.languagePickerTitle, tint = DeepRose)
                        }
                        IconButton(onClick = { viewModel.triggerLock() }) {
                            Icon(Icons.Default.Lock, strings.lock, tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            },
            bottomBar = {
                // Primary navigation moved off the cramped top-bar icon row into a
                // labeled bottom tab bar. Explore is the persistent content;
                // Bookings/Profile open their sheets, Favorites toggles the filter
                // (so its selected state reflects showFavoritesOnly), Support opens
                // its own screen.
                NavigationBar(containerColor = DashboardSurface, tonalElevation = 0.dp) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = DeepRose,
                        selectedTextColor   = DeepRose,
                        unselectedIconColor = RoseGold,
                        unselectedTextColor = RoseGold,
                        indicatorColor      = BlushPink.copy(alpha = 0.6f)
                    )
                    NavigationBarItem(
                        selected = !showFavoritesOnly,
                        onClick  = { if (showFavoritesOnly) viewModel.toggleFavoritesOnly() },
                        icon     = { Icon(Icons.Default.Storefront, null) },
                        label    = { Text(strings.tabExplore, fontSize = 11.sp) },
                        colors   = itemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick  = { showBookingsSheet = true },
                        icon     = {
                            BadgedBox(badge = {
                                if (myAppointments.isNotEmpty()) {
                                    Badge(containerColor = DeepRose) {
                                        Text("${myAppointments.size}", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }) { Icon(Icons.Default.CalendarMonth, null) }
                        },
                        label    = { Text(strings.myBookings, fontSize = 11.sp) },
                        colors   = itemColors
                    )
                    NavigationBarItem(
                        selected = showFavoritesOnly,
                        onClick  = { if (!showFavoritesOnly) viewModel.toggleFavoritesOnly() },
                        icon     = {
                            Icon(if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                        },
                        label    = { Text(strings.favorites, fontSize = 11.sp) },
                        colors   = itemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick  = { showProfileSheet = true },
                        icon     = { Icon(Icons.Default.Person, null) },
                        label    = { Text(strings.myProfile, fontSize = 11.sp) },
                        colors   = itemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick  = { onNavigate(Screen.Support.route) },
                        icon     = { Icon(Icons.Default.SupportAgent, null) },
                        label    = { Text(strings.tabSupport, fontSize = 11.sp) },
                        colors   = itemColors
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Gradients.ScreenBg)
                    .padding(padding)
            ) {

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

                // ── Results count + Nearest sort ──────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (filteredSalons.isEmpty()) strings.noProvidersTitle
                               else strings.providersFound(filteredSalons.size),
                        fontSize = 11.sp,
                        color    = RoseGold,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = filtersActive,
                        onClick  = { showFilterSheet = true },
                        label    = { Text(strings.filtersButton, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Tune,
                                null,
                                tint = if (filtersActive) Color.White else RoseGold,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChipActive,
                            selectedLabelColor     = Color.White,
                            containerColor         = ChipInactive,
                            labelColor             = DeepRose
                        )
                    )
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
                            val distanceKm = customerLoc?.let { (la, lo) ->
                                if (salon.hasLocation())
                                    com.safebeauty.app.util.LocationHelper.distanceKm(la, lo, salon.latitude, salon.longitude)
                                else null
                            }
                            SalonCard(
                                salon            = salon,
                                isFavorite       = favoriteIds.contains(salon.id),
                                distanceKm       = distanceKm,
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
                    ReferralCard(
                        code     = referralCode,
                        credit   = referralCredit,
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
                  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Staff picker — only shown for a salon that actually has
                    // stylists. Switching stylist reloads the slots so the customer
                    // sees exactly when that person is free ("Any" = whole salon).
                    val staff = bookingIntent?.salon?.activeStaff().orEmpty()
                    if (staff.isNotEmpty()) {
                        val intent = bookingIntent
                        Text(strings.chooseStaff, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = (intent?.staffId ?: "").isEmpty(),
                                    onClick  = {
                                        if (intent != null && intent.dateMs != null) {
                                            bookingIntent = intent.copy(staffId = "", staffName = "")
                                            viewModel.loadSlotsForDate(intent.salon, intent.dateMs, "")
                                        }
                                    },
                                    label = { Text(strings.staffAny, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ChipActive,
                                        selectedLabelColor     = Color.White,
                                        containerColor         = ChipInactive,
                                        labelColor             = DeepRose
                                    )
                                )
                            }
                            items(staff) { member ->
                                FilterChip(
                                    selected = intent?.staffId == member.id,
                                    onClick  = {
                                        if (intent != null && intent.dateMs != null) {
                                            bookingIntent = intent.copy(staffId = member.id, staffName = member.name)
                                            viewModel.loadSlotsForDate(intent.salon, intent.dateMs, member.id)
                                        }
                                    },
                                    label = { Text(member.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ChipActive,
                                        selectedLabelColor     = Color.White,
                                        containerColor         = ChipInactive,
                                        labelColor             = DeepRose
                                    )
                                )
                            }
                        }
                    }
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
                    paymentMethod   = "ONLINE"
                    bookingIntent   = null
                    viewModel.clearPromo()
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
                        if (intent.staffName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, null, tint = RoseGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(intent.staffName, fontSize = 13.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                            }
                        }
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
                        Text(strings.paymentMethodLabel, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paymentMethod == "ONLINE",
                                onClick  = { paymentMethod = "ONLINE" },
                                label    = { Text(strings.paymentMethodOnline, fontSize = 13.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ChipActive,
                                    selectedLabelColor     = Color.White,
                                    containerColor         = ChipInactive,
                                    labelColor             = DeepRose
                                )
                            )
                            FilterChip(
                                selected = paymentMethod == "CASH",
                                onClick  = { paymentMethod = "CASH" },
                                label    = { Text(strings.paymentMethodCash, fontSize = 13.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ChipActive,
                                    selectedLabelColor     = Color.White,
                                    containerColor         = ChipInactive,
                                    labelColor             = DeepRose
                                )
                            )
                        }

                        // ── Promo code ────────────────────────────────────────
                        val applied = viewModel.promoApplied
                        if (applied != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AvailableGreen.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    strings.promoAppliedText(applied.discountAmount),
                                    fontSize = 13.sp,
                                    color = DeepRose,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    strings.promoRemove,
                                    fontSize = 13.sp,
                                    color = RoseGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.clearPromo() }
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value         = viewModel.promoInput,
                                    onValueChange = { viewModel.promoInput = it },
                                    label         = { Text(strings.promoCodeLabel, fontSize = 12.sp) },
                                    singleLine    = true,
                                    modifier      = Modifier.weight(1f),
                                    shape         = RoundedCornerShape(12.dp),
                                    isError       = viewModel.promoError != null,
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = RoseGold,
                                        unfocusedBorderColor = ChipInactive,
                                        cursorColor          = RoseGold,
                                        focusedLabelColor    = RoseGold
                                    )
                                )
                                Button(
                                    onClick = { viewModel.applyPromo(intent.salon.id, intent.service) },
                                    enabled = viewModel.promoInput.isNotBlank() && !viewModel.promoChecking,
                                    shape   = RoundedCornerShape(12.dp),
                                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                                ) {
                                    if (viewModel.promoChecking) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(strings.promoApplyButton, color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                            viewModel.promoError?.let { err ->
                                Text(err, fontSize = 11.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Identity must be verified before a customer can book.
                            if (viewModel.needsKycBeforeBooking()) {
                                showNotesDialog = false
                                bookingIntent   = null
                                viewModel.clearPromo()
                                viewModel.clearSlots()
                                onNavigate(Screen.Kyc.build(viewModel.customerId))
                            } else {
                                viewModel.bookService(intent.salon, intent.service, pendingSlotMs, bookingNotes, paymentMethod, intent.staffId)
                                showNotesDialog = false
                                pendingSlotMs   = 0L
                                bookingNotes    = ""
                                paymentMethod   = "ONLINE"
                                bookingIntent   = null
                                viewModel.clearSlots()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.confirmBooking, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNotesDialog = false
                        pendingSlotMs   = 0L
                        bookingNotes    = ""
                        paymentMethod   = "ONLINE"
                        bookingIntent   = null
                        viewModel.clearPromo()
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

                is CheckoutUiState.CashConfirmed -> {
                    // Same as Paid — the booking-confirmation dialog (driven by
                    // bookingConfirmSalonName / bookingConfirmCashAmount) shows
                    // the "bring cash" success message.
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
            val cashAmount = viewModel.bookingConfirmCashAmount
            AlertDialog(
                onDismissRequest = { viewModel.dismissConfirmation() },
                icon  = { Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp)) },
                title = { Text(strings.bookingRequestSent, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = {
                    Text(
                        if (cashAmount != null) strings.cashBookingConfirmText(salonName, cashAmount)
                        else strings.bookingConfirmText(salonName),
                        fontSize = 14.sp,
                        color    = Color(0xFF555555)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissConfirmation() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        if (viewModel.cancelFailed) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCancelFailed() },
                title = { Text(strings.actionFailedTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text(strings.actionFailedText, fontSize = 14.sp, color = Color(0xFF555555)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissCancelFailed() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── Admin announcement popup (one-time per broadcast) ─────────────────
        com.safebeauty.app.ui.components.AnnouncementPopup(broadcasts)

        // ── Filter / sort sheet ───────────────────────────────────────────────
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor   = ElegantCream
            ) {
                FilterSheetContent(
                    sortMode    = sortMode,
                    minRating   = minRating,
                    maxPrice    = maxPrice,
                    onSort      = { mode ->
                        // Nearest needs a location; request it if we don't have one
                        // yet (the launcher sets NEAREST once granted).
                        if (mode == SalonSort.NEAREST && !viewModel.hasCustomerLocation()) {
                            locationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            viewModel.setSortMode(mode)
                        }
                    },
                    onMinRating = { viewModel.setMinRating(it) },
                    onMaxPrice  = { viewModel.setMaxPrice(it) },
                    onReset     = { viewModel.resetFilters() }
                )
            }
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
                                otherName      = appt.salonName,
                                // Only an active booking keeps the chat open; a
                                // finished/cancelled one becomes a read-only archive.
                                active         = appt.status == "PENDING" || appt.status == "CONFIRMED"
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
                    onSupportClick    = { appt ->
                        showBookingsSheet = false
                        viewModel.contactSupport(appt)
                        onNavigate(
                            Screen.Chat.build(
                                conversationId = "support_${viewModel.customerId}",
                                myUserId       = viewModel.customerId,
                                myName         = currentUserName,
                                otherName      = strings.supportTitle,
                                active         = true
                            )
                        )
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
    onBook: () -> Unit,
    distanceKm: Double? = null
) {
    val strings  = LocalStrings.current
    val context  = LocalContext.current
    val gradient = remember(salon.salonName) { avatarGradient(salon.salonName) }

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
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(listOf(gradient.first, gradient.second))
                        )
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
                        if (distanceKm != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(RoseGold.copy(alpha = 0.12f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = strings.distanceKm(
                                        if (distanceKm < 10) "%.1f".format(distanceKm)
                                        else "%.0f".format(distanceKm)
                                    ),
                                    fontSize = 10.sp,
                                    color = RoseGold,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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
                if (salon.hasLocation()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                com.safebeauty.app.util.LocationHelper.openDirections(
                                    context, salon.latitude, salon.longitude, salon.salonName
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Default.Directions, null, tint = RoseGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.directions, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .then(
                            if (salon.isAvailable)
                                Modifier
                                    .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Gradients.BrandRose)
                                    .clickable(onClick = onBook)
                            else
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(UnavailableGrey.copy(alpha = 0.22f))
                        )
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text(
                        strings.book,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (salon.isAvailable) Color.White else UnavailableGrey
                    )
                }
            }
        }
    }
}

// ── Broadcast banner ──────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BroadcastBanner(broadcasts: List<BroadcastDocument>) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    // Track swiped-away ids in state so the banner disappears immediately; seed
    // from prefs so a dismissed announcement stays gone across restarts. Showing
    // only the single newest un-dismissed one keeps the top of the screen clean.
    var dismissed by remember { mutableStateOf(AnnouncementPrefs.dismissedIds(context)) }
    val newest = broadcasts
        .filter { it.id.isNotBlank() && it.id !in dismissed }
        .maxByOrNull { it.createdAt } ?: return

    key(newest.id) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    AnnouncementPrefs.dismiss(context, newest.id)
                    dismissed = dismissed + newest.id
                    true
                } else false
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF8F0))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    // Subtle "release to dismiss" affordance behind the card.
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RoseGold.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlushPink.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("📢", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = newest.message,
                            fontSize   = 13.sp,
                            color      = DeepRose,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text     = dateFmt.format(Date(newest.createdAt)),
                            fontSize = 11.sp,
                            color    = RoseGold
                        )
                    }
                    Icon(Icons.Default.Close, null, tint = RoseGold.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
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
private fun FilterSheetContent(
    sortMode: SalonSort,
    minRating: Double,
    maxPrice: Int,
    onSort: (SalonSort) -> Unit,
    onMinRating: (Double) -> Unit,
    onMaxPrice: (Int) -> Unit,
    onReset: () -> Unit
) {
    val strings = LocalStrings.current
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = ChipActive,
        selectedLabelColor     = Color.White,
        containerColor         = ChipInactive,
        labelColor             = DeepRose
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(strings.filtersTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DeepRose, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text(strings.filtersReset, color = RoseGold, fontSize = 13.sp) }
        }

        Text(strings.sortByLabel, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                SalonSort.RECOMMENDED to strings.sortRecommended,
                SalonSort.NEAREST     to strings.sortNearest,
                SalonSort.TOP_RATED   to strings.sortTopRated,
                SalonSort.PRICE_LOW   to strings.sortCheapest
            ).forEach { (mode, label) ->
                FilterChip(selected = sortMode == mode, onClick = { onSort(mode) },
                    label = { Text(label, fontSize = 12.sp) }, colors = chipColors)
            }
        }

        Text(strings.minRatingLabel, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.0 to strings.filterAny, 3.0 to "3.0+", 4.0 to "4.0+", 4.5 to "4.5+").forEach { (r, label) ->
                FilterChip(selected = minRating == r, onClick = { onMinRating(r) },
                    label = { Text(label, fontSize = 12.sp) }, colors = chipColors)
            }
        }

        Text(strings.maxPriceLabel, fontSize = 12.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to strings.filterAny, 500 to strings.priceUnder(500), 1000 to strings.priceUnder(1000), 2000 to strings.priceUnder(2000)).forEach { (p, label) ->
                FilterChip(selected = maxPrice == p, onClick = { onMaxPrice(p) },
                    label = { Text(label, fontSize = 12.sp) }, colors = chipColors)
            }
        }
    }
}

/**
 * A DoorDash-style horizontal progress tracker for a booking:
 * Requested → Confirmed → Completed. A cancelled booking shows a single red
 * state instead. "Completed" lights up once a confirmed appointment's time
 * has passed.
 */
@Composable
internal fun SalonBadgeChip(badge: SalonBadge, modifier: Modifier = Modifier) {
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

// ── Referral card (invite friends, earn credit) ───────────────────────────────

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
