package com.safebeauty.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.ui.theme.TextFaint
import com.safebeauty.app.ui.theme.DangerRed
import com.safebeauty.app.ui.theme.WarningOrange
import androidx.compose.material.icons.filled.Palette
import com.safebeauty.app.viewmodel.ThemeViewModel
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

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDashboardScreen(
    onSignOut: () -> Unit,
    onNavigate: (String) -> Unit         = {},
    viewModel: ProviderViewModel         = hiltViewModel(),
    langVm: LanguageViewModel            = hiltViewModel(),
    notifVm: NotificationCenterViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.signOutTriggered) {
        if (viewModel.signOutTriggered) {
            viewModel.resetSignOut()
            onSignOut()
        }
    }

    // Providers rely on new-booking pushes most — prompt for notifications too.
    com.safebeauty.app.ui.components.RequestNotificationPermission()

    val strings             = LocalStrings.current
    val currentLanguage     by langVm.language.collectAsStateWithLifecycle()
    val salon               by viewModel.salon.collectAsStateWithLifecycle()
    val isAvailable         by viewModel.isAvailable.collectAsStateWithLifecycle()
    val pendingAppointments by viewModel.pendingAppointments.collectAsStateWithLifecycle()
    val monthAppointments   by viewModel.monthAppointments.collectAsStateWithLifecycle()
    val analytics           by viewModel.analytics.collectAsStateWithLifecycle()
    val broadcasts          by viewModel.broadcasts.collectAsStateWithLifecycle()
    val reviews             by viewModel.reviews.collectAsStateWithLifecycle()
    var selectedTab         by remember { mutableIntStateOf(0) }
    var showLangPicker      by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { onNavigate(Screen.Support.route) }) {
                            Icon(Icons.Default.SupportAgent, contentDescription = strings.supportTitle, tint = RoseGold)
                        }
                        IconButton(onClick = { showThemePicker = true }) {
                            Icon(Icons.Default.Palette, contentDescription = strings.themePickerTitle, tint = RoseGold)
                        }
                        IconButton(onClick = { showLangPicker = true }) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = RoseGold)
                        }
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut, tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Gradients.ScreenBg)
                    .padding(padding)
            ) {

                // ── "You're hidden" call-to-action ────────────────────────
                // A newly-approved salon defaults to isAvailable=false and is
                // invisible to customers until the provider goes live — without
                // this prompt that state is silent and confusing.
                if (!isAvailable) {
                    ProviderHiddenBanner(onGoLive = { viewModel.toggleAvailability() })
                }

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
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Color.Transparent,
                    contentColor     = DeepRose,
                    edgePadding      = 12.dp,
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
                        onRate       = { viewModel.openRatingDialog(it) },
                        onSupport    = { appt ->
                            viewModel.contactSupport(appt)
                            onNavigate(
                                Screen.Chat.build(
                                    conversationId = "support_${viewModel.providerId}",
                                    myUserId       = viewModel.providerId,
                                    myName         = salon?.salonName ?: salon?.providerName ?: "",
                                    otherName      = strings.supportTitle,
                                    active         = true
                                )
                            )
                        },
                        onNavigate   = onNavigate
                    )
                    1 -> ProfileTab(viewModel = viewModel)
                    2 -> AnalyticsTab(analytics = analytics)
                    3 -> IncomeTab(viewModel = viewModel)
                    4 -> CalendarTab(
                        monthAppointments = monthAppointments,
                        onMonthShown      = viewModel::showCalendarMonth,
                    )
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
                text  = { Text(strings.profileSavedText, fontSize = 14.sp, color = TextStrong) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissSaveSuccess() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── Save error dialog ─────────────────────────────────────────────
        if (viewModel.showSaveError) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.dismissSaveError() },
                title = { Text(strings.actionFailedTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                text  = { Text(strings.actionFailedText, fontSize = 14.sp, color = TextStrong) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissSaveError() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── Rate-customer dialog ──────────────────────────────────────────
        viewModel.ratingTarget?.let { target ->
            RateCustomerDialog(
                appointment  = target,
                isSubmitting = viewModel.isSubmittingReport,
                onSubmit     = { rating, noShow, flagged, comment ->
                    viewModel.submitCustomerReport(target.id, rating, noShow, flagged, comment)
                },
                onDismiss    = { viewModel.dismissRatingDialog() }
            )
        }

        // ── Feedback-submitted confirmation ───────────────────────────────
        if (viewModel.showReportDone) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.dismissReportDone() },
                icon = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = AvailableGreen,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(strings.rateCustomerDone, fontWeight = FontWeight.Bold, color = DeepRose) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissReportDone() },
                        colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White) }
                },
                containerColor = ElegantCream
            )
        }

        // ── Admin announcement popup (one-time per broadcast) ─────────────────
        com.safebeauty.app.ui.components.AnnouncementPopup(broadcasts)

        if (showThemePicker) {
            val themeVm: ThemeViewModel = hiltViewModel()
            val brand by themeVm.brand.collectAsStateWithLifecycle()
            ThemePickerDialog(
                current   = brand,
                onPick    = { themeVm.setBrand(it); showThemePicker = false },
                onDismiss = { showThemePicker = false }
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

/**
 * Dialog where the provider rates the customer after a confirmed booking: a
 * 1–5 star tap-rating, an optional no-show toggle, an optional "report to
 * admin" toggle for misconduct, and a free-text comment. At least one signal
 * (rating, no-show, or flag) is required before submit is enabled.
 */
@Composable
private fun RateCustomerDialog(
    appointment: AppointmentDocument,
    isSubmitting: Boolean,
    onSubmit: (rating: Int, noShow: Boolean, flagged: Boolean, comment: String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var rating  by remember { mutableIntStateOf(0) }
    var noShow  by remember { mutableStateOf(false) }
    var flagged by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    val canSubmit = (rating > 0 || noShow || flagged) && !isSubmitting

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Column {
                Text(strings.rateCustomerTitle, fontWeight = FontWeight.Bold, color = DeepRose, fontSize = 17.sp)
                Text(appointment.customerName, fontSize = 13.sp, color = RoseGold)
            }
        },
        text = {
            Column {
                // Star rating row
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick  = { rating = star },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star",
                                tint     = if (star <= rating) WarmGold else TextFaint,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = noShow,
                        onCheckedChange = { noShow = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = DangerRed)
                    )
                    Text(strings.rateCustomerNoShow, fontSize = 14.sp, color = TextStrong)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = flagged,
                        onCheckedChange = { flagged = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = DangerRed)
                    )
                    Text(strings.rateCustomerFlag, fontSize = 14.sp, color = TextStrong)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500) },
                    label = { Text(strings.rateCustomerComment) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, noShow, flagged, comment.trim()) },
                enabled = canSubmit,
                colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(strings.rateCustomerSubmit, color = Color.White)
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) { Text(strings.cancel, color = RoseGold) }
        },
        containerColor = ElegantCream
    )
}

