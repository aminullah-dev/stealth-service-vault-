package com.safebeauty.app.ui.screens

// Bookings bottom sheet (list + cards + status timeline + waitlist), split out
// of CustomerDashboardScreen.kt. Same package; BookingsSheetContent is internal so
// the main screen opens it. Everything else stays private to this file.

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material.icons.filled.Replay
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
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextFaint
import com.safebeauty.app.ui.theme.DangerRed
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookingsSheetContent(
    appointments: List<AppointmentDocument>,
    waitlistEntries: List<WaitlistEntry> = emptyList(),
    refundStatusByAppointment: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onChatClick: (AppointmentDocument) -> Unit = {},
    onCancelClick: (AppointmentDocument) -> Unit = {},
    onRescheduleClick: (AppointmentDocument) -> Unit = {},
    onReviewClick: (AppointmentDocument) -> Unit = {},
    onSupportClick: (AppointmentDocument) -> Unit = {},
    onRebookClick: (AppointmentDocument) -> Unit = {},
    onTipClick: (AppointmentDocument) -> Unit = {},
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
                    Text(strings.noBookingsSubtext, fontSize = 13.sp, color = TextFaint, textAlign = TextAlign.Center)
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
                upcoming.forEach { appt ->
                    SwipeToCancel(appt, onRequestCancel = { cancelTarget = appt }) {
                        BookingCard(appt, dateFmt, onChatClick, onRescheduleClick, onReviewClick, { cancelTarget = appt }, onSupportClick, onRebookClick, onTipClick, refundStatusByAppointment[appt.id])
                    }
                }
            }

            if (past.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(UnavailableGrey)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.bookingsPast, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMuted)
                }
                // Past bookings are not cancellable, so the wrapper renders them
                // untouched — the gesture simply is not there rather than being
                // there and doing nothing.
                past.forEach { appt ->
                    SwipeToCancel(appt, onRequestCancel = { cancelTarget = appt }) {
                        BookingCard(appt, dateFmt, onChatClick, onRescheduleClick, onReviewClick, { cancelTarget = appt }, onSupportClick, onRebookClick, onTipClick, refundStatusByAppointment[appt.id])
                    }
                }
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
            text  = { Text(strings.cancelConfirmText, fontSize = 14.sp, color = TextStrong) },
            confirmButton = {
                Button(
                    onClick = { onCancelClick(appt); cancelTarget = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = DangerRed)
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

/**
 * PENDING (awaiting the salon) is always cancellable. CONFIRMED (paid and
 * accepted) stays cancellable until the appointment time — cancelAppointment
 * flags the payment for a manual refund either way.
 *
 * One definition, because the card's button and the swipe gesture must agree:
 * a row that can be swiped but shows no button, or the reverse, is a bug the
 * customer discovers by trying.
 */
private fun AppointmentDocument.isCancellable(): Boolean =
    status == "PENDING" ||
        (status == "CONFIRMED" && appointmentDate > System.currentTimeMillis())

/**
 * Swipe a booking toward the end of the row to cancel it.
 *
 * The gesture opens the confirmation; it never cancels on its own. Cancelling a
 * paid booking moves money — it flags a refund a person then has to settle — and
 * a payment should not turn on whether a thumb slipped. confirmValueChange
 * returns false for exactly that reason: the row springs back, and the dialog
 * that was always there is what actually decides.
 *
 * Bookings that cannot be cancelled do not swipe at all, rather than swiping to
 * nothing, which reads as the app having ignored you.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToCancel(
    appt: AppointmentDocument,
    onRequestCancel: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!appt.isCancellable()) { content(); return }

    val strings = LocalStrings.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRequestCancel()
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val active = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(
                targetValue = if (active) DangerRed else Color.Transparent,
                label = "cancel_bg"
            )
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (active) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White,
                             modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.cancelAppointment, color = Color.White,
                             fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingCard(
    appt: AppointmentDocument,
    dateFmt: SimpleDateFormat,
    onChatClick: (AppointmentDocument) -> Unit,
    onRescheduleClick: (AppointmentDocument) -> Unit,
    onReviewClick: (AppointmentDocument) -> Unit,
    onCancelClick: () -> Unit,
    onSupportClick: (AppointmentDocument) -> Unit,
    onRebookClick: (AppointmentDocument) -> Unit = {},
    onTipClick: (AppointmentDocument) -> Unit = {},
    refundStatus: String? = null
) {
    val strings       = LocalStrings.current
    val canReschedule = appt.status == "PENDING" || appt.status == "CONFIRMED"
    val canReview     = appt.status == "CONFIRMED" || appt.status == "COMPLETED"
    // "Book again" makes sense once a visit is done or was cancelled — not while a
    // payment is still pending.
    val canRebook     = appt.status == "CONFIRMED" || appt.status == "COMPLETED" ||
                        appt.status == "CANCELLED" || appt.status == "DECLINED"
    // PENDING (awaiting provider confirmation) is always cancellable. CONFIRMED
    // (paid + accepted) can still be cancelled up until the appointment time —
    // cancelAppointment() flags the payment for a manual refund either way.
    val canCancel     = appt.isCancellable()

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
                        color    = TextFaint
                    )
                    // The reference support will ask for. Blank on bookings made
                    // before codes existed, so it is shown only when there is one.
                    if (appt.bookingCode.isNotBlank()) {
                        Text(
                            appt.bookingCode,
                            fontSize   = 11.sp,
                            color      = RoseGold,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick  = { onChatClick(appt) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = strings.chat, tint = RoseGold, modifier = Modifier.size(20.dp))
                }
                StatusChip(appt.status)
            }

            // ── Order-tracking style status timeline ──────────────────────────
            BookingStatusTimeline(appt)

            // ── Refund status (only for a cancelled booking that was paid) ─────
            if (appt.status == "CANCELLED" && refundStatus != null) {
                val processed = refundStatus == "PROCESSED"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(
                        if (processed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (processed) AvailableGreen else WarmGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (processed) strings.refundProcessed else strings.refundPending,
                        fontSize   = 12.sp,
                        color      = if (processed) AvailableGreen else WarmGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            run {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Support is always available for a booking, active or past.
                    OutlinedButton(
                        onClick        = { onSupportClick(appt) },
                        shape          = RoundedCornerShape(8.dp),
                        border         = androidx.compose.foundation.BorderStroke(1.dp, ChipInactive),
                        colors         = ButtonDefaults.outlinedButtonColors(contentColor = RoseGold),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.contactSupport, fontSize = 12.sp)
                    }
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
                    if (canReview) {
                        OutlinedButton(
                            onClick        = { onTipClick(appt) },
                            shape          = RoundedCornerShape(8.dp),
                            border         = androidx.compose.foundation.BorderStroke(1.dp, DeepRose),
                            colors         = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Favorite, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.tipTitle, fontSize = 12.sp)
                        }
                    }
                    if (canRebook) {
                        OutlinedButton(
                            onClick        = { onRebookClick(appt) },
                            shape          = RoundedCornerShape(8.dp),
                            border         = androidx.compose.foundation.BorderStroke(1.dp, DeepRose),
                            colors         = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Replay, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.bookAgain, fontSize = 12.sp)
                        }
                    }
                    if (canCancel) {
                        OutlinedButton(
                            onClick        = onCancelClick,
                            shape          = RoundedCornerShape(8.dp),
                            border         = androidx.compose.foundation.BorderStroke(1.dp, DangerRed),
                            colors         = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
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

/**
 * Bottom-sheet panel for narrowing and ordering the salon list: sort mode
 * (recommended / nearest / top-rated / cheapest), a minimum star rating, and a
 * maximum starting price. All applied live to [DashboardViewModel.displayedSalons].
 */
@Composable
private fun BookingStatusTimeline(appt: AppointmentDocument) {
    val strings = LocalStrings.current
    if (appt.status == "CANCELLED") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Icon(Icons.Default.Cancel, null, tint = DangerRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.timelineCancelled, fontSize = 12.sp, color = DangerRed, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    val now = System.currentTimeMillis()
    val reached = when {
        appt.status == "COMPLETED"                                -> 2
        appt.status == "CONFIRMED" && appt.appointmentDate <= now -> 2
        appt.status == "CONFIRMED"                                -> 1
        else                                                      -> 0   // PENDING
    }
    val labels = listOf(strings.timelineRequested, strings.timelineConfirmed, strings.timelineCompleted)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            for (i in 0..2) {
                val done = i <= reached
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (done) AvailableGreen else ChipInactive)
                ) {
                    if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                if (i < 2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (i < reached) AvailableGreen else ChipInactive)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    fontSize   = 10.sp,
                    color      = if (i <= reached) DeepRose else TextFaint,
                    fontWeight = if (i == reached) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val strings = LocalStrings.current
    val (bg, fg) = when (status) {
        "CONFIRMED", "COMPLETED" -> Pair(AvailableGreen.copy(alpha = 0.15f), AvailableGreen)
        "CANCELLED", "DECLINED"  -> Pair(UnavailableGrey.copy(alpha = 0.15f), UnavailableGrey)
        else                     -> Pair(WarmGold.copy(alpha = 0.15f), WarmGold)
    }
    val label = when (status) {
        "CONFIRMED" -> strings.analyticsConfirmed
        "COMPLETED" -> strings.timelineCompleted
        "CANCELLED", "DECLINED" -> strings.analyticsCancelled
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
                    fontSize = 11.sp, color = TextFaint
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
