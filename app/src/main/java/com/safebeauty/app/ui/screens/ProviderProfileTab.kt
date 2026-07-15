package com.safebeauty.app.ui.screens

// Provider profile tab (edit salon: location, staff, portfolio, working hours),
// split out of ProviderDashboardScreen.kt. Same package; ProfileTab is internal,
// its sections stay private to this file.

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
import androidx.compose.material.icons.filled.LocalOffer
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
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
import com.safebeauty.app.util.DateUtils
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileTab(viewModel: ProviderViewModel) {
    val strings     = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Location card ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardSurface)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.sectionLocation, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
                HorizontalDivider(color = BlushPink)
                OutlinedTextField(
                    value         = viewModel.editDistrict,
                    onValueChange = viewModel::onDistrictChanged,
                    label         = { Text(strings.districtArea, fontSize = 13.sp) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors
                )
            }
        }

        // ── Services card ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardSurface)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.sectionServices, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
                HorizontalDivider(color = BlushPink)

                if (viewModel.editServices.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        viewModel.editServices.forEach { service ->
                            InputChip(
                                selected     = false,
                                onClick      = {},
                                label        = { Text(service, fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick  = { viewModel.removeService(service) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = strings.a11yRemoveService(service),
                                            modifier           = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor         = ChipInactive,
                                    labelColor             = DeepRose,
                                    trailingIconColor      = RoseGold,
                                    selectedContainerColor = ChipActive,
                                )
                            )
                        }
                    }
                } else {
                    Text(strings.noServicesAdded, fontSize = 13.sp, color = Color(0xFFAAAAAA))
                }

                // ── Price per service ──────────────────────────────────────
                if (viewModel.editServices.isNotEmpty()) {
                    Text(
                        strings.incomePriceLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = RoseGold,
                        modifier   = Modifier.padding(top = 4.dp)
                    )
                    viewModel.editServices.forEach { service ->
                        var priceText by remember(service) {
                            mutableStateOf((viewModel.editPrices[service] ?: 0).toString())
                        }
                        var minutesText by remember(service) {
                            mutableStateOf((viewModel.editDurations[service] ?: 0).takeIf { it > 0 }?.toString() ?: "")
                        }
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                service,
                                fontSize = 13.sp,
                                color    = DeepRose,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value         = minutesText,
                                onValueChange = { v ->
                                    minutesText = v.filter { it.isDigit() }
                                    viewModel.setDurationForService(service, minutesText.toIntOrNull() ?: 0)
                                },
                                singleLine        = true,
                                keyboardOptions   = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder       = { Text(strings.serviceMinutesHint, fontSize = 11.sp) },
                                modifier          = Modifier.width(92.dp),
                                shape             = RoundedCornerShape(10.dp),
                                colors            = fieldColors,
                                suffix            = { Text(strings.minutesShort, fontSize = 11.sp, color = RoseGold) }
                            )
                            OutlinedTextField(
                                value         = priceText,
                                onValueChange = { v ->
                                    priceText = v.filter { it.isDigit() }
                                    viewModel.setPriceForService(service, priceText.toIntOrNull() ?: 0)
                                },
                                singleLine        = true,
                                keyboardOptions   = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder       = { Text(strings.incomePriceHint, fontSize = 12.sp) },
                                modifier          = Modifier.width(110.dp),
                                shape             = RoundedCornerShape(10.dp),
                                colors            = fieldColors,
                                suffix            = { Text(strings.incomeAFN, fontSize = 11.sp, color = RoseGold) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = viewModel.newServiceDraft,
                        onValueChange = viewModel::onNewServiceDraftChanged,
                        label         = { Text(strings.addServiceLabel, fontSize = 12.sp) },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = fieldColors
                    )
                    IconButton(
                        onClick  = { viewModel.addService() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (viewModel.newServiceDraft.isNotBlank()) RoseGold else ChipInactive)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.addServiceLabel, tint = Color.White)
                    }
                }
            }
        }

        // ── Working hours card ────────────────────────────────────────────
        WorkingHoursSection(viewModel = viewModel)

        // ── Portfolio / sample-work photos ────────────────────────────────
        PortfolioSection(viewModel = viewModel)

        // ── Offers / promotions ───────────────────────────────────────────
        OffersSection(viewModel = viewModel)

        // ── Last-minute deal ──────────────────────────────────────────────
        LastMinuteSection(viewModel = viewModel)

        // ── Staff / stylists roster ───────────────────────────────────────
        StaffSection(viewModel = viewModel)

        // ── Salon location ────────────────────────────────────────────────
        LocationSection(viewModel = viewModel)

        // ── Payout card ───────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardSurface)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.sectionPayout, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
                HorizontalDivider(color = BlushPink)
                OutlinedTextField(
                    value         = viewModel.editHesabAccountNumber,
                    onValueChange = viewModel::onHesabAccountNumberChanged,
                    label         = { Text(strings.hesabAccountNumberLabel, fontSize = 13.sp) },
                    supportingText = { Text(strings.hesabAccountNumberHint, fontSize = 11.sp) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors
                )
            }
        }

        // ── Time off (blocked days) ─────────────────────────────────────────
        TimeOffSection(
            blockedDates = viewModel.editBlockedDates,
            onToggleDate = { viewModel.toggleBlockedDate(it) }
        )

        // ── Save button ───────────────────────────────────────────────────
        Button(
            onClick  = { viewModel.saveProfile() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
        ) {
            Text(strings.saveProfile, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // ── Change PIN ────────────────────────────────────────────────────
        val changePinVm: ChangePinViewModel = hiltViewModel()
        ChangePinSection(changePinVm = changePinVm)

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Lets the provider pin the salon's location from their device GPS (no maps SDK
 * — a dependency-free framework read). Customers then see the distance to the
 * salon and can open directions in their own maps app. Location is optional.
 */
@Composable
private fun LocationSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val loc = com.safebeauty.app.util.LocationHelper.lastKnownLocation(context)
            if (loc != null) {
                viewModel.setLocation(loc.latitude, loc.longitude)
                status = strings.locationCaptured
            } else {
                status = strings.locationUnavailable
            }
        } else {
            status = strings.locationPermissionNeeded
        }
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.sectionLocationPin, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            }
            Text(strings.locationPinHint, fontSize = 11.sp, color = Color(0xFF999999))
            HorizontalDivider(color = BlushPink)

            val isSet = viewModel.editLatitude != 0.0 || viewModel.editLongitude != 0.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSet) Icons.Default.CheckCircle else Icons.Default.LocationOff,
                    null,
                    tint = if (isSet) AvailableGreen else Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isSet) strings.locationIsSet else strings.locationNotSet,
                    fontSize = 13.sp,
                    color    = if (isSet) DeepRose else Color(0xFF888888)
                )
            }

            OutlinedButton(
                onClick  = {
                    permLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MyLocation, null, tint = RoseGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.useMyLocation, color = DeepRose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            status?.let { Text(it, fontSize = 12.sp, color = RoseGold) }
        }
    }
}

