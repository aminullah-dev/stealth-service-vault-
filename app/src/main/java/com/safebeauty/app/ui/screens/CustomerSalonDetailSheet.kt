package com.safebeauty.app.ui.screens

// Salon-detail bottom sheet + its review card, split out of CustomerDashboardScreen.kt
// to keep that file manageable. Same package; SalonDetailSheetContent is internal so the
// main screen opens it. SalonBadgeChip and avatarGradient stay in main (shared).

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
import androidx.compose.material.icons.filled.LocalOffer
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
import com.safebeauty.app.data.firebase.OfferDocument
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SalonDetailSheetContent(
    salon: SalonDocument,
    reviews: List<ReviewDocument>,
    gallery: List<GalleryImageDocument>,
    offers: List<OfferDocument> = emptyList(),
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBook: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings  = LocalStrings.current
    val gradient = remember(salon.salonName) { avatarGradient(salon.salonName) }

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
                    .background(Brush.linearGradient(listOf(gradient.first, gradient.second)))
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

        // ── Our team (stylists + their portfolios) ────────────────────
        val team = salon.activeStaff()
        if (team.isNotEmpty()) {
            Text(strings.teamTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                team.forEach { member ->
                    Column {
                        Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DeepRose)
                        if (member.specialty.isNotBlank()) {
                            Text(member.specialty, fontSize = 11.sp, color = RoseGold)
                        }
                        if (member.photoUrls.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(member.photoUrls) { url ->
                                    AsyncImage(
                                        model              = url,
                                        contentDescription = null,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(BlushPink)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Offers / deals (informational — do not change checkout price) ──
        if (offers.isNotEmpty()) {
            Text(strings.offersTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offers.forEach { offer ->
                    val badge = when {
                        offer.discountPercent > 0 -> "%d%%".format(offer.discountPercent)
                        offer.discountAmount  > 0 -> "%,d AFN".format(offer.discountAmount)
                        else                      -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Gradients.BrandRose)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocalOffer, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(offer.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (offer.description.isNotBlank()) {
                                Text(offer.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                        if (badge.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(
                    if (salon.isAvailable)
                        Modifier
                            .shadow(6.dp, RoundedCornerShape(16.dp), clip = false)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Gradients.BrandRose)
                            .clickable(onClick = onBook)
                    else
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(UnavailableGrey.copy(alpha = 0.22f))
                )
        ) {
            Text(
                strings.book,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (salon.isAvailable) Color.White else UnavailableGrey
            )
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
            if (review.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(review.imageUrls) { url ->
                        AsyncImage(
                            model              = url,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
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

