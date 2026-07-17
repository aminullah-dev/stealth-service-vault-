package com.safebeauty.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.viewmodel.FeedViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onBack: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val strings = LocalStrings.current
    val posts by viewModel.posts.collectAsStateWithLifecycle()

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
                    Text(
                        text      = strings.feedEmpty,
                        fontSize  = 15.sp,
                        color     = ChipInactive,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(posts, key = { it.id }) { post -> FeedPostCard(post) }
                }
            }
        }
    }
}

@Composable
private fun FeedPostCard(post: SalonPostDocument) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = DashboardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            AsyncImage(
                model              = post.imageUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxWidth().height(280.dp)
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Text(post.salonName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRose)
                if (post.caption.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(post.caption, fontSize = 13.sp, color = TextStrong)
                }
                Spacer(Modifier.height(6.dp))
                Text(formatFeedTime(post.createdAt), fontSize = 11.sp, color = ChipInactive)
            }
        }
    }
}

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