/**
 * Lets a salon manage its stylists. Adding staff turns the salon into a
 * multi-chair business: customers can then pick a specific stylist, and the
 * booking calendar allows one parallel booking per active stylist in the same
 * time slot. A solo salon simply leaves this empty.
 */
@Composable
private fun StaffSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        cursorColor          = RoseGold,
        focusedLabelColor    = RoseGold
    )
    // One picker shared by all staff rows; the tapped row sets the target first.
    var photoTargetStaff by remember { mutableStateOf("") }
    val staffPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = photoTargetStaff
        if (uri == null || target.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = ImageUtils.uriToCompressedBytes(context, uri)) {
                is ImageUtils.BytesResult.Success -> viewModel.addStaffPhoto(target, result.bytes)
                else -> { /* size/format errors surfaced elsewhere */ }
            }
        }
    }
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.sectionStaff, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            }
            Text(strings.staffHint, fontSize = 11.sp, color = Color(0xFF999999))
            HorizontalDivider(color = BlushPink)

            if (viewModel.editStaff.isEmpty()) {
                Text(strings.staffEmpty, fontSize = 12.sp, color = Color(0xFFAAAAAA))
            } else {
                viewModel.editStaff.forEach { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                member.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (member.active) DeepRose else Color(0xFFAAAAAA)
                            )
                            if (member.specialty.isNotBlank()) {
                                Text(member.specialty, fontSize = 11.sp, color = RoseGold)
                            }
                        }
                        // Active toggle — an inactive stylist is hidden from booking
                        // without deleting their history.
                        Switch(
                            checked = member.active,
                            onCheckedChange = { viewModel.toggleStaffActive(member.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AvailableGreen
                            )
                        )
                        IconButton(onClick = { viewModel.removeStaff(member.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = strings.staffRemove, tint = Color(0xFFB00020))
                        }
                    }
                    // ── Per-stylist portfolio (up to 4 photos) ────────────────
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    ) {
                        items(member.photoUrls) { url ->
                            Box {
                                AsyncImage(
                                    model              = url,
                                    contentDescription = null,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick  = { viewModel.removeStaffPhoto(member.id, url) },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = strings.remove,
                                        tint = DeepRose, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (member.photoUrls.size < 4) {
                            item {
                                OutlinedButton(
                                    onClick = { photoTargetStaff = member.id
                                        staffPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        ) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = BlushPink.copy(alpha = 0.4f))
                }
            }

            OutlinedTextField(
                value         = viewModel.newStaffName,
                onValueChange = viewModel::onNewStaffNameChanged,
                label         = { Text(strings.staffNameLabel, fontSize = 12.sp) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = fieldColors
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = viewModel.newStaffSpecialty,
                    onValueChange = viewModel::onNewStaffSpecialtyChanged,
                    label         = { Text(strings.staffSpecialtyLabel, fontSize = 12.sp) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (viewModel.newStaffName.isNotBlank()) RoseGold else ChipInactive)
                        .clickable(enabled = viewModel.newStaffName.isNotBlank()) { viewModel.addStaff() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.staffAdd, tint = Color.White)
                }
            }
        }
    }
}

// ── Offers section ──────────────────────────────────────────────────────────────

@Composable
private fun OffersSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val offers  by viewModel.offers.collectAsStateWithLifecycle()
    var title   by remember { mutableStateOf("") }
    var desc    by remember { mutableStateOf("") }
    var percent by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        cursorColor          = RoseGold,
        focusedLabelColor    = RoseGold
    )
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.offersTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            }
            HorizontalDivider(color = BlushPink)

            if (offers.isEmpty()) {
                Text(strings.noOffersYet, fontSize = 12.sp, color = Color(0xFFAAAAAA))
            } else {
                offers.forEach { offer ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                offer.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (offer.active) DeepRose else Color(0xFFAAAAAA)
                            )
                            if (offer.description.isNotBlank()) {
                                Text(offer.description, fontSize = 11.sp, color = RoseGold)
                            }
                        }
                        Switch(
                            checked = offer.active,
                            onCheckedChange = { viewModel.toggleOffer(offer.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AvailableGreen
                            )
                        )
                        IconButton(onClick = { viewModel.deleteOffer(offer.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFB00020))
                        }
                    }
                    HorizontalDivider(color = BlushPink.copy(alpha = 0.4f))
                }
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(strings.offerTitleHint, fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), colors = fieldColors
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                label = { Text(strings.offerDescHint, fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), colors = fieldColors
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = percent,
                    onValueChange = { v -> if (v.length <= 3 && v.all(Char::isDigit)) percent = v },
                    label = { Text(strings.offerPercentHint, fontSize = 12.sp) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (title.isNotBlank()) RoseGold else ChipInactive)
                        .clickable(enabled = title.isNotBlank()) {
                            viewModel.addOffer(title, desc, percent.toIntOrNull() ?: 0)
                            title = ""; desc = ""; percent = ""
                        }
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.addOffer, tint = Color.White)
                }
            }
        }
    }
}

