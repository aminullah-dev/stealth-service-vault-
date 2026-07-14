package com.safebeauty.app.ui.screens

// Admin users tab (list, role filter, per-user row + badges), split out of
// AdminDashboardScreen.kt. Same package; UsersTab is internal.

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.navigation.Screen
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.PayoutDocument
import com.safebeauty.app.data.firebase.ProviderBalance
import com.safebeauty.app.data.firebase.SalonBadge
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.UserDocument
import com.safebeauty.app.data.firebase.badge
import com.safebeauty.app.ui.theme.AvailableGreen
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.Gradients
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.ui.theme.WarmGold
import com.safebeauty.app.viewmodel.AdminViewModel
import com.safebeauty.app.viewmodel.LanguageViewModel
import com.safebeauty.app.viewmodel.SystemStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun UsersTab(users: List<UserDocument>, isLoaded: Boolean, viewModel: AdminViewModel) {
    val strings = LocalStrings.current
    var deleteTarget by remember { mutableStateOf<UserDocument?>(null) }
    var searchQuery  by remember { mutableStateOf("") }
    var roleFilter   by remember { mutableStateOf<String?>(null) }   // null = all roles

    val filtered = remember(users, searchQuery, roleFilter) {
        users
            .filter { roleFilter == null || it.role == roleFilter }
            .filter {
                searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true)
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder   = { Text(strings.searchUsersHint, fontSize = 13.sp, color = RoseGold) },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = RoseGold, modifier = Modifier.size(20.dp)) },
            trailingIcon  = if (searchQuery.isNotBlank()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, tint = RoseGold) } }
            } else null,
            singleLine = true,
            shape      = RoundedCornerShape(14.dp),
            modifier   = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors     = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = DeepRose,
                unfocusedBorderColor = BlushPink
            )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            RoleFilterChip(strings.filterAll, roleFilter == null) { roleFilter = null }
            RoleFilterChip(strings.roleBadgeCustomer, roleFilter == "CUSTOMER") { roleFilter = "CUSTOMER" }
            RoleFilterChip(strings.roleBadgeProvider, roleFilter == "PROVIDER") { roleFilter = "PROVIDER" }
            RoleFilterChip(strings.roleBadgeAdmin, roleFilter == "ADMIN") { roleFilter = "ADMIN" }
        }

        if (!isLoaded) {
            LoadingBox()
        } else if (filtered.isEmpty()) {
            CenteredEmpty(
                Icons.Default.Group,
                strings.statsTotalUsers,
                if (searchQuery.isNotBlank() || roleFilter != null) strings.noUsersMatchSearch else ""
            )
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.uid }) { user ->
                    UserRow(
                        user        = user,
                        onDelete    = { deleteTarget = user },
                        onSuspend   = { viewModel.suspendUser(user.uid) },
                        onUnsuspend = { viewModel.unsuspendUser(user.uid) }
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text(strings.deleteUserConfirmTitle, color = DeepRose) },
            text             = { Text(strings.deleteUserConfirmText, color = RoseGold) },
            confirmButton    = {
                TextButton(onClick = {
                    viewModel.deleteUser(target.uid, target.role == "PROVIDER")
                    deleteTarget = null
                }) {
                    Text(strings.deleteUser, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(strings.cancel, color = RoseGold)
                }
            }
        )
    }
}

@Composable
private fun RoleFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) DeepRose else BlushPink.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else DeepRose
        )
    }
}

@Composable
private fun UserRow(
    user: UserDocument,
    onDelete: () -> Unit,
    onSuspend: () -> Unit,
    onUnsuspend: () -> Unit
) {
    val strings = LocalStrings.current
    val roleColor = when (user.role) {
        "ADMIN"    -> Color(0xFF7B6FA0)
        "PROVIDER" -> AvailableGreen
        else       -> RoseGold
    }
    val statusColor = when (user.status) {
        "APPROVED"  -> AvailableGreen
        "REJECTED"  -> UnavailableGrey
        "SUSPENDED" -> Color(0xFFE67E22)
        else        -> Color(0xFFE67E22)
    }

    ElevatedCard(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier             = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(40.dp)
                    .background(roleColor.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Person, null, tint = roleColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleBadge(user.role, roleColor)
                    StatusBadge(user.status, statusColor)
                }
            }
            if (user.role != "ADMIN") {
                if (user.status == "SUSPENDED") {
                    IconButton(onClick = onUnsuspend, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.LockOpen, strings.unsuspendUser, tint = AvailableGreen, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onSuspend, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Block, strings.suspendUser, tint = Color(0xFFE67E22), modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, strings.deleteUser, tint = UnavailableGrey, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String, color: Color) {
    val strings = LocalStrings.current
    val label = when (role) {
        "ADMIN"    -> strings.roleBadgeAdmin
        "PROVIDER" -> strings.roleBadgeProvider
        else       -> strings.roleBadgeCustomer
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(status: String, color: Color) {
    val strings = LocalStrings.current
    val label = when (status) {
        "APPROVED"  -> strings.approved
        "REJECTED"  -> strings.rejected
        "SUSPENDED" -> strings.suspended
        else        -> strings.pending
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Tab 2: Stats ──────────────────────────────────────────────────────────────
