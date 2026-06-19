package com.security.stealthapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.data.firebase.UserDocument
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.BlushPink
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.UnavailableGrey
import com.security.stealthapp.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLockTriggered: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.lockTriggered) {
        if (viewModel.lockTriggered) {
            viewModel.resetLockTrigger()
            onLockTriggered()
        }
    }

    val pendingProviders by viewModel.pendingProviders.collectAsStateWithLifecycle()

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text       = "Admin Panel",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = DeepRose
                            )
                            Text(
                                text     = "Provider approval queue",
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    navigationIcon = {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint     = RoseGold,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.triggerLock() }) {
                            Icon(Icons.Default.Lock, "Lock", tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            if (pendingProviders.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .background(BlushPink.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                null,
                                tint     = RoseGold,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "No pending applications",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = DeepRose
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "New provider registrations\nwill appear here for approval.",
                            fontSize  = 13.sp,
                            color     = Color(0xFFAAAAAA),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.padding(padding)
                ) {
                    items(pendingProviders, key = { it.uid }) { provider ->
                        PendingProviderCard(
                            provider  = provider,
                            onApprove = { viewModel.approveProvider(provider.uid) },
                            onReject  = { viewModel.rejectProvider(provider.uid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingProviderCard(
    provider: UserDocument,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    ElevatedCard(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(BlushPink, RoundedCornerShape(14.dp))
                ) {
                    Icon(Icons.Default.Person, null, tint = DeepRose, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = provider.name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = DeepRose
                    )
                    Text(
                        text     = provider.phone.ifBlank { "No phone provided" },
                        fontSize = 12.sp,
                        color    = RoseGold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(UnavailableGrey.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Pending",
                        fontSize   = 11.sp,
                        color      = UnavailableGrey,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BlushPink.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick        = onApprove,
                    modifier       = Modifier.weight(1f),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = AvailableGreen),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Approve", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick        = onReject,
                    modifier       = Modifier.weight(1f),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = UnavailableGrey.copy(alpha = 0.8f)),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Reject", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
