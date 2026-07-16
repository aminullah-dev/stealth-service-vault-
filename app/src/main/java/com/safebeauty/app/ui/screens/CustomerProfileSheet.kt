package com.safebeauty.app.ui.screens

// Customer profile bottom sheet + its cards (referral, loyalty, change-PIN), split
// out of CustomerDashboardScreen.kt. Same package; the pieces the main screen calls
// are internal/public. ProfileInitialsAvatar and ChangePinField stay private here.

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
import androidx.compose.material.icons.filled.Redeem
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

@Composable
internal fun CustomerProfileSheetContent(
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
                            color    = TextFaint
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
                            text       = if (appt.status == "CONFIRMED") strings.analyticsConfirmed else strings.analyticsCancelled,
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
    val initial  = if (name.isNotBlank()) name.first().toString().uppercase() else "?"
    val gradient = remember(name) {
        if (name.isNotBlank()) avatarGradient(name) else avatarGradients.first()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second)))
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


// ── Waitlist card ──────────────────────────────────────────────────────────────

@Composable
internal fun ReferralCard(code: String, credit: Long, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    if (code.isBlank()) return

    Card(
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = DashboardSurface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, null, tint = RoseGold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(strings.referralCardTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose, modifier = Modifier.weight(1f))
                if (credit > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AvailableGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(strings.referralCreditBadge(credit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AvailableGreen)
                    }
                }
            }
            Text(strings.referralCardBody, fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BlushPink.copy(alpha = 0.4f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.referralYourCode, fontSize = 11.sp, color = RoseGold)
                    Text(code, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DeepRose, letterSpacing = 2.sp)
                }
                Button(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, strings.referralShareText(code))
                        }
                        runCatching { context.startActivity(Intent.createChooser(share, null)) }
                    },
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoseGold),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.referralShare, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Customer Loyalty card ─────────────────────────────────────────────────────

@Composable
internal fun LoyaltyCard(points: Int, tier: LoyaltyTier, modifier: Modifier = Modifier, onRedeem: () -> Unit = {}) {
    val strings = LocalStrings.current

    val (tierLabel, tierColor, nextTarget) = when (tier) {
        LoyaltyTier.NEWCOMER -> Triple(strings.loyaltyTierNewcomer, Color(0xFFCD7F32), 50)
        LoyaltyTier.REGULAR  -> Triple(strings.loyaltyTierRegular,  UnavailableGrey, 150)
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

            // Redeem points → wallet credit, once there are at least 100.
            if (points >= 100) {
                OutlinedButton(
                    onClick  = onRedeem,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, tierColor),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = tierColor)
                ) {
                    Icon(Icons.Default.Redeem, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.redeemPoints, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
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
                onValueChange = { changePinVm.currentPin = it },
                label         = strings.changePinCurrentPin,
                fieldColors   = fieldColors
            )
            ChangePinField(
                value         = changePinVm.newPin,
                onValueChange = { changePinVm.newPin = it },
                label         = strings.changePinNewPin,
                fieldColors   = fieldColors
            )
            ChangePinField(
                value         = changePinVm.confirmPin,
                onValueChange = { changePinVm.confirmPin = it },
                label         = strings.changePinConfirmNew,
                fieldColors   = fieldColors
            )
            if (changePinVm.state is ChangePinViewModel.State.Error) {
                Text(
                    (changePinVm.state as ChangePinViewModel.State.Error).message,
                    fontSize = 12.sp,
                    color    = DangerRed
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
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier             = Modifier.fillMaxWidth(),
        shape                = RoundedCornerShape(12.dp),
        colors               = fieldColors
    )
}

// ── Language picker dialog ────────────────────────────────────────────────────
