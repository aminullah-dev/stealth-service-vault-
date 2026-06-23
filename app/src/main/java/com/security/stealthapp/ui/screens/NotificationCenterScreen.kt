package com.security.stealthapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.data.firebase.NotificationDocument
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.ChipActive
import com.security.stealthapp.ui.theme.ChipInactive
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.WarmGold
import com.security.stealthapp.viewmodel.NotificationCenterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NotifFilter { ALL, UNREAD, BOOKINGS, WAITLIST, SYSTEM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    viewModel: NotificationCenterViewModel = hiltViewModel()
) {
    val strings       = LocalStrings.current
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var activeFilter  by remember { mutableStateOf(NotifFilter.ALL) }

    val filtered = remember(notifications, activeFilter) {
        when (activeFilter) {
            NotifFilter.ALL      -> notifications
            NotifFilter.UNREAD   -> notifications.filter { !it.isRead }
            NotifFilter.BOOKINGS -> notifications.filter {
                it.type in listOf("BOOKING_CONFIRMED", "BOOKING_CANCELLED", "NEW_BOOKING")
            }
            NotifFilter.WAITLIST -> notifications.filter { it.type == "WAITLIST" }
            NotifFilter.SYSTEM   -> notifications.filter {
                it.type in listOf("SYSTEM", "BROADCAST")
            }
        }
    }

    val hasUnread = notifications.any { !it.isRead }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            strings.notificationCenterTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp,
                            color      = DeepRose
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = DeepRose)
                        }
                    },
                    actions = {
                        if (hasUnread) {
                            TextButton(onClick = { viewModel.markAllRead() }) {
                                Text(
                                    strings.notificationsMarkAllRead,
                                    color    = RoseGold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Filter chips ──────────────────────────────────────────────
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val filters = listOf(
                        NotifFilter.ALL      to strings.notificationFilterAll,
                        NotifFilter.UNREAD   to strings.notificationFilterUnread,
                        NotifFilter.BOOKINGS to strings.notificationFilterBookings,
                        NotifFilter.WAITLIST to strings.notificationFilterWaitlist,
                        NotifFilter.SYSTEM   to strings.notificationFilterSystem
                    )
                    items(filters) { (filter, label) ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick  = { activeFilter = filter },
                            label    = { Text(label, fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor    = RoseGold,
                                selectedLabelColor        = Color.White,
                                containerColor            = DashboardSurface,
                                labelColor                = ChipInactive
                            )
                        )
                    }
                }

                // ── Content ───────────────────────────────────────────────────
                if (filtered.isEmpty()) {
                    Box(
                        modifier          = Modifier.fillMaxSize(),
                        contentAlignment  = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Notifications,
                                contentDescription = null,
                                tint               = ChipInactive,
                                modifier           = Modifier.size(64.dp)
                            )
                            Text(
                                text      = strings.notificationsEmpty,
                                fontSize  = 15.sp,
                                color     = ChipInactive,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { notif ->
                            SwipeableNotificationCard(
                                notification = notif,
                                onRead       = { viewModel.markRead(notif.id) },
                                onDismiss    = { viewModel.delete(notif.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationCard(
    notification: NotificationDocument,
    onRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDismiss(); true }
            else false
        }
    )

    SwipeToDismissBox(
        state              = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent  = {
            val bg by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    Color(0xFFD32F2F) else Color.Transparent,
                label = "swipe_bg"
            )
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        NotificationCard(notification = notification, onClick = onRead)
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationDocument,
    onClick: () -> Unit
) {
    val bgColor = if (!notification.isRead) Color(0xFFFFF0F3) else DashboardSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!notification.isRead) onClick() },
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!notification.isRead) 2.dp else 0.dp)
    ) {
        Row(
            modifier            = Modifier.padding(14.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // ── Type icon ─────────────────────────────────────────────────────
            val (icon, iconBg) = notifIconAndColor(notification.type)
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(14.dp))

            // ── Text content ──────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = notification.title,
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium,
                    fontSize   = 14.sp,
                    color      = DeepRose,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (notification.body.isNotBlank()) {
                    Text(
                        text     = notification.body,
                        fontSize = 13.sp,
                        color    = Color(0xFF555555),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text     = formatNotifTime(notification.createdAt),
                    fontSize = 11.sp,
                    color    = ChipInactive
                )
            }

            // ── Unread indicator ──────────────────────────────────────────────
            if (!notification.isRead) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(DeepRose)
                )
            }
        }
    }
}

private fun notifIconAndColor(type: String): Pair<ImageVector, Color> = when (type) {
    "BOOKING_CONFIRMED" -> Icons.Default.CheckCircle to AvailableGreen
    "BOOKING_CANCELLED" -> Icons.Default.Cancel      to Color(0xFFD32F2F)
    "NEW_BOOKING"       -> Icons.Default.CalendarMonth to RoseGold
    "WAITLIST"          -> Icons.Default.Schedule    to WarmGold
    "BROADCAST"         -> Icons.Default.Campaign    to Color(0xFF7B6FA0)
    else                -> Icons.Default.Info        to ChipInactive
}

private fun formatNotifTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val mins   = diffMs / 60_000
    val hours  = diffMs / 3_600_000
    val days   = diffMs / 86_400_000
    return when {
        mins  < 1  -> "Just now"
        mins  < 60 -> "$mins min ago"
        hours < 24 -> "${hours}h ago"
        days  == 1L -> "Yesterday"
        else        -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(epochMs))
    }
}
