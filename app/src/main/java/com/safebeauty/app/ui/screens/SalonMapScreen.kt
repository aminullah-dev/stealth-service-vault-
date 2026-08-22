package com.safebeauty.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.hasLocation
import com.safebeauty.app.util.ratingLabel
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.WarmGold

/** Kabul city centre — the fallback view when no salon has coordinates yet. */
private val KABUL = LatLng(34.5553, 69.2075)

/**
 * Salons on a map.
 *
 * Salon coordinates have been stored since the provider console gained its
 * "use my location" button, but nothing ever displayed them — a customer could
 * read a district name and nothing more. Tapping a pin opens a small card with
 * the salon's name, rating and a Book button, so the map is a real entry point
 * into booking rather than a decoration.
 *
 * Only the Maps SDK is used (map loads are not billed). Directions deliberately
 * stay on the existing geo: hand-off to whatever maps app the user has, which
 * avoids the metered Directions API entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalonMapScreen(
    salons: List<SalonDocument>,
    onBack: () -> Unit,
    onBook: (SalonDocument) -> Unit,
) {
    val strings = LocalStrings.current
    val located = remember(salons) { salons.filter { it.hasLocation() } }
    var selected by remember { mutableStateOf<SalonDocument?>(null) }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            located.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: KABUL,
            if (located.isEmpty()) 11f else 13f
        )
    }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = { Text(strings.mapTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RoseGold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camera,
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                    // Tapping empty map dismisses the card, so the map is never
                    // permanently covered by a selection the user is done with.
                    onMapClick = { selected = null },
                ) {
                    located.forEach { salon ->
                        Marker(
                            state = MarkerState(LatLng(salon.latitude, salon.longitude)),
                            title = salon.salonName,
                            snippet = salon.district,
                            onClick = { selected = salon; true },
                        )
                    }
                }

                if (located.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                        modifier = Modifier.align(Alignment.Center).padding(28.dp)
                    ) {
                        Text(
                            strings.mapNoLocations,
                            fontSize = 13.sp, color = TextMuted,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }

                selected?.let { salon ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                salon.salonName,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.size(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = RoseGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(salon.district, fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Star, null, tint = WarmGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(ratingLabel(salon.rating, strings), fontSize = 12.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.size(12.dp))
                            Button(
                                onClick = { onBook(salon) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                            ) { Text(strings.book, color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}
