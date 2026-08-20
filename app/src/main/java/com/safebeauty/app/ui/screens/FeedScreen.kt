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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.imePadding
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
import com.safebeauty.app.data.firebase.StoryDocument
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextStrong
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import com.safebeauty.app.ui.theme.Gradients
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
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentFailed by viewModel.commentFailed.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<SalonPostDocument?>(null) }
    var openStory by remember { mutableStateOf<StoryDocument?>(null) }

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
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // A salon with no portfolio can still be announcing a free
                    // chair; the empty grid must not hide that.
                    if (stories.isNotEmpty()) StoryRow(stories = stories, onOpen = { openStory = it })
                    Box(
                        modifier         = Modifier.fillMaxSize(),
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
                }
            } else {
                // A fixed 3-up grid assumes a full catalogue. At launch there are
                // one or two photos on the whole platform, and three columns turn
                // that into a lonely tile in the corner above two thirds of empty
                // row -- which reads as broken rather than as new. Widening the
                // tiles while there are few of them keeps every row full, and the
                // grid tightens to 3-up on its own as salons post.
                val columns = when {
                    posts.size < 3  -> posts.size   // 1 or 2 photos fill their own row
                    posts.size == 4 -> 2            // a 2x2 block beats a row of 3 plus an orphan
                    else            -> 3
                }
                // 1dp gaps, edge to edge: the photos form one continuous surface
                // rather than a list of separated cards.
                LazyVerticalGrid(
                    columns             = GridCells.Fixed(columns),
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement   = Arrangement.spacedBy(1.dp),
                ) {
                    // Today's availability sits above the portfolio grid: a free
                    // chair this afternoon is worth more to both sides than a photo
                    // from last month, and it expires on its own.
                    if (stories.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StoryRow(stories = stories, onOpen = { openStory = it })
                        }
                    }
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
        // Full-screen-ish story viewer: the announcement text is the payload, the
        // photo is optional decoration, and the salon is one tap away.
        openStory?.let { story ->
            val hoursLeft = ((story.expiresAt - System.currentTimeMillis()) / 3_600_000L)
                .coerceAtLeast(0L).toInt()
            ModalBottomSheet(
                onDismissRequest = { openStory = null },
                sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor   = ElegantCream
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 28.dp)
                ) {
                    if (story.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model              = story.imageUrl,
                            contentDescription = story.salonName,
                            contentScale       = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                                .background(DashboardSurface)
                        )
                    }
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            story.salonName,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepRose,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(story.text, fontSize = 15.sp, color = TextStrong)
                        Spacer(Modifier.height(8.dp))
                        Text(strings.storyExpires(hoursLeft), fontSize = 11.sp, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val id = story.salonId
                                openStory = null
                                if (id.isNotBlank()) onOpenSalon(id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.viewSalon, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        opened?.let { tapped ->
            // The counts move while the sheet is open — this user comments, or
            // someone else likes the photo — so render from the live list rather
            // than from the snapshot captured at tap time.
            val post  = posts.firstOrNull { it.id == tapped.id } ?: tapped
            val liked = post.id in likedIds
            var draft by remember(post.id) { mutableStateOf("") }

            // One thread is listened to at a time, and only while its sheet is
            // open: a listener per tile would put a hundred of them on Discover.
            LaunchedEffect(post.id) { viewModel.openComments(post.id) }

            ModalBottomSheet(
                onDismissRequest = { opened = null; viewModel.closeComments() },
                sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor   = ElegantCream
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        // Without this the keyboard covers the comment field it
                        // was opened to fill.
                        .imePadding()
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

                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleLike(post) }) {
                                Icon(
                                    imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = strings.feedLikes,
                                    tint = if (liked) DeepRose else TextMuted,
                                )
                            }
                            Text("${post.likeCount}", fontSize = 13.sp, color = TextStrong)
                            Spacer(Modifier.width(14.dp))
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = strings.feedComments,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("${post.commentCount}", fontSize = 13.sp, color = TextStrong)
                        }

                        Spacer(Modifier.height(10.dp))
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

                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = DashboardSurface)
                        Spacer(Modifier.height(14.dp))

                        Text(
                            strings.feedComments,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRose
                        )
                        Spacer(Modifier.height(10.dp))

                        if (comments.isEmpty()) {
                            Text(strings.feedNoComments, fontSize = 13.sp, color = TextMuted)
                        } else {
                            comments.forEach { c ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            c.authorName.ifBlank { "—" },
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextStrong,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(c.text, fontSize = 13.sp, color = TextStrong)
                                        Spacer(Modifier.height(2.dp))
                                        Text(formatFeedTime(c.createdAt), fontSize = 10.sp, color = TextMuted)
                                    }
                                    // Only the author's own comment is removable from
                                    // here. A salon moderating its own thread, or an
                                    // admin, does it from their console — putting that
                                    // power on a customer's screen would only confuse.
                                    if (c.userId == viewModel.userId) {
                                        IconButton(
                                            onClick  = { viewModel.deleteComment(c.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = strings.feedCommentDelete,
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (commentFailed) {
                            Text(
                                strings.feedCommentFailed,
                                fontSize = 12.sp, color = DeepRose,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value         = draft,
                                onValueChange = { if (it.length <= 300) draft = it },
                                placeholder   = { Text(strings.feedCommentHint, fontSize = 13.sp) },
                                singleLine    = true,
                                shape         = RoundedCornerShape(14.dp),
                                modifier      = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = RoseGold,
                                    unfocusedBorderColor = DashboardSurface,
                                )
                            )
                            IconButton(
                                onClick = {
                                    viewModel.postComment(post, draft)
                                    draft = ""
                                },
                                enabled = draft.isNotBlank()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = strings.feedCommentSend,
                                    tint = if (draft.isNotBlank()) RoseGold else TextMuted
                                )
                            }
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

/**
 * The availability rail: one ring per salon announcing a free chair today.
 *
 * Rings rather than cards because the row has to stay small — it sits above the
 * portfolio grid and must not push the actual work off the screen.
 */
@Composable
private fun StoryRow(stories: List<StoryDocument>, onOpen: (StoryDocument) -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            strings.storiesTitle,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold,
            modifier = Modifier.padding(start = 14.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(stories, key = { it.id }) { story ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(74.dp).clickable { onOpen(story) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            // The gradient ring is the "unseen story" cue people
                            // already know from elsewhere; reusing the brand
                            // gradient keeps it ours rather than a copy.
                            .background(Gradients.BrandRose)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(57.dp).clip(CircleShape).background(ElegantCream)
                        ) {
                            if (story.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model              = story.imageUrl,
                                    contentDescription = story.salonName,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.size(53.dp).clip(CircleShape)
                                )
                            } else {
                                Text(
                                    story.salonName.firstOrNull()?.uppercase() ?: "•",
                                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DeepRose
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        story.salonName,
                        fontSize = 11.sp, color = TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
