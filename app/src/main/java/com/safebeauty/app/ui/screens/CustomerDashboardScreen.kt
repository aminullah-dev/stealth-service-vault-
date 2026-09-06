package com.safebeauty.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.safebeauty.app.util.formatIsolated
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.safebeauty.app.data.firebase.OfferDocument
import com.safebeauty.app.data.firebase.ReviewDocument
import com.safebeauty.app.data.firebase.SalonBadge
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.activeStaff
import com.safebeauty.app.data.firebase.serviceNamesFrom
import com.safebeauty.app.data.firebase.hasLocation
import com.safebeauty.app.data.firebase.LoyaltyTier
import com.safebeauty.app.data.firebase.WaitlistEntry
import com.safebeauty.app.data.firebase.badge
import com.safebeauty.app.navigation.Screen
import com.safebeauty.app.ui.theme.AppBrand
import com.safebeauty.app.ui.theme.LocalPalette
import com.safebeauty.app.ui.theme.paletteFor
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
import com.safebeauty.app.ui.theme.motionTween
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.RosePetal
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.ui.theme.WarmGold
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextFaint
import com.safebeauty.app.ui.theme.DangerRed
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.safebeauty.app.util.ratingLabel
import com.safebeauty.app.util.Analytics
import com.safebeauty.app.util.AnnouncementPrefs
import com.safebeauty.app.util.ImageUtils
import com.safebeauty.app.util.NotificationHelper
import com.safebeauty.app.viewmodel.CheckoutUiState
import com.safebeauty.app.viewmodel.GiftUiState
import com.safebeauty.app.viewmodel.TipUiState
import com.safebeauty.app.viewmodel.WalletUiState
import com.safebeauty.app.viewmodel.SalonSort
import com.safebeauty.app.viewmodel.DashboardViewModel
import com.safebeauty.app.viewmodel.ExportPhase
import com.safebeauty.app.viewmodel.ExportViewModel
import androidx.compose.material.icons.filled.Palette
import com.safebeauty.app.viewmodel.ThemeViewModel
import com.safebeauty.app.viewmodel.LanguageViewModel
import java.text.SimpleDateFormat
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
import androidx.compose.material.icons.filled.PhotoLibrary
import com.safebeauty.app.ui.components.SwipeHint
import kotlin.math.abs
import com.safebeauty.app.viewmodel.ReportTarget

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
    // firstOrNull guards a blank salon/user name — .first() would crash the whole
    // browse list on one empty-named doc.
    avatarGradients[(name.firstOrNull() ?: '?').lowercaseChar().code % avatarGradients.size]

// One guest in a group / event booking (bride + companions). Each guest gets
// their own services; the whole party is booked as a single appointment and the
// prices all sum together (the backend just receives the flattened service list).
private data class PartyGuest(
    val name: String,
    val services: List<String>
)

