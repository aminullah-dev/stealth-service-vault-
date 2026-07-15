package com.safebeauty.app.ui.screens

// Provider calendar + reviews tabs, split out of ProviderDashboardScreen.kt.
// Same package; CalendarTab and ReviewsTab are internal. ProviderStatusBadge
// stays in the main file (shared).

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
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextFaint
import com.safebeauty.app.ui.theme.RosePetal
import com.safebeauty.app.ui.theme.PetalPink
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

@Composable
internal fun CalendarTab(allAppointments: List<AppointmentDocument>) {
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = RoseGold)
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = RoseGold)
                    }
                }

                // Day-of-week header row
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { d ->
                        Text(
                            text      = d,
                            fontSize  = 11.sp,
                            color     = TextMuted,
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
                                            else       -> TextStrong
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
                    Text(strings.calendarTapDay, fontSize = 14.sp, color = TextFaint, textAlign = TextAlign.Center)
                }
            }
            selectedDayAppts.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Text(strings.calendarNoAppointments, fontSize = 14.sp, color = TextFaint, textAlign = TextAlign.Center)
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
                    Text(appt.customerPhone, fontSize = 11.sp, color = TextMuted)
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
internal fun ReviewsTab(
    reviews: List<ReviewDocument>,
    onReply: (String, String) -> Unit
) {
    val strings = LocalStrings.current
    var expandedReviewId by remember { mutableStateOf<String?>(null) }

    if (reviews.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.noReviewsYet, color = TextMuted, fontSize = 14.sp)
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
                colors   = CardDefaults.cardColors(containerColor = PetalPink)
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
                                        trackColor = BlushPink
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("$count", fontSize = 11.sp, color = TextMuted,
                                        modifier = Modifier.width(20.dp))
                                }
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
                    Text(
                        "${reviews.size} ${strings.reviews}",
                        fontSize = 12.sp, color = TextMuted,
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
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
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
                    Text(dateStr, fontSize = 11.sp, color = TextMuted)
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
                Text(review.comment, fontSize = 13.sp, color = TextStrong)
            }
            // Existing reply (collapsed view)
            if (review.providerReply.isNotBlank() && !isExpanded) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 3.dp,
                            color = RosePetal,
                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 8.dp,
                                topEnd = 8.dp, bottomEnd = 8.dp)
                        )
                        .background(PetalPink, RoundedCornerShape(topStart = 0.dp,
                            bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                        .padding(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.providerReplied, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = RoseGold)
                        Spacer(Modifier.height(2.dp))
                        Text(review.providerReply, fontSize = 12.sp, color = TextStrong)
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
                        Text(strings.cancel, color = TextMuted)
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