// ── Portfolio section ───────────────────────────────────────────────────────────

@Composable
private fun PortfolioSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val gallery by viewModel.gallery.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.photoError = null
        viewModel.isUploadingPhoto = true
        scope.launch {
            when (val result = ImageUtils.uriToCompressedBytes(context, uri)) {
                is ImageUtils.BytesResult.Success  -> viewModel.addGalleryImage(result.bytes)
                is ImageUtils.BytesResult.TooLarge -> {
                    viewModel.isUploadingPhoto = false
                    viewModel.photoError = strings.photoTooLarge
                }
                is ImageUtils.BytesResult.Failed   -> {
                    viewModel.isUploadingPhoto = false
                    viewModel.photoError = strings.photoUploadFailed
                }
            }
        }
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(strings.portfolioTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            HorizontalDivider(color = BlushPink)

            if (gallery.isEmpty() && !viewModel.isUploadingPhoto) {
                Text(strings.noPhotosYet, fontSize = 13.sp, color = Color(0xFFAAAAAA))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (viewModel.isUploadingPhoto) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BlushPink)
                            ) {
                                CircularProgressIndicator(
                                    color       = RoseGold,
                                    strokeWidth = 2.dp,
                                    modifier    = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    items(gallery, key = { it.id }) { image ->
                        ProviderGalleryThumb(
                            image    = image,
                            onDelete = { viewModel.deleteGalleryImage(image.id) },
                            deleteCd = strings.deletePhoto
                        )
                    }
                }
            }

            viewModel.photoError?.let { err ->
                Text(err, fontSize = 12.sp, color = Color(0xFFD32F2F))
            }

            Button(
                onClick  = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled  = !viewModel.isUploadingPhoto,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (viewModel.isUploadingPhoto) strings.uploadingPhoto else strings.addPhoto,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProviderGalleryThumb(
    image: GalleryImageDocument,
    onDelete: () -> Unit,
    deleteCd: String
) {
    val thumbModifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(12.dp))
    Box(modifier = Modifier.size(96.dp)) {
        if (image.imageUrl.isNotBlank()) {
            AsyncImage(
                model              = image.imageUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = thumbModifier
            )
        } else {
            val bitmap = remember(image.id) { ImageUtils.base64ToBitmap(image.imageBase64) }
            if (bitmap != null) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = thumbModifier
                )
            } else {
                Box(modifier = thumbModifier.background(BlushPink))
            }
        }
        // Delete badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xCC000000))
                .clickable { onDelete() }
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = deleteCd,
                tint               = Color.White,
                modifier           = Modifier.size(14.dp)
            )
        }
    }
}

