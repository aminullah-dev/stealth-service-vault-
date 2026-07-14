package com.safebeauty.app.ui.screens

// Provider booking-requests tab (incoming requests, cards, reputation, swipe),
// split out of ProviderDashboardScreen.kt. Same package; BookingRequestsTab is
// internal. ProviderStatusBadge stays in the main file (shared with the calendar tab).

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
internal fun BookingRequestsTab(
    appointments: List<AppointmentDocument>,
    salonId: String,
    providerName: String,
    providerId: String,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRate: (AppointmentDocument) -> Unit,
    onSupport: (AppointmentDocument) -> Unit,
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
                val card: @Composable () -> Unit = {
                    BookingRequestCard(
                        appointment = appt,
                        onAccept    = { onAccept(appt.id) },
                        onDecline   = { onDecline(appt.id) },
                        onRate      = { onRate(appt) },
                        onSupport   = { onSupport(appt) },
                        onChat      = {
                            if (salonId.isNotBlank()) {
                                onNavigate(
                                    Screen.Chat.build(
                                        conversationId = "${appt.customerId}_$salonId",
                                        myUserId       = providerId,
                                        myName         = providerName,
                                        otherName      = appt.customerName,
                                        active         = appt.status == "PENDING" || appt.status == "CONFIRMED"
                                    )
                                )
                            }
                        }
                    )
                }
                // A pending request can be actioned by swiping — right to accept,
                // left to decline — as a faster alternative to the buttons.
                if (appt.status == "PENDING") {
                    SwipeableRequestCard(
                        onAccept  = { onAccept(appt.id) },
                        onDecline = { onDecline(appt.id) },
                        content   = card
                    )
                } else {
                    card()
                }
            }
        }
    }
}

@Composable
private fun BookingRequestCard(
    appointment: AppointmentDocument,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onChat: () -> Unit = {},
    onRate: () -> Unit = {},
    onSupport: () -> Unit = {}
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text     = appointment.serviceName,
                                fontSize = 13.sp,
                                color    = RoseGold
                            )
                            if (appointment.paymentMethod == "CASH") {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFB00020).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text       = strings.paymentMethodCash,
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = Color(0xFFB00020)
                                    )
                                }
                            }
                        }
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
                        CustomerReputationBadge(appointment)
                        if (appointment.staffName.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint     = RoseGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text       = appointment.staffName,
                                    fontSize   = 12.sp,
                                    color      = DeepRose,
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
                                contentDescription = strings.a11yCall,
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
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint     = RoseGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick  = onSupport,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SupportAgent,
                            contentDescription = strings.contactSupport,
                            tint     = Color(0xFF888888),
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
                } else if (appointment.status == "CONFIRMED" && !appointment.customerReported) {
                    // Once a booking is confirmed the provider can leave feedback
                    // about the customer (rating / no-show / misconduct report).
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick        = onRate,
                        modifier       = Modifier.fillMaxWidth(),
                        shape          = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.rateCustomer, color = DeepRose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Small pill row showing what the platform knows about this customer's
 * reputation — their average rating from other providers and any no-shows —
 * so a provider can gauge a booking before confirming. Nothing shows for a
 * brand-new customer beyond a neutral "new" tag.
 */
@Composable
private fun CustomerReputationBadge(appointment: AppointmentDocument) {
    val strings = LocalStrings.current
    val rating  = appointment.customerRating()
    val noShows = appointment.noShowCount
    if (rating <= 0.0 && noShows <= 0) {
        Spacer(Modifier.height(3.dp))
        Text(
            text     = strings.customerNewBadge,
            fontSize = 11.sp,
            color    = Color(0xFF9E9E9E),
            fontWeight = FontWeight.Medium
        )
        return
    }
    Spacer(Modifier.height(3.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rating > 0.0) {
            Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                text     = String.format(Locale.getDefault(), "%.1f", rating),
                fontSize = 12.sp,
                color    = DeepRose,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (noShows > 0) {
            if (rating > 0.0) Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFB00020).copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = strings.customerNoShowBadge(noShows),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFFB00020)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRequestCard(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onAccept();  true }
                SwipeToDismissBoxValue.EndToStart -> { onDecline(); true }
                else -> false
            }
        },
        // Require a deliberate ~35% drag so a stray scroll can't action a booking.
        positionalThreshold = { total -> total * 0.35f }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val accepting = dir == SwipeToDismissBoxValue.StartToEnd
            val bg by animateColorAsState(
                targetValue = when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> AvailableGreen
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFD32F2F)
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (accepting) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        if (accepting) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        content = { content() }
    )
}