private data class BookingIntent(
    val salon: SalonDocument,
    val services: List<String>,
    val dateMs: Long? = null,
    // "" = any available stylist (or a solo salon).
    val staffId: String = "",
    val staffName: String = "",
    // Non-empty when booking a discounted package; the server applies its discount.
    val packageId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CustomerDashboardScreen(
    onSignOut: () -> Unit,
    onNavigate: (String) -> Unit           = {},
    viewModel: DashboardViewModel          = hiltViewModel(),
    langVm: LanguageViewModel              = hiltViewModel(),
    exportVm: ExportViewModel              = hiltViewModel(),
    changePinVm: ChangePinViewModel        = hiltViewModel(),
    themeVm: ThemeViewModel                = hiltViewModel(),
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
                "${change.serviceName} at ${change.salonName}\n${dateFmt.formatIsolated(Date(change.appointmentDate))}"
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

    LaunchedEffect(viewModel.signOutTriggered) {
        if (viewModel.signOutTriggered) {
            viewModel.resetSignOut()
            onSignOut()
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
    val matchingCount             by viewModel.matchingCount.collectAsStateWithLifecycle()
    val loadingSalons             by viewModel.loadingSalons.collectAsStateWithLifecycle()
    val sortMode                  by viewModel.sortMode.collectAsStateWithLifecycle()
    val minRating                 by viewModel.minRating.collectAsStateWithLifecycle()
    val maxPrice                  by viewModel.maxPrice.collectAsStateWithLifecycle()
    val customerLoc               by viewModel.customerLoc.collectAsStateWithLifecycle()
    var showFilterSheet           by remember { mutableStateOf(false) }
    var showBlocked               by remember { mutableStateOf(false) }
    val filtersActive = sortMode != SalonSort.RECOMMENDED || minRating > 0.0 || maxPrice > 0
    val myAppointments            by viewModel.myAppointments.collectAsStateWithLifecycle()
    val myWaitlist                by viewModel.myWaitlist.collectAsStateWithLifecycle()
    val refundStatusByAppointment by viewModel.refundStatusByAppointment.collectAsStateWithLifecycle()
    val loyaltyPoints             by viewModel.loyaltyPoints.collectAsStateWithLifecycle()
    val loyaltyTier               by viewModel.loyaltyTier.collectAsStateWithLifecycle()
    val referralCode              by viewModel.referralCode.collectAsStateWithLifecycle()
    val referralCredit            by viewModel.referralCredit.collectAsStateWithLifecycle()
    val recommendedSalons         by viewModel.recommendedSalons.collectAsStateWithLifecycle()
    val reviewsForSalon           by viewModel.reviewsForSalon.collectAsStateWithLifecycle()
    val blocked                   by viewModel.moderation.blocked.collectAsStateWithLifecycle()
    val reporting                 by viewModel.moderation.reporting.collectAsStateWithLifecycle()
    val reportSending             by viewModel.moderation.sending.collectAsStateWithLifecycle()
    val reportSent                by viewModel.moderation.sent.collectAsStateWithLifecycle()
    val reportFailed              by viewModel.moderation.failed.collectAsStateWithLifecycle()
    val galleryForSalon           by viewModel.galleryForSalon.collectAsStateWithLifecycle()
    val offersForSalon            by viewModel.offersForSalon.collectAsStateWithLifecycle()
    // Blocked salons' deals leave the strip too. The post and the story go and
    // the same salon's offer stays one row up, immediately after the app said
    // "you will not see anything from them again".
    val allActiveOffers           by viewModel.activeOffers.collectAsStateWithLifecycle()
    val activeOffers = allActiveOffers.filter { it.salonId !in blocked }
    val offerSalonIds             by viewModel.offerSalonIds.collectAsStateWithLifecycle()
    // Identity gate: deals & special offers are a verified-customer perk, shown
    // only once KYC is APPROVED. While the status is still loading (null) we lock
    // nothing yet, so a verified customer never sees the lock flash.
    val kycStatusValue            by viewModel.kycStatus.collectAsStateWithLifecycle()
    val dealsUnlocked = kycStatusValue == "APPROVED"
    val dealsLocked   = kycStatusValue != null && kycStatusValue != "APPROVED"
    val selectedCategoryIndex     by viewModel.selectedCategoryIndex.collectAsStateWithLifecycle()
    val selectedNeighborhoodIndex by viewModel.selectedNeighborhoodIndex.collectAsStateWithLifecycle()
    val isOffline                 by viewModel.isOffline.collectAsStateWithLifecycle()
    val currentUserName           by viewModel.currentUserName.collectAsStateWithLifecycle()
    val currentUserPhoto          by viewModel.currentUserPhoto.collectAsStateWithLifecycle()
    val favoriteIds               by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val showFavoritesOnly         by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val broadcasts                by viewModel.broadcasts.collectAsStateWithLifecycle()
    val stories                   by viewModel.stories.collectAsStateWithLifecycle()
    val searchQuery               by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Funnel step 1. Keyed on the result set so it fires once per distinct view
    // rather than on every recomposition.
    LaunchedEffect(filteredSalons.size, filtersActive, searchQuery) {
        Analytics.salonListViewed(filteredSalons.size, filtersActive || searchQuery.isNotBlank())
    }

    val categoryLabels = listOf(
        strings.categoryAll, strings.categoryHair, strings.categoryMakeup,
        strings.categoryNails, strings.categorySkincare, strings.categoryEyebrows
    )
    // "All neighborhoods" + every area of the selected city, localized.
    //
    // Index-parallel with the ViewModel's neighborhoodKeysFor because both now
    // read neighborhoodOptionsFor — they used to read two different functions
    // and the old comment here warned that a labels list built from a different
    // set would silently filter by the wrong district. It did, in Herat and
    // Mazar. See neighborhoodOptionsFor for what that cost.
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()
    val neighborhoodLabels = remember(strings.language, selectedCity) {
        listOf(strings.neighborhoodAll) +
            com.safebeauty.app.viewmodel.neighborhoodOptionsFor(selectedCity)
                .map { com.safebeauty.app.util.Areas.labelFor(it, strings.language) }
    }

    var showNeighborhoodMenu by remember { mutableStateOf(false) }
    var showBookingsSheet    by remember { mutableStateOf(false) }
    var showLangPicker       by remember { mutableStateOf(false) }
    var showProfileSheet     by remember { mutableStateOf(false) }
    var showSalonDetail      by remember { mutableStateOf<SalonDocument?>(null) }
    // Shown INSTEAD of starting a booking when the account isn't verified yet.
    // Asking at the end — after services, slot, notes and guests are entered —
    // meant discarding all of it, which is the worst possible moment.
    var showKycNotice        by remember { mutableStateOf(false) }
    var showThemePicker      by remember { mutableStateOf(false) }
    // Funnel step 2. Watching the state rather than each opener means every path
    // into the detail sheet is counted, including ones added later.
    LaunchedEffect(showSalonDetail?.id) {
        showSalonDetail?.let { Analytics.salonOpened(it.id) }
    }
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
    // Services the customer has ticked in the multi-select booking dialog (one
    // appointment can cover several services; the total is their summed price).
    val selectedServices = remember { mutableStateListOf<String>() }
    // Group / event booking (bride + companions): guests added so far, the
    // name being typed for the next guest, and a human-readable party summary
    // that rides along in the booking notes.
    var showGroupDialog by remember { mutableStateOf(false) }
    val partyGuests     = remember { mutableStateListOf<PartyGuest>() }
    var guestNameInput  by remember { mutableStateOf("") }
    var partyNote       by remember { mutableStateOf("") }
    // Gift-card dialog state.
    var showGiftDialog by remember { mutableStateOf(false) }
    var giftPhone      by remember { mutableStateOf("") }
    var giftAmount     by remember { mutableStateOf("") }
    var giftMessage    by remember { mutableStateOf("") }
    // Loyalty redeem dialog.
    var showWalletDialog by remember { mutableStateOf(false) }
    var walletAmount     by remember { mutableStateOf("") }
    var showRedeemDialog by remember { mutableStateOf(false) }
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
    var reviewTarget        by remember { mutableStateOf<AppointmentDocument?>(null) }
    var showOverflow        by remember { mutableStateOf(false) }
    var tipTarget           by remember { mutableStateOf<AppointmentDocument?>(null) }

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
                        // Seven unlabeled icons crowded the bar and squeezed the
                        // title. Only notifications earns a permanent slot — it
                        // carries an unread badge. The rest are occasional, and a
                        // named menu makes them MORE discoverable than a row of
                        // mystery glyphs was.
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
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, strings.menuMore, tint = DeepRose)
                            }
                            DropdownMenu(
                                expanded         = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                @Composable
                                fun item(
                                    icon: androidx.compose.ui.graphics.vector.ImageVector,
                                    label: String,
                                    enabled: Boolean = true,
                                    onClick: () -> Unit
                                ) = DropdownMenuItem(
                                    text        = { Text(label, color = DeepRose, fontSize = 14.sp) },
                                    leadingIcon = { Icon(icon, null, tint = RoseGold) },
                                    enabled     = enabled,
                                    onClick     = { showOverflow = false; onClick() }
                                )
                                item(Icons.Default.Map, strings.mapTitle) {
                                    onNavigate(Screen.SalonMap.build(viewModel.customerId))
                                }
                                item(Icons.Default.SupportAgent, strings.tabSupport) {
                                    onNavigate(Screen.Support.route)
                                }
                                item(Icons.Default.Palette, strings.menuTheme) { showThemePicker = true }
                                item(Icons.Default.Language, strings.languagePickerTitle) { showLangPicker = true }
                                item(
                                    Icons.Default.Download,
                                    strings.exportTitle,
                                    enabled = exportVm.phase != ExportPhase.WORKING
                                ) { exportVm.export() }
                                // Only once she has blocked somebody. An empty
                                // list is a menu row teaching her about a
                                // feature she has not used.
                                if (blocked.isNotEmpty()) {
                                    item(Icons.Default.Block, strings.blockedTitle) {
                                        showBlocked = true
                                    }
                                }
                                HorizontalDivider()
                                item(Icons.AutoMirrored.Filled.Logout, strings.signOut) {
                                    viewModel.signOut()
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            },
            bottomBar = {
                // Primary navigation moved off the cramped top-bar icon row into a
                // labeled bottom tab bar. Salons is the persistent content;
                // Bookings/Profile open their sheets, Favorites toggles the filter
                // (so its selected state reflects showFavoritesOnly), Discover
                // opens its own screen.
                //
                // Discover took the fifth slot from Support. Discover is where a
                // customer decides which salon to book -- the reason to open the
                // app at all -- and it had been sitting in the overflow menu,
                // which is no place for a primary destination. Support is a
                // utility reached once in a while, and the menu now carries
                // labels, so it is easier to find there than an unlabeled icon
                // ever was here.
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
                        onClick  = { onNavigate(Screen.Feed.build(viewModel.customerId)) },
                        icon     = { Icon(Icons.Default.PhotoLibrary, null) },
                        label    = { Text(strings.feedTitle, fontSize = 11.sp) },
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
                    // Explore and Favourites are the same screen with a
                    // different filter, so a horizontal swipe between them is
                    // honest. The other three bottom-bar items are not tabs at
                    // all — two open a sheet and one leaves for another screen —
                    // and nothing can be paged into a modal sheet, which is why
                    // this is a two-state toggle and not a pager.
                    //
                    // Direction-agnostic on purpose: with two states, any
                    // decisive horizontal drag means "the other one", and that
                    // is the same gesture in Dari, Pashto and English rather
                    // than one that reverses with the layout direction.
                    .pointerInput(Unit) {
                        val threshold = 90.dp.toPx()
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragEnd   = {
                                if (abs(travelled) > threshold) viewModel.toggleFavoritesOnly()
                            },
                            onDragCancel = { travelled = 0f },
                            onHorizontalDrag = { _, amount -> travelled += amount },
                        )
                    }
            ) {

                SwipeHint(
                    text    = strings.swipeHintExploreFavourites,
                    hintKey = "exploreFav",
                )

                // ── Offline banner ────────────────────────────────────────────
                AnimatedVisibility(
                    visible = isOffline,
                    enter   = fadeIn(motionTween()) + expandVertically(motionTween()),
                    exit    = fadeOut(motionTween()) + shrinkVertically(motionTween()),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .fillMaxWidth()
                            .background(DangerRed)
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

                // ── Today's free chairs ───────────────────────────────────────
                // These used to live only inside Discover, one tap away. A story
                // says "two chairs free this afternoon" and deletes itself in 24
                // hours -- of everything on this screen it is the item with the
                // shortest shelf life, so hiding it behind a tap wasted most of
                // what it was worth. Tapping a ring opens that exact story in
                // Discover rather than dropping the customer at the top of it.
                if (stories.isNotEmpty()) {
                    StoryRow(
                        stories = stories,
                        onOpen  = { onNavigate(Screen.Feed.build(viewModel.customerId, it.id)) }
                    )
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
                            IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.CheckCircle, strings.clearSearch, tint = ChipInactive, modifier = Modifier.size(18.dp))
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

                // ── City picker ───────────────────────────────────────────────
                //
                // The view model has carried a city for a while and nothing on
                // screen could set it, so it sat blank and the neighbourhood
                // menu below opened onto a single "all" row — districts only
                // exist inside a city, so with none chosen there was nothing to
                // list. A customer saw an empty dropdown and no way to fix it.
                //
                // Blank stays a real option: with four cities and a handful of
                // salons, "everywhere" is the honest default, and picking a city
                // is narrowing rather than a gate to get past.
                var showCityMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    OutlinedButton(
                        onClick  = { showCityMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ChipInactive),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose)
                    ) {
                        Icon(Icons.Default.LocationCity, null, modifier = Modifier.size(15.dp), tint = RoseGold)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (selectedCity.isBlank()) strings.allCities
                            else com.safebeauty.app.util.Areas.cityLabel(selectedCity, strings.language),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            color    = DeepRose
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = RoseGold)
                    }
                    DropdownMenu(
                        expanded         = showCityMenu,
                        onDismissRequest = { showCityMenu = false },
                        modifier         = Modifier.background(ElegantCream)
                    ) {
                        DropdownMenuItem(
                            text    = { Text(strings.allCities, fontSize = 13.sp, color = DeepRose) },
                            onClick = { viewModel.onCityChanged(""); showCityMenu = false }
                        )
                        com.safebeauty.app.util.Areas.liveCities.forEach { city ->
                            DropdownMenuItem(
                                text    = {
                                    Text(
                                        com.safebeauty.app.util.Areas.cityLabel(city.key, strings.language),
                                        fontSize = 13.sp, color = DeepRose,
                                    )
                                },
                                onClick = { viewModel.onCityChanged(city.key); showCityMenu = false }
                            )
                        }
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
                        // The server's count, not the loaded page's size. These
                        // used to be the same number because the whole collection
                        // was in memory; with paging, the page size would read as
                        // "20 providers found" no matter how many there are.
                        // No number at all when the count is unknown, rather
                        // than a number that might be wrong.
                        text = when (matchingCount) {
                            null -> ""
                            0    -> strings.noProvidersTitle
                            else -> strings.providersFound(matchingCount!!)
                        },
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

                // ── Discovery feed ────────────────────────────────────────────
                // Recommendations + deals now scroll WITH the salon list inside one
                // LazyColumn instead of sitting in the fixed header above it. Before,
                // the carousel + deals strip permanently ate ~320dp at the top and
                // squeezed the real list into a sliver, hiding the other salons below
                // the fold; now they scroll away and the list gets the full height.
                // The search/filter header above stays pinned.
                // loadingSalons was collected and never read, so the first frame of
                // Discover said "no providers found" before a single document had
                // come back — the same sentence a genuinely empty city produces,
                // which is the one thing it must not be confused with.
                if (loadingSalons && filteredSalons.isEmpty() && recommendedSalons.isEmpty()) {
                    LoadingBox()
                } else if (filteredSalons.isEmpty() && recommendedSalons.isEmpty() && activeOffers.isEmpty()) {
                    val narrowing = filtersActive || showFavoritesOnly || searchQuery.isNotBlank() ||
                    selectedCategoryIndex != 0 || selectedNeighborhoodIndex != 0
                    SalonEmptyState(
                        favoritesOnly  = showFavoritesOnly,
                        modifier       = Modifier.fillMaxSize(),
                        onClearFilters = if (narrowing) ({ viewModel.clearAllFilters() }) else null
                    )
                } else {
                    val listState = rememberLazyListState()
                    val feedScope = rememberCoroutineScope()
                    // Show a jump-to-top pill once the user has scrolled a few
                    // cards deep — a fast way back to search/filters in a long list.
                    val showJumpTop by remember {
                        derivedStateOf { listState.firstVisibleItemIndex > 3 }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state               = listState,
                        contentPadding      = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier            = Modifier.fillMaxSize()
                    ) {
                        if (recommendedSalons.isNotEmpty() && searchQuery.isBlank()) {
                            item(key = "recommended") {
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
                        }
                        if (activeOffers.isNotEmpty() && searchQuery.isBlank()) {
                            item(key = "deals") {
                                if (dealsUnlocked) {
                                    DealsStrip(
                                        offers  = activeOffers,
                                        onOpen  = { salonId ->
                                            viewModel.findSalon(salonId)?.let { salon ->
                                                showSalonDetail = salon
                                                viewModel.setActiveSalon(salon.id)
                                            }
                                        }
                                    )
                                } else if (dealsLocked) {
                                    // Unverified customers see the deals exist but must
                                    // verify their identity to unlock them — the nudge.
                                    LockedDealsTeaser(
                                        count    = activeOffers.size,
                                        onUnlock = { onNavigate(Screen.Kyc.build(viewModel.customerId)) }
                                    )
                                }
                            }
                        }
                        if (filteredSalons.isEmpty()) {
                            item(key = "empty") {
                                // The common case: recommendations or deals still have
                                // content, so only the filtered list came up empty.
                                val narrowing = filtersActive || showFavoritesOnly ||
                                    searchQuery.isNotBlank() ||
                                    selectedCategoryIndex != 0 || selectedNeighborhoodIndex != 0
                                SalonEmptyState(
                                    favoritesOnly  = showFavoritesOnly,
                                    modifier       = Modifier.fillParentMaxWidth().padding(vertical = 40.dp),
                                    onClearFilters = if (narrowing) ({ viewModel.clearAllFilters() }) else null
                                )
                            }
                        } else {
                            // Sticky section bar: the count + "All salons" label stay
                            // pinned at the top of the list while the cards scroll, so
                            // the user always knows how many salons there are.
                            stickyHeader(key = "allHeader") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ElegantCream)
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Storefront, null, tint = RoseGold, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        strings.allSalonsTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 14.sp,
                                        color      = DeepRose,
                                        modifier   = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(DeepRose)
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "${matchingCount ?: filteredSalons.size}",
                                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            itemsIndexed(filteredSalons, key = { _, s -> s.id }) { index, salon ->
                                // Fetch the next page a few cards before the end,
                                // so scrolling does not stop to wait. loadMore
                                // ignores the call while one is in flight or the
                                // end is reached, so this cannot stampede.
                                if (index >= filteredSalons.size - 4) {
                                    LaunchedEffect(filteredSalons.size, index) {
                                        viewModel.loadMoreSalons()
                                    }
                                }
                                val distanceKm = customerLoc?.let { (la, lo) ->
                                    if (salon.hasLocation())
                                        com.safebeauty.app.util.LocationHelper.distanceKm(la, lo, salon.latitude, salon.longitude)
                                    else null
                                }
                                SalonCard(
                                    salon            = salon,
                                    modifier         = Modifier.padding(horizontal = 16.dp),
                                    isFavorite       = favoriteIds.contains(salon.id),
                                    distanceKm       = distanceKm,
                                    hasOffer         = dealsUnlocked && offerSalonIds.contains(salon.id),
                                    onToggleFavorite = { viewModel.toggleFavorite(salon.id) },
                                    onBook           = { showSalonDetail = salon; viewModel.setActiveSalon(salon.id) }
                                )
                            }
                        }
                    }
                    // Jump-to-top pill — floats over the list once scrolled a few
                    // cards deep, so the user isn't stuck scrolling all the way back.
                    // (A plain `if` rather than AnimatedVisibility: inside a Box the
                    // scoped AnimatedVisibility overloads are ambiguous.)
                    if (showJumpTop) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .shadow(6.dp, RoundedCornerShape(24.dp), clip = false)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Gradients.BrandRose)
                                .clickable { feedScope.launch { listState.animateScrollToItem(0) } }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.backToTop, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    }
                }
            }
        }

        // ── Language picker dialog ────────────────────────────────────────────
        if (showKycNotice) {
            AlertDialog(
                onDismissRequest = { showKycNotice = false },
                icon  = { Icon(Icons.Default.Lock, null, tint = RoseGold) },
                title = { Text(strings.kycBeforeBookingTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text(strings.kycBeforeBookingText, fontSize = 14.sp, color = TextStrong) },
                confirmButton = {
                    Button(
                        onClick = { showKycNotice = false; onNavigate(Screen.Kyc.build(viewModel.customerId)) },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.kycVerifyNow, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showKycNotice = false }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        if (showThemePicker) {
            val brand by themeVm.brand.collectAsStateWithLifecycle()
            ThemePickerDialog(
                current   = brand,
                onPick    = { themeVm.setBrand(it); showThemePicker = false },
                onDismiss = { showThemePicker = false }
            )
        }

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
                    WalletCard(
                        credit   = referralCredit,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                        onTopUp  = { showWalletDialog = true }
                    )
                    LoyaltyCard(
                        points   = loyaltyPoints,
                        tier     = loyaltyTier,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                        onRedeem = { showRedeemDialog = true }
                    )
                    ReferralCard(
                        code     = referralCode,
                        credit   = referralCredit,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
                    )
                    OutlinedButton(
                        onClick = { showGiftDialog = true },
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp).fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, RoseGold),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose)
                    ) {
                        Icon(Icons.Default.CardGiftcard, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.giftCard, fontWeight = FontWeight.SemiBold)
                    }
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
                        Text(strings.photoConfirmBody, fontSize = 13.sp, color = TextStrong, textAlign = TextAlign.Center)
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
            // One appointment can bundle several services (e.g. haircut + makeup);
            // the running total is the sum of every ticked service's price.
            val servicesTotal = selectedServices.sumOf { salon?.pricePerService?.get(it) ?: 0 }
            AlertDialog(
                onDismissRequest = { showServiceDialog = false; bookingIntent = null; selectedServices.clear() },
                title = {
                    Column {
                        BookingStepper(1, listOf(strings.stepServices, strings.stepTime, strings.stepConfirm))
                        Spacer(Modifier.height(10.dp))
                        Text(strings.chooseService, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        salon?.services?.forEach { service ->
                            val price    = salon.pricePerService[service] ?: 0
                            val selected = selectedServices.contains(service)
                            Button(
                                onClick = {
                                    if (selected) selectedServices.remove(service)
                                    else          selectedServices.add(service)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) DeepRose else BlushPink
                                )
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint     = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            service,
                                            fontSize   = 14.sp,
                                            color      = if (selected) Color.White else DeepRose,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (price > 0) {
                                        Text(
                                            "%,d AFN".format(price),
                                            fontSize   = 12.sp,
                                            color      = if (selected) Color.White else RoseGold,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        if (selectedServices.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(strings.total, fontSize = 14.sp, color = DeepRose, fontWeight = FontWeight.Bold)
                                Text("%,d AFN".format(servicesTotal), fontSize = 15.sp, color = DeepRose, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Switch to the group / event flow (bride + companions).
                        TextButton(
                            onClick = {
                                showServiceDialog = false
                                selectedServices.clear()
                                partyGuests.clear()
                                guestNameInput = ""
                                showGroupDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = RoseGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.groupBooking, color = RoseGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                confirmButton  = {
                    TextButton(
                        enabled = selectedServices.isNotEmpty(),
                        onClick = {
                            showServiceDialog = false
                            bookingIntent = bookingIntent?.copy(services = selectedServices.toList())
                            showDatePicker = true
                        }
                    ) {
                        Text(
                            strings.continueLabel,
                            color      = if (selectedServices.isEmpty()) RoseGold.copy(alpha = 0.4f) else DeepRose,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton  = {
                    TextButton(onClick = { showServiceDialog = false; bookingIntent = null; selectedServices.clear() }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Step 1b: Group / event booking (bride + companions) ───────────────
        if (showGroupDialog) {
            val salon = bookingIntent?.salon
            fun priceOf(s: String) = salon?.pricePerService?.get(s) ?: 0
            val partyTotal = partyGuests.sumOf { g -> g.services.sumOf { priceOf(it) } }
            AlertDialog(
                onDismissRequest = {
                    showGroupDialog = false; bookingIntent = null
                    partyGuests.clear(); selectedServices.clear(); guestNameInput = ""
                },
                title = { Text(strings.groupBooking, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier            = Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Guests already added
                        partyGuests.forEachIndexed { index, g ->
                            val subtotal = g.services.sumOf { priceOf(it) }
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BlushPink.copy(alpha = 0.40f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        g.name.ifBlank { "${strings.guest} ${index + 1}" },
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DeepRose
                                    )
                                    Text(g.services.joinToString("، "), fontSize = 11.sp, color = RoseGold)
                                }
                                Text("%,d AFN".format(subtotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeepRose)
                                IconButton(onClick = { partyGuests.removeAt(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = strings.remove, tint = RoseGold, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = BlushPink)
                        // Add-a-guest form: name + tap services + running subtotal
                        OutlinedTextField(
                            value         = guestNameInput,
                            onValueChange = { guestNameInput = it },
                            label         = { Text(strings.guestNameHint, fontSize = 12.sp) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement   = Arrangement.spacedBy(6.dp)
                        ) {
                            salon?.services?.forEach { service ->
                                val selected = selectedServices.contains(service)
                                val price    = priceOf(service)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) DeepRose else BlushPink.copy(alpha = 0.55f))
                                        .clickable {
                                            if (selected) selectedServices.remove(service) else selectedServices.add(service)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        if (price > 0) "$service · %,d".format(price) else service,
                                        fontSize   = 12.sp,
                                        color      = if (selected) Color.White else DeepRose,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                partyGuests.add(PartyGuest(guestNameInput.trim(), selectedServices.toList()))
                                guestNameInput = ""
                                selectedServices.clear()
                            },
                            enabled  = selectedServices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.addGuest, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        if (partyGuests.isNotEmpty()) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.total, fontWeight = FontWeight.Bold, color = DeepRose)
                                Text("%,d AFN".format(partyTotal), fontWeight = FontWeight.Bold, color = DeepRose)
                            }
                        }
                    }
                },
                confirmButton  = {
                    TextButton(
                        enabled = partyGuests.isNotEmpty(),
                        onClick = {
                            val flat = partyGuests.flatMap { it.services }
                            partyNote = partyGuests.mapIndexed { i, g ->
                                "${g.name.ifBlank { "${strings.guest} ${i + 1}" }}: ${g.services.joinToString("، ")}"
                            }.joinToString("\n")
                            bookingIntent   = bookingIntent?.copy(services = flat)
                            showGroupDialog = false
                            showDatePicker  = true
                        }
                    ) {
                        Text(
                            strings.continueLabel,
                            color      = if (partyGuests.isEmpty()) RoseGold.copy(alpha = 0.4f) else DeepRose,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton  = {
                    TextButton(onClick = {
                        showGroupDialog = false; bookingIntent = null
                        partyGuests.clear(); selectedServices.clear(); guestNameInput = ""
                    }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Gift card dialog ──────────────────────────────────────────────────
        if (showGiftDialog) {
            val giftState = viewModel.giftState
            val giftCtx   = LocalContext.current
            // Open the HesabPay page as soon as the session is created.
            LaunchedEffect(giftState) {
                if (giftState is GiftUiState.OpenCheckout) {
                    runCatching {
                        viewModel.beginExternalPayment()
                        giftCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(giftState.url)))
                    }
                }
            }
            AlertDialog(
                onDismissRequest = { showGiftDialog = false; viewModel.resetGift() },
                title = { Text(strings.giftCardTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = giftPhone, onValueChange = { giftPhone = it },
                            label = { Text(strings.giftRecipientPhone, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = giftAmount,
                            onValueChange = { v -> if (v.length <= 6 && v.all(Char::isDigit)) giftAmount = v },
                            label = { Text(strings.giftAmount, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = giftMessage, onValueChange = { giftMessage = it },
                            label = { Text(strings.giftMessageHint, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        when (giftState) {
                            is GiftUiState.Creating -> Text(strings.paymentPreparing, fontSize = 12.sp, color = RoseGold)
                            is GiftUiState.Sent     -> Text(strings.giftSent, fontSize = 12.sp, color = AvailableGreen)
                            is GiftUiState.Failed   -> Text(strings.giftFailed, fontSize = 12.sp, color = DangerRed)
                            else                    -> {}
                        }
                    }
                },
                confirmButton = {
                    if (giftState is GiftUiState.Sent) {
                        Button(
                            onClick = { showGiftDialog = false; viewModel.resetGift(); giftPhone = ""; giftAmount = ""; giftMessage = "" },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.goToApp, color = Color.White) }
                    } else {
                        val amt = giftAmount.toLongOrNull() ?: 0L
                        Button(
                            enabled = giftPhone.isNotBlank() && amt > 0 && giftState !is GiftUiState.Creating,
                            onClick = { viewModel.sendGiftCard(giftPhone.trim(), amt, giftMessage.trim()) },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.giftCard, color = Color.White) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGiftDialog = false; viewModel.resetGift() }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Wallet top-up dialog ──────────────────────────────────────────────
        if (showWalletDialog) {
            val walletState = viewModel.walletState
            val walletCtx   = LocalContext.current
            // Open the HesabPay page as soon as the session is created.
            LaunchedEffect(walletState) {
                if (walletState is WalletUiState.OpenCheckout) {
                    runCatching {
                        viewModel.beginExternalPayment()
                        walletCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(walletState.url)))
                    }
                }
            }
            AlertDialog(
                onDismissRequest = { showWalletDialog = false; viewModel.resetWallet() },
                title = { Text(strings.walletTopUpTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.walletTopUpHint, fontSize = 13.sp, color = TextStrong)
                        // Quick-pick amounts.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(200L, 500L, 1000L).forEach { preset ->
                                OutlinedButton(
                                    onClick = { walletAmount = preset.toString() },
                                    shape   = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    border  = androidx.compose.foundation.BorderStroke(1.dp, RoseGold),
                                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose)
                                ) { Text("%,d".format(preset), fontSize = 13.sp) }
                            }
                        }
                        OutlinedTextField(
                            value = walletAmount,
                            onValueChange = { v -> if (v.length <= 6 && v.all(Char::isDigit)) walletAmount = v },
                            label = { Text(strings.walletTopUpAmount, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        when (walletState) {
                            is WalletUiState.Creating -> Text(strings.paymentPreparing, fontSize = 12.sp, color = RoseGold)
                            is WalletUiState.Done     -> Text(strings.walletTopUpDone, fontSize = 12.sp, color = AvailableGreen)
                            is WalletUiState.Failed   -> Text(strings.walletTopUpFailed, fontSize = 12.sp, color = DangerRed)
                            else                      -> {}
                        }
                    }
                },
                confirmButton = {
                    if (walletState is WalletUiState.Done) {
                        Button(
                            onClick = { showWalletDialog = false; viewModel.resetWallet(); walletAmount = "" },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.goToApp, color = Color.White) }
                    } else {
                        val amt = walletAmount.toLongOrNull() ?: 0L
                        Button(
                            enabled = amt > 0 && walletState !is WalletUiState.Creating,
                            onClick = { viewModel.topUpWallet(amt) },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.walletTopUpAction, color = Color.White) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWalletDialog = false; viewModel.resetWallet() }) {
                        Text(strings.cancel, color = RoseGold)
                    }
                },
                containerColor = ElegantCream
            )
        }

        // ── Loyalty redeem dialog ─────────────────────────────────────────────
        if (showRedeemDialog) {
            val eligible = (loyaltyPoints / 100) * 100
            val redeemResult = viewModel.redeemResult
            LaunchedEffect(redeemResult) {
                if (redeemResult == "redeemed") { showRedeemDialog = false; viewModel.clearRedeemResult() }
            }
            AlertDialog(
                onDismissRequest = { showRedeemDialog = false; viewModel.clearRedeemResult() },
                title = { Text(strings.redeemPoints, fontWeight = FontWeight.Bold, color = DeepRose) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.redeemHint, fontSize = 13.sp, color = TextStrong)
                        if (eligible >= 100) {
                            Text(
                                "$eligible ${strings.loyaltyPtsUnit} → %,d AFN".format(eligible),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DeepRose
                            )
                        }
                        if (redeemResult == "redeem_failed") {
                            Text(strings.redeemTooFew, fontSize = 12.sp, color = DangerRed)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = eligible >= 100,
                        onClick = { viewModel.redeemLoyalty(eligible) },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.redeemPoints, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showRedeemDialog = false; viewModel.clearRedeemResult() }) {
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
                                viewModel.loadSlotsForDate(bookingIntent!!.salon, selectedDate, services = bookingIntent!!.services,
                                    party = partyGuests.map { it.name to it.services })
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
                title = {
                    Column {
                        BookingStepper(2, listOf(strings.stepServices, strings.stepTime, strings.stepConfirm))
                        Spacer(Modifier.height(10.dp))
                        Text(strings.selectTimeSlot, fontWeight = FontWeight.Bold, color = DeepRose)
                    }
                },
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
                                            viewModel.loadSlotsForDate(intent.salon, intent.dateMs, "", intent.services,
                                                partyGuests.map { it.name to it.services })
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
                                            viewModel.loadSlotsForDate(intent.salon, intent.dateMs, member.id, intent.services,
                                                partyGuests.map { it.name to it.services })
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
                            // Before the empty branch: "no times left" and "we
                            // could not find out" look identical on screen and
                            // are not the same thing. One means try another day;
                            // the other means try again.
                            viewModel.slotsFailed -> Text(
                                strings.slotsLoadFailed,
                                color = DangerRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
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
                                            Text(timeFmt.formatIsolated(Date(slotMs)), color = DeepRose, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                    partyNote       = ""
                    partyGuests.clear()
                    paymentMethod   = "ONLINE"
                    bookingIntent   = null
                    viewModel.clearPromo()
                    viewModel.clearSlots()
                },
                title = {
                    Column {
                        BookingStepper(3, listOf(strings.stepServices, strings.stepTime, strings.stepConfirm))
                        Spacer(Modifier.height(10.dp))
                        Text(strings.bookingNotesTitle, fontWeight = FontWeight.Bold, color = DeepRose)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text     = "${intent.services.joinToString("، ")} · ${intent.salon.salonName}",
                            fontSize = 14.sp,
                            color    = DeepRose,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text     = dateFmt.formatIsolated(Date(pendingSlotMs)),
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
                                    onClick = { viewModel.applyPromo(intent.salon.id, intent.services) },
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
                                Text(err, fontSize = 11.sp, color = DangerRed)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Identity must be verified before a customer can book.
                            if (viewModel.needsKycBeforeBooking()) {
                                // Safety net only — the flow is normally stopped
                                // before any of this is entered (see showKycNotice).
                                // The draft is deliberately KEPT so that returning
                                // from verification lands the user back on a filled
                                // booking instead of an empty one.
                                showNotesDialog = false
                                onNavigate(Screen.Kyc.build(viewModel.customerId))
                            } else {
                                val fullNotes = listOf(partyNote, bookingNotes).filter { it.isNotBlank() }.joinToString("\n")
                                // The guest list goes as a list, not only as the
                                // note. The note is still sent because it reads
                                // well on the salon's screen today, but the
                                // structure is what lets the server see that
                                // everyone works at once instead of queueing five
                                // guests onto one stylist.
                                val partyPayload = partyGuests.map { g ->
                                    mapOf<String, Any>("name" to g.name, "services" to g.services)
                                }
                                viewModel.bookService(intent.salon, intent.services, pendingSlotMs, fullNotes, paymentMethod, intent.staffId, intent.packageId, partyPayload)
                                showNotesDialog = false
                                pendingSlotMs   = 0L
                                bookingNotes    = ""
                                partyNote       = ""
                                partyGuests.clear()
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
                        partyNote       = ""
                        partyGuests.clear()
                        guestNameInput  = ""
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
                viewModel.beginExternalPayment()
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
                                Text(strings.paymentPreparing, fontSize = 14.sp, color = TextStrong)
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
                                Text(strings.paymentOpenInstruction, fontSize = 13.sp, color = TextStrong)
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
                    LaunchedEffect(Unit) { Analytics.bookingFailed(state.message) }
                    // The message field now carries the server's reason code, so
                    // we can explain exactly what went wrong AND offer the recovery
                    // that saves the user from re-entering the whole booking.
                    val reason  = state.message
                    val message = when (reason) {
                        "SLOT_TAKEN"        -> strings.bookFailSlotTaken
                        "SALON_CLOSED"      -> strings.bookFailSalonClosed
                        "STAFF_UNAVAILABLE" -> strings.bookFailStaffUnavailable
                        "FREE_USE_CASH"     -> strings.bookFailFreeUseCash
                        "PROMO_LIMIT"       -> strings.bookFailPromoLimit
                        "PARTY"             -> strings.bookFailPartyPrepay
                        "NO_SHOW_HISTORY"   -> strings.bookFailMustPrepay
                        else                -> strings.paymentFailed
                    }
                    AlertDialog(
                        onDismissRequest = { viewModel.cancelCheckout() },
                        title = { Text(strings.bookFailTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                        text  = { Text(message, fontSize = 14.sp, color = TextStrong) },
                        confirmButton = {
                            when (reason) {
                                "FREE_USE_CASH" -> Button(
                                    onClick = { viewModel.retryLastAsCash() },
                                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                                ) { Text(strings.payCashInstead, color = Color.White) }
                                // The refusal is the point of the rule, so the
                                // recovery is not to argue with it but to make
                                // the alternative one tap away. Losing the
                                // booking here would punish the salon twice.
                                "PARTY", "NO_SHOW_HISTORY" -> Button(
                                    onClick = { viewModel.retryLastAsOnline() },
                                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                                ) { Text(strings.payOnlineInstead, color = Color.White) }
                                "PROMO_LIMIT" -> Button(
                                    onClick = { viewModel.retryLastWithoutPromo() },
                                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                                ) { Text(strings.continueWithoutCode, color = Color.White) }
                                "SLOT_TAKEN", "SALON_CLOSED", "STAFF_UNAVAILABLE" -> Button(
                                    onClick = {
                                        // Reopen the date picker with the same salon
                                        // + services so only the time changes.
                                        val salon = viewModel.lastAttemptSalon
                                        if (salon != null) {
                                            selectedServices.clear()
                                            selectedServices.addAll(viewModel.lastAttemptServiceList)
                                            bookingIntent = BookingIntent(
                                                salon     = salon,
                                                services  = viewModel.lastAttemptServiceList,
                                                staffId   = viewModel.lastAttemptStaff,
                                                packageId = viewModel.lastAttemptPackage
                                            )
                                            showDatePicker = true
                                        }
                                        viewModel.cancelCheckout()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                                ) { Text(strings.chooseAnotherTime, color = Color.White) }
                                else -> Button(
                                    onClick = { viewModel.cancelCheckout() },
                                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                                ) { Text(strings.ok, color = Color.White) }
                            }
                        },
                        dismissButton = {
                            if (reason != "GENERIC") {
                                TextButton(onClick = { viewModel.cancelCheckout() }) {
                                    Text(strings.cancel, color = RoseGold)
                                }
                            }
                        },
                        containerColor = ElegantCream
                    )
                }

                is CheckoutUiState.Paid -> {
                    LaunchedEffect(Unit) { Analytics.bookingCompleted(cash = false) }
                    // Reset checkout; the booking-confirmation dialog (driven by
                    // bookingConfirmSalonName) shows the success message.
                    LaunchedEffect(Unit) { viewModel.cancelCheckout() }
                }

                is CheckoutUiState.CashConfirmed -> {
                    LaunchedEffect(Unit) { Analytics.bookingCompleted(cash = true) }
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
                text  = { Text(strings.waitlistJoinedText(salonName), fontSize = 14.sp, color = TextStrong) },
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
                        color    = TextStrong
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
                text  = { Text(strings.actionFailedText, fontSize = 14.sp, color = TextStrong) },
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

        // ── Report sheet ──────────────────────────────────────────────────────
        // Hosted here rather than inside the salon sheet: a ModalBottomSheet
        // opened from inside another one is a sheet on top of a sheet, and
        // dismissing the inner one takes the outer with it.
        reporting?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.moderation.cancel() },
                containerColor   = ElegantCream
            ) {
                com.safebeauty.app.ui.components.ReportSheetContent(
                    authorId = target.authorId,
                    sending  = reportSending,
                    sent     = reportSent,
                    error    = if (reportFailed) strings.actionFailedTitle else "",
                    onSubmit = { reason, note, alsoBlock ->
                        viewModel.moderation.submit(reason, note, alsoBlock)
                    }
                )
            }
        }

        // ── Blocked accounts ──────────────────────────────────────────────────
        // A block she could make and never lift is a mis-tap that lasts
        // forever: it lives on the server, so reinstalling does not clear it.
        if (showBlocked) {
            ModalBottomSheet(
                onDismissRequest = { showBlocked = false },
                containerColor   = ElegantCream
            ) {
                com.safebeauty.app.ui.components.BlockedAccountsSheetContent(
                    blocked   = blocked,
                    onUnblock = { viewModel.moderation.unblock(it) }
                )
            }
        }

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
                            // COARSE is deliberate: this only sorts salons by
                            // distance, where city-block accuracy is ample. Asking
                            // a customer for precise location to rank a list would
                            // take more than the feature needs. (The provider side
                            // still requests FINE — pinning a salon's own map
                            // location genuinely requires it.)
                            locationPermLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
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
                    refundStatusByAppointment = refundStatusByAppointment,
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
                    onRebookClick     = { appt ->
                        // "Book again": reopen the booking flow for the same salon with
                        // the previous services pre-selected (dropping any the salon no
                        // longer offers). Splitting serviceName works for every past
                        // booking without needing the structured breakdown.
                        val salon = viewModel.findSalon(appt.salonId)
                        if (salon != null) {
                            val prev = serviceNamesFrom(appt.serviceName, salon.pricePerService)
                            showBookingsSheet = false
                            selectedServices.clear()
                            selectedServices.addAll(prev)
                            partyGuests.clear()
                            guestNameInput = ""
                            bookingIntent  = BookingIntent(salon, prev)
                            showServiceDialog = true
                        }
                    },
                    onTipClick        = { appt ->
                        showBookingsSheet = false
                        tipTarget         = appt
                    },
                    onLeaveWaitlist   = { entryId -> viewModel.leaveWaitlist(entryId) },
                    onDismissWaitlistSlot = { entryId -> viewModel.dismissWaitlistSlot(entryId) }
                )
            }
        }

        // ── Reschedule: date picker ───────────────────────────────────────────
        if (showRescheduleDate) {
            DatePickerDialog(
                onDismissRequest = {
                    showRescheduleDate = false; rescheduleTarget = null; viewModel.clearSlots()
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRescheduleDate   = false
                            val picked = rescheduleDateState.selectedDateMillis
                            reschedulePickedDate = picked
                            val target = rescheduleTarget
                            // Cleared and marked loading first: ensureSalonLoaded is
                            // asynchronous, and until it answers the dialog would
                            // otherwise render the previous flow's times as this
                            // appointment's — or, once cleared, claim the salon has no
                            // times on this day at all.
                            if (picked == null || target == null) {
                                viewModel.slotLoadFailed()
                            } else {
                                viewModel.beginSlotLoad()
                                // The salon's real free times, exactly as the booking
                                // flow computes them. A free clock face let a customer
                                // pick a minute the salon does not open on, an hour it
                                // is closed, or a slot already taken — and she found
                                // out only when the server refused, with no reason
                                // given. The same grid, the same maths, one screen
                                // later in the same journey.
                                viewModel.ensureSalonLoaded(target.salonId) { salon ->
                                    if (salon == null) {
                                        // The salon could not be read, so no day can be
                                        // judged. Said plainly, rather than spinning for
                                        // ever or claiming she is fully booked.
                                        viewModel.slotLoadFailed()
                                    } else {
                                        // The services recovered from the display name, the
                                        // same way "Book again" above does it — the stored
                                        // `services` array holds {name, price} maps, and a
                                        // Kotlin field reading it as strings crashes the
                                        // snapshot listener rather than being ignored.
                                        viewModel.loadSlotsForDate(
                                            salon, picked, target.staffId,
                                            serviceNamesFrom(target.serviceName, salon.pricePerService),
                                            excludeAppointmentId = target.id,
                                        )
                                    }
                                }
                            }
                            showRescheduleTime   = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.next, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRescheduleDate = false; rescheduleTarget = null; viewModel.clearSlots()
                    }) {
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
                // Cleared on every exit, including the back gesture. A slot list left
                // behind is a list of times computed for another salon on another day,
                // and the next reschedule would open showing them as bookable.
                onDismissRequest = {
                    showRescheduleTime = false; rescheduleTarget = null; viewModel.clearSlots()
                },
                title = { Text(strings.rescheduleTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        when {
                            viewModel.slotsLoading -> CircularProgressIndicator(
                                color = RoseGold, modifier = Modifier.align(Alignment.Center)
                            )
                            // "We could not find out" and "there is nothing left"
                            // look identical on screen and mean opposite things.
                            viewModel.slotsFailed -> Text(
                                strings.slotsLoadFailed,
                                color = DangerRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                            viewModel.noWorkingHours || viewModel.availableSlots.isEmpty() -> Text(
                                strings.noSlotsAvailable,
                                color = RoseGold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                            else -> {
                                val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(viewModel.availableSlots) { slotMs ->
                                        Button(
                                            onClick = {
                                                showRescheduleTime = false
                                                rescheduleTarget?.let {
                                                    viewModel.rescheduleAppointment(it.id, slotMs)
                                                }
                                                rescheduleTarget = null
                                                viewModel.clearSlots()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BlushPink)
                                        ) {
                                            Text(
                                                timeFmt.formatIsolated(Date(slotMs)),
                                                color = DeepRose,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        showRescheduleTime = false
                        rescheduleTarget = null
                        viewModel.clearSlots()
                    }) {
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
                onSubmit  = { rating, comment, photos ->
                    viewModel.submitReview(appt.id, appt.salonId, rating, comment, photos)
                    reviewTarget = null
                },
                onDismiss = { reviewTarget = null }
            )
        }

        // ── Tip dialog ────────────────────────────────────────────────────────
        tipTarget?.let { appt ->
            TipDialog(
                salonName = appt.salonName,
                tipState  = viewModel.tipState,
                onSend    = { amount -> viewModel.sendTip(appt.id, amount) },
                onDismiss = { tipTarget = null; viewModel.resetTip() },
                onLeavingForCheckout = { viewModel.beginExternalPayment() },
            )
        }

        // ── Salon detail sheet ────────────────────────────────────────────────
        showSalonDetail?.let { salon ->
            // Peek-then-expand sheet (Lyft/Uber-style): opens at a partial
            // "peek" height showing the salon identity + primary Book action,
            // with a drag handle to pull it up to full for the rest.
            val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { showSalonDetail = null },
                sheetState       = detailSheetState,
                containerColor   = ElegantCream
            ) {
                SalonDetailSheetContent(
                    salon            = salon,
                    reviews          = reviewsForSalon,
                    gallery          = galleryForSalon,
                    // Special offers are a verified-customer perk (see deals gate).
                    offers           = if (dealsUnlocked) offersForSalon else emptyList(),
                    isFavorite       = favoriteIds.contains(salon.id),
                    blocked          = blocked,
                    onReportReview   = { r ->
                        viewModel.moderation.start(
                            ReportTarget("REVIEW", r.id, r.customerId, "USER")
                        )
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(salon.id) },
                    onBook = {
                        if (viewModel.needsKycBeforeBooking()) {
                            showSalonDetail = null
                            showKycNotice   = true
                        } else {
                            showSalonDetail   = null
                            selectedServices.clear()
                            bookingIntent     = BookingIntent(salon, emptyList())
                            showServiceDialog = true
                        }
                    },
                    onBookPackage = { pkg ->
                        // The same gate the ordinary booking button applies. This one
                        // went straight to the date picker, so a customer who had not
                        // verified her identity could book a package — the one path
                        // that skipped the check, and the cheaper one, which is
                        // exactly the path someone avoiding it would find.
                        if (viewModel.needsKycBeforeBooking()) {
                            showSalonDetail = null
                            showKycNotice   = true
                        } else {
                            // Package services are fixed — skip service selection and go
                            // straight to date/time; the server applies the bundle discount.
                            showSalonDetail = null
                            selectedServices.clear()
                            selectedServices.addAll(pkg.services)
                            bookingIntent  = BookingIntent(salon, pkg.services, packageId = pkg.id)
                            showDatePicker = true
                        }
                    },
                    onDismiss = { showSalonDetail = null }
                )
            }
        }

        // ── Review thanks confirmation ────────────────────────────────────────
        if (viewModel.reviewFailed) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissReviewError() },
                icon  = { Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(40.dp)) },
                title = { Text(strings.reviewFailedMessage, fontSize = 14.sp, color = DeepRose, textAlign = TextAlign.Center) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissReviewError() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

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
    onSubmit: (Int, String, List<ByteArray>) -> Unit,
    onDismiss: () -> Unit
) {
    val strings    = LocalStrings.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    var rating     by remember { mutableStateOf(0) }
    var comment    by remember { mutableStateOf("") }
    // Up to 3 photos, kept as compressed JPEG bytes ready for upload.
    val photos     = remember { mutableStateListOf<ByteArray>() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || photos.size >= 3) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = ImageUtils.uriToCompressedBytes(context, uri)) {
                is ImageUtils.BytesResult.Success  -> photos.add(result.bytes)
                is ImageUtils.BytesResult.TooLarge -> { /* silently skip oversized */ }
                is ImageUtils.BytesResult.Failed   -> { /* silently skip */ }
            }
        }
    }

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
                Spacer(Modifier.height(12.dp))
                // ── Photo attachments (optional, max 3) ───────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    photos.forEachIndexed { index, bytes ->
                        val bmp = remember(bytes) {
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            if (bmp != null) {
                                Image(
                                    bitmap             = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            IconButton(
                                onClick  = { photos.removeAt(index) },
                                modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = strings.cancel,
                                    tint               = DeepRose,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (photos.size < 3) {
                        OutlinedButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            shape   = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.addPhoto, fontSize = 13.sp)
                        }
                    }
                }
                Text(
                    strings.reviewPhotosHint,
                    fontSize = 11.sp,
                    color    = RoseGold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment, photos.toList()) },
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

// ── Tip dialog ────────────────────────────────────────────────────────────────

@Composable
private fun TipDialog(
    salonName: String,
    tipState: TipUiState,
    onSend: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** Called just before the browser opens, so the idle lock defers. */
    onLeavingForCheckout: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var selected by remember { mutableStateOf(0L) }
    var customText by remember { mutableStateOf("") }
    val presets = listOf(50L, 100L, 200L, 500L)

    // Open the HesabPay checkout page as soon as the session is created.
    LaunchedEffect(tipState) {
        if (tipState is TipUiState.OpenCheckout) {
            runCatching {
                onLeavingForCheckout()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tipState.url)))
            }
        }
    }

    val amount = if (customText.isNotBlank()) customText.toLongOrNull() ?: 0L else selected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.tipTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DeepRose)
                if (salonName.isNotBlank()) Text(salonName, fontSize = 13.sp, color = RoseGold)
            }
        },
        text = {
            Column {
                Text(strings.tipHint, fontSize = 12.sp, color = RoseGold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { amt ->
                        FilterChip(
                            selected = customText.isBlank() && selected == amt,
                            onClick  = { selected = amt; customText = "" },
                            label    = { Text("$amt", fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChipActive,
                                selectedLabelColor     = Color.White,
                                containerColor         = ChipInactive,
                                labelColor             = DeepRose
                            )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value         = customText,
                    onValueChange = { customText = it.filter { c -> c.isDigit() } },
                    placeholder   = { Text(strings.tipCustomHint, fontSize = 13.sp) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    suffix        = { Text(strings.incomeAFN, fontSize = 11.sp, color = RoseGold) },
                    colors        = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = RoseGold,
                        unfocusedBorderColor = ChipInactive,
                        cursorColor          = RoseGold
                    )
                )
                Spacer(Modifier.height(8.dp))
                when (tipState) {
                    is TipUiState.Creating -> Text(strings.paymentPreparing, fontSize = 12.sp, color = RoseGold)
                    is TipUiState.Sent     -> Text(strings.tipSent, fontSize = 12.sp, color = AvailableGreen)
                    is TipUiState.Failed   -> Text(strings.tipFailed, fontSize = 12.sp, color = DangerRed)
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (tipState is TipUiState.Sent) {
                Button(
                    onClick = onDismiss,
                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) { Text(strings.ok, color = Color.White) }
            } else {
                Button(
                    onClick = { if (amount > 0) onSend(amount) },
                    enabled = amount > 0 && tipState !is TipUiState.Creating,
                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) { Text(strings.tipSend, color = Color.White) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = RoseGold) }
        },
        containerColor = ElegantCream
    )
}

// ── Booking progress stepper ────────────────────────────────────────────────

/**
 * Compact progress header for the multi-step booking journey
 * (Services → Time → Confirm). [current] is 1-based; every segment up to and
 * including it fills in, and the current step's label is emphasised.
 */
@Composable
private fun BookingStepper(current: Int, labels: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            labels.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i + 1 <= current) RoseGold else BlushPink)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    fontSize   = 10.sp,
                    color      = if (i + 1 == current) DeepRose else TextFaint,
                    fontWeight = if (i + 1 == current) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Salon card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalonCard(
    salon: SalonDocument,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBook: () -> Unit,
    modifier: Modifier = Modifier,
    distanceKm: Double? = null,
    hasOffer: Boolean = false
) {
    val strings  = LocalStrings.current
    val context  = LocalContext.current
    val haptic   = LocalHapticFeedback.current
    val gradient = remember(salon.salonName) { avatarGradient(salon.salonName) }

    ElevatedCard(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier  = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Photo-first hero: the provider's chosen cover leads the card. The
            // gradient sits behind the image, so an empty or broken cover URL
            // degrades to the monogram gradient instead of a blank band.
            if (salon.coverImageUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(gradient.first, gradient.second)))
                ) {
                    // Blurred crop fills the band edge-to-edge; the sharp Fit image
                    // on top shows the WHOLE photo instead of center-cropping it. On
                    // API < 31 blur is a no-op — the crop fill just shows unblurred.
                    AsyncImage(
                        model              = ImageRequest.Builder(context)
                            .data(salon.coverImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize().blur(18.dp)
                    )
                    AsyncImage(
                        model              = ImageRequest.Builder(context)
                            .data(salon.coverImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0x55000000))
                                )
                            )
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.Top) {
                if (salon.coverImageUrl.isBlank()) {
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
                            text       = salon.salonName.firstOrNull()?.toString() ?: "?",
                            fontSize   = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
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
                            text     = com.safebeauty.app.util.Areas.labelForKey(salon.district, strings.language),
                            fontSize = 12.sp,
                            color    = TextMuted,
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
                    if (cardBadge != SalonBadge.NONE || hasOffer) {
                        Spacer(Modifier.height(5.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            if (cardBadge != SalonBadge.NONE) SalonBadgeChip(badge = cardBadge)
                            if (hasOffer) OfferChip()
                        }
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
                    Text(ratingLabel(salon.rating, strings), fontSize = 12.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
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
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBook()
                                    }
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
    val strings = LocalStrings.current
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
                .background(ElegantCream)
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
                            text     = dateFmt.formatIsolated(Date(newest.createdAt)),
                            fontSize = 11.sp,
                            color    = RoseGold
                        )
                    }
                    // A real button, not decoration. This was a plain Icon, and
                    // the only way to dismiss the banner was to swipe it — so a
                    // customer saw a close cross, tapped it, and nothing
                    // happened. An × that does not close is worse than no ×.
                    IconButton(
                        onClick = {
                            AnnouncementPrefs.dismiss(context, newest.id)
                            dismissed = dismissed + newest.id
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = RoseGold,
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
        colors    = CardDefaults.cardColors(containerColor = DashboardSurface),
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
                IconButton(onClick = onToggleFav, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = strings.favorites,
                        tint     = if (isFavorite) DeepRose else ChipInactive,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = RoseGold, modifier = Modifier.size(11.dp))
                Text(com.safebeauty.app.util.Areas.labelForKey(salon.district, strings.language), fontSize = 10.sp, color = UnavailableGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (salon.rating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(11.dp))
                    Text(ratingLabel(salon.rating, strings), fontSize = 10.sp, color = UnavailableGrey)
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
private fun SalonEmptyState(
    favoritesOnly: Boolean = false,
    modifier: Modifier = Modifier,
    // Non-null when something the user chose is narrowing the list. Without a way
    // back, an over-filtered search is a dead end: the customer has to work out on
    // their own which of category, neighbourhood, search or favourites emptied it.
    onClearFilters: (() -> Unit)? = null,
) {
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
                color     = TextFaint,
                textAlign = TextAlign.Center
            )
            if (onClearFilters != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onClearFilters,
                    shape   = RoundedCornerShape(14.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) {
                    Text(strings.showAllSalons, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
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
        SalonBadge.VERIFIED -> Pair(strings.badgeVerified, AvailableGreen)
        SalonBadge.GOLD     -> Pair(strings.badgeGold,     WarmGold)
        SalonBadge.SILVER   -> Pair(strings.badgeSilver,   UnavailableGrey)
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

/** Small "🔥 آفر" chip shown on a salon card that has a live offer. */
@Composable
private fun OfferChip(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DeepRose)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Icon(Icons.Default.LocalOffer, null, tint = Color.White, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(strings.offerBadge, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/**
 * Horizontal strip of live deals across all salons, shown above the salon list.
 * Tapping a deal opens that salon's detail sheet.
 */
@Composable
private fun DealsStrip(
    offers: List<OfferDocument>,
    onOpen: (String) -> Unit
) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Default.LocalOffer, null, tint = DeepRose, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.dealsTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(offers, key = { it.id }) { offer ->
                ElevatedCard(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    modifier  = Modifier
                        .width(230.dp)
                        .clickable { onOpen(offer.salonId) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OfferChip()
                            if (offer.discountPercent > 0) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${offer.discountPercent}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 13.sp,
                                    color      = DeepRose
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            offer.title,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = DeepRose,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (offer.salonName.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                offer.salonName,
                                fontSize = 12.sp,
                                color    = RoseGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown in place of [DealsStrip] for customers who haven't verified their identity.
 * It reveals that live deals exist (the count) but keeps them locked behind KYC —
 * the whole card taps through to the verification screen. Purely a UI nudge; the
 * server already gates booking on verification regardless.
 */
@Composable
private fun LockedDealsTeaser(count: Int, onUnlock: () -> Unit) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Default.LocalOffer, null, tint = DeepRose, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.dealsTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
        }
        ElevatedCard(
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier  = Modifier
                .fillMaxWidth()
                .clickable { onUnlock() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DeepRose.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = DeepRose, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        strings.dealsLockedTitle(count),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = DeepRose
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        strings.dealsLockedBody,
                        fontSize   = 12.sp,
                        color      = TextMuted,
                        lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(RoseGold)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(strings.dealsLockedCta, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
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

/**
 * Colour-family picker.
 *
 * Each option is previewed with a swatch drawn from that family's own palette
 * rather than the one currently applied — the whole decision is "which of these
 * do I like", so the choices have to look like themselves while you choose.
 */
@Composable
fun ThemePickerDialog(
    current: AppBrand,
    onPick: (AppBrand) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val isDark  = LocalPalette.current.isDark
    val label: (AppBrand) -> String = { brand ->
        when (brand) {
            AppBrand.ROSE     -> strings.themeRose
            AppBrand.LAVENDER -> strings.themeLavender
            AppBrand.SAGE     -> strings.themeSage
            AppBrand.OCEAN    -> strings.themeOcean
            AppBrand.HONEY    -> strings.themeHoney
            AppBrand.MAROON   -> strings.themeMaroon
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(strings.themePickerTitle, fontWeight = FontWeight.Bold, color = DeepRose, fontSize = 15.sp)
        },
        text = {
            // Two columns of swatches rather than a stack of full-width rows.
            // Six families made the stacked list taller than the dialog, and the
            // choice is made by looking at colour, not by reading names — so the
            // colours should all be visible at once, without scrolling.
            //
            // Built from Rows rather than LazyVerticalGrid: an AlertDialog measures
            // its content with an unbounded height, which a lazy grid cannot handle.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppBrand.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { brand ->
                            // Preview in the mode the user is actually in, so the
                            // swatch matches what applying it will look like.
                            val preview  = paletteFor(brand, isDark)
                            val selected = brand == current
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) preview.blushPink else preview.dashboardSurface)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) preview.roseGold else preview.cardBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onPick(brand) }
                                    .padding(vertical = 14.dp, horizontal = 8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(preview.brandRose))
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Default.CheckCircle, null,
                                            tint = preview.onPrimaryWhite,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    text       = label(brand),
                                    fontSize   = 13.sp,
                                    color      = preview.deepRose,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // An odd number of families must not stretch the last one
                        // across the full width.
                        repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text(strings.cancel, color = RoseGold) } },
        containerColor = ElegantCream
    )
}
