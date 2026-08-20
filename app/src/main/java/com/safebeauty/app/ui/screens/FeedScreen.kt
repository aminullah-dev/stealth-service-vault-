package com.safebeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.safebeauty.app.data.firebase.SalonPostDocument
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.viewmodel.FeedViewModel
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat

/**
 * Discover — a photo grid of what salons are actually producing.
 *
 * People choose a salon by looking at its work, not by reading its name. This
 * screen used to be a vertical list of full-width cards: roughly one salon per
 * screenful, with the name and caption taking as much room as the photo. A
 * three-column grid puts about nine pieces of work in the same space, which is
 * the whole point of a discovery surface — density of things worth tapping.
 *
 * Tapping a tile opens the photo with its caption and a way through to the
 * salon, so discovery leads somewhere instead of dead-ending on an image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onBack: () -> Unit,
    onOpenSalon: (String) -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel()
) {
    val strings = LocalStrings.current
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<SalonPostDocument?>(null) }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Text(strings.feedTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DeepRose)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            if (posts.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary, null,
                            tint = ChipInactive, modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text      = strings.feedEmpty,
                            fontSize  = 15.sp,
                            color     = ChipInactive,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                // 1dp gaps, edge to edge: the photos form one continuous surface
                // rather than a list of separated cards.
                LazyVerticalGrid(
                    columns             = GridCells.Fixed(3),
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement   = Arrangement.spacedBy(1.dp),
                ) {
                    items(posts, key = { it.id }) { post ->
                        AsyncImage(
                            model              = post.imageUrl,
                            contentDescription = post.salonName,
                            contentScale       = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(DashboardSurface)
                                .clickable { opened = post }
                        )
                    }
                }
            }
        }

        // Tapping a tile opens the full photo, its caption, and the route to the
        // salon — the grid is for finding work, this is for acting on it.
        opened?.let { post ->
            ModalBottomSheet(
                onDismissRequest = { opened = null },
                sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor   = ElegantCream
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 28.dp)
                ) {
                    AsyncImage(
                        model              = post.imageUrl,
                        contentDescription = post.salonName,
                        contentScale       = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .background(DashboardSurface)
                    )
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            post.salonName,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepRose,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (post.caption.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(post.caption, fontSize = 14.sp, color = TextStrong)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(formatFeedTime(post.createdAt), fontSize = 11.sp, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val id = post.salonId
                                opened = null
                                if (id.isNotBlank()) onOpenSalon(id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) {
                            Text(strings.viewSalon, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Relative time for a post ("2 hours ago"), falling back to a date after a day. */
@Composable
private fun formatFeedTime(epochMs: Long): String {
    val strings = LocalStrings.current
    val diffMs  = System.currentTimeMillis() - epochMs
    val mins    = diffMs / 60_000
    val hours   = diffMs / 3_600_000
    val days    = diffMs / 86_400_000
    return when {
        mins  < 1   -> strings.timeJustNow
        mins  < 60  -> strings.timeMinsAgo(mins.toInt())
        hours < 24  -> strings.timeHoursAgo(hours.toInt())
        days  == 1L -> strings.timeYesterday
        else        -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(epochMs))
    }
}