// ── Analytics tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkingHoursSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    // Map dayOfWeek constant → display label
    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        7    -> strings.daySat
        1    -> strings.daySun
        2    -> strings.dayMon
        3    -> strings.dayTue
        4    -> strings.dayWed
        5    -> strings.dayThu
        6    -> strings.dayFri
        else -> ""
    }

    // Ordered list: Sat(7), Sun(1), Mon(2), Tue(3), Wed(4), Thu(5), Fri(6)
    val orderedDays = listOf(7, 1, 2, 3, 4, 5, 6)

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.workingHoursTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            HorizontalDivider(color = BlushPink)

            orderedDays.forEach { dow ->
                val wh = viewModel.editWorkingHours.find { it.dayOfWeek == dow }
                if (wh != null) {
                    Column {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text       = dayLabel(dow),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color      = DeepRose,
                                modifier   = Modifier.weight(1f)
                            )
                            if (!wh.isOpen) {
                                Text(
                                    text     = strings.closedThisDay,
                                    fontSize = 12.sp,
                                    color    = Color(0xFFAAAAAA)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Switch(
                                checked         = wh.isOpen,
                                onCheckedChange = { viewModel.toggleDayOpen(dow) },
                                colors          = SwitchDefaults.colors(
                                    checkedThumbColor   = Color.White,
                                    checkedTrackColor   = RoseGold,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCCCCCC)
                                )
                            )
                        }
                        if (wh.isOpen) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            ) {
                                val openLabel  = "%02d:%02d".format(wh.openHour, wh.openMinute)
                                val closeLabel = "%02d:%02d".format(wh.closeHour, wh.closeMinute)

                                Text(strings.openTime, fontSize = 12.sp, color = Color(0xFF888888))
                                TextButton(
                                    onClick = {
                                        TimePickerDialog(context, { _, h, m ->
                                            viewModel.setDayOpenTime(dow, h, m)
                                        }, wh.openHour, wh.openMinute, true).show()
                                    }
                                ) {
                                    Text(openLabel, fontSize = 14.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                                }

                                Text("–", fontSize = 14.sp, color = Color(0xFF888888))

                                Text(strings.closeTime, fontSize = 12.sp, color = Color(0xFF888888))
                                TextButton(
                                    onClick = {
                                        TimePickerDialog(context, { _, h, m ->
                                            viewModel.setDayCloseTime(dow, h, m)
                                        }, wh.closeHour, wh.closeMinute, true).show()
                                    }
                                ) {
                                    Text(closeLabel, fontSize = 14.sp, color = RoseGold, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BlushPink)

            // Slot duration selector
            Text(strings.slotDurationLabel, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DeepRose)
            val durations = listOf(
                30 to strings.slotDuration30,
                45 to strings.slotDuration45,
                60 to strings.slotDuration60,
                90 to strings.slotDuration90
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                durations.forEach { (minutes, label) ->
                    FilterChip(
                        selected = viewModel.editSlotDuration == minutes,
                        onClick  = { viewModel.setSlotDuration(minutes) },
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
        }
    }
}

/**
 * Lets a provider block off days (holidays, time off). Blocked days offer no
 * booking slots to customers. Dates are kept as Kabul-local "yyyy-MM-dd" keys.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimeOffSection(
    blockedDates: List<String>,
    onToggleDate: (String) -> Unit
) {
    val strings = LocalStrings.current
    var showPicker by remember { mutableStateOf(false) }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(strings.timeOffTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            HorizontalDivider(color = BlushPink)
            Text(strings.timeOffHint, fontSize = 11.sp, color = DeepRose)

            if (blockedDates.isEmpty()) {
                Text(strings.timeOffNone, fontSize = 12.sp, color = Color(0xFFAAAAAA))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    blockedDates.forEach { date ->
                        InputChip(
                            selected = true,
                            onClick  = { onToggleDate(date) },
                            label    = { Text(date, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = strings.remove, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showPicker = true },
                shape   = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.timeOffAdd, fontSize = 13.sp)
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onToggleDate(DateUtils.kabulDateKey(ms))
                    }
                    showPicker = false
                }) { Text(strings.ok, color = RoseGold) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(strings.cancel, color = RoseGold) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Lets a provider turn on an automatic "last-minute" discount that applies to any
 * booking whose slot starts within the chosen window — a simple way to fill
 * soon-to-be-empty chairs. The discount is applied server-side at checkout.
 */
@Composable
private fun LastMinuteSection(viewModel: ProviderViewModel) {
    val strings = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        cursorColor          = RoseGold,
        focusedLabelColor    = RoseGold
    )
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    strings.lastMinuteTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = RoseGold,
                    modifier   = Modifier.weight(1f)
                )
                Switch(
                    checked = viewModel.editLastMinuteEnabled,
                    onCheckedChange = { viewModel.editLastMinuteEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AvailableGreen
                    )
                )
            }
            Text(strings.lastMinuteHint, fontSize = 11.sp, color = DeepRose)

            if (viewModel.editLastMinuteEnabled) {
                var pctText by remember {
                    mutableStateOf(viewModel.editLastMinutePercent.takeIf { it > 0 }?.toString() ?: "")
                }
                var winText by remember {
                    mutableStateOf(viewModel.editLastMinuteWindow.takeIf { it > 0 }?.toString() ?: "")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value         = pctText,
                        onValueChange = { v ->
                            pctText = v.filter { it.isDigit() }.take(3)
                            viewModel.editLastMinutePercent = pctText.toIntOrNull() ?: 0
                        },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label           = { Text(strings.lastMinutePercentLabel, fontSize = 11.sp) },
                        modifier        = Modifier.weight(1f),
                        shape           = RoundedCornerShape(10.dp),
                        colors          = fieldColors,
                        suffix          = { Text("%", fontSize = 11.sp, color = RoseGold) }
                    )
                    OutlinedTextField(
                        value         = winText,
                        onValueChange = { v ->
                            winText = v.filter { it.isDigit() }.take(3)
                            viewModel.editLastMinuteWindow = winText.toIntOrNull() ?: 0
                        },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label           = { Text(strings.lastMinuteWindowLabel, fontSize = 11.sp) },
                        modifier        = Modifier.weight(1f),
                        shape           = RoundedCornerShape(10.dp),
                        colors          = fieldColors,
                        suffix          = { Text(strings.hoursShort, fontSize = 11.sp, color = RoseGold) }
                    )
                }
            }
        }
    }
}