// ── Broadcast banner ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderBroadcastBanner(broadcasts: List<BroadcastDocument>) {
    val context = LocalContext.current
    val dateFmt = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }
    // Only the newest un-dismissed announcement, swipeable away (and remembered).
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
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text     = dateFmt.format(java.util.Date(newest.createdAt)),
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

// ── Availability toggle card ───────────────────────────────────────────────────

@Composable
private fun ProviderHiddenBanner(onGoLive: () -> Unit) {
    val strings = LocalStrings.current
    ElevatedCard(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = WarningOrange.copy(alpha = 0.12f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint     = WarningOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.providerHiddenTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = WarningOrange
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                strings.providerHiddenBody,
                fontSize = 13.sp,
                color    = WarningOrange,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = onGoLive,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AvailableGreen)
            ) {
                Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.providerHiddenCta, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

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
internal fun ProviderStatusBadge(status: String) {
    val strings = LocalStrings.current
    val (bg, fg) = when (status.uppercase()) {
        "CONFIRMED" -> Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
        "CANCELLED" -> Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
        else        -> Pair(WarmGold.copy(alpha = 0.15f), WarmGold)
    }
    val label = when (status.uppercase()) {
        "CONFIRMED" -> strings.analyticsConfirmed
        "CANCELLED" -> strings.analyticsCancelled
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

internal fun Int.format(): String {
    return "%,d".format(this)
}

internal fun Long.format(): String {
    return "%,d".format(this)
}


// ── Provider Calendar Tab ─────────────────────────────────────────────────────
