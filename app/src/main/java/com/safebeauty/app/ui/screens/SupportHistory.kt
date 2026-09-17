package com.safebeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.data.firebase.ChatMessage
import com.safebeauty.app.data.firebase.SupportHistoryDocument
import com.safebeauty.app.data.firebase.canRate
import com.safebeauty.app.ui.theme.CardBorder
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DangerRed
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextFaint
import com.safebeauty.app.ui.theme.TextMuted
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.ui.theme.WarmGold
import com.safebeauty.app.util.formatIsolated
import com.safebeauty.app.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private const val RATING_COMMENT_MAX = 500

/**
 * Five stars. Interactive when [onRate] is given — each star is its own 48dp
 * target announced as "N stars" — otherwise a compact read-only row announced
 * once as a whole.
 */
@Composable
fun StarRatingRow(
    rating: Int,
    onRate: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    starSize: Dp = 32.dp
) {
    val strings = LocalStrings.current
    if (onRate != null) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = modifier
        ) {
            (1..5).forEach { star ->
                IconButton(onClick = { onRate(star) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector        = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = strings.ratingStars(star),
                        tint               = WarmGold,
                        modifier           = Modifier.size(starSize)
                    )
                }
            }
        }
    } else {
        val label = strings.ratingStars(rating)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = modifier.semantics { contentDescription = label }
        ) {
            (1..5).forEach { star ->
                Icon(
                    imageVector        = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint               = WarmGold,
                    modifier           = Modifier.size(starSize)
                )
            }
        }
    }
}

/**
 * "How was your support conversation?" — stars, optional comment, Submit.
 * Shows a thank-you once [state] says the rating was saved; keeps the card (with
 * an error line) when saving failed. [onNotNow] is omitted where hiding the card
 * makes no sense (a transcript).
 */
@Composable
fun SupportRatingCard(
    entry: SupportHistoryDocument,
    state: ChatViewModel.RatingState?,
    onSubmit: (stars: Int, comment: String) -> Unit,
    onNotNow: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var stars   by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var comment by rememberSaveable(entry.id) { mutableStateOf("") }
    val submitting = state?.submitting == true

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DashboardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        if (state?.thanked == true) {
            Icon(Icons.Filled.CheckCircle, null, tint = RoseGold, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            if (stars > 0) {
                StarRatingRow(rating = stars, onRate = null, starSize = 22.dp)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                strings.ratingThanks,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = DeepRose,
                textAlign  = TextAlign.Center
            )
            return@Column
        }

        Text(
            strings.rateConversationTitle,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            color      = DeepRose,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        StarRatingRow(
            rating = stars,
            onRate = { n -> if (!submitting) stars = n },
            starSize = 34.dp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value         = comment,
            onValueChange = { comment = it.take(RATING_COMMENT_MAX) },
            placeholder   = { Text(strings.rateConversationCommentHint, fontSize = 13.sp, color = TextFaint) },
            enabled       = !submitting,
            minLines      = 2,
            maxLines      = 4,
            shape         = RoundedCornerShape(12.dp),
            modifier      = Modifier.fillMaxWidth(),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = RoseGold,
                unfocusedBorderColor = ChipInactive,
                cursorColor          = RoseGold
            )
        )
        if (state?.failed == true) {
            Spacer(Modifier.height(8.dp))
            Text(strings.ratingSubmitFailed, fontSize = 13.sp, color = DangerRed, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            if (onNotNow != null) {
                TextButton(
                    onClick  = onNotNow,
                    enabled  = !submitting,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(strings.notNow, color = TextMuted, fontSize = 14.sp)
                }
            }
            Button(
                onClick  = { onSubmit(stars, comment.trim().take(RATING_COMMENT_MAX)) },
                enabled  = stars in 1..5 && !submitting,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = DeepRose),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                if (submitting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(strings.submitRating, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

/** The list of closed support conversations, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportHistorySheet(
    history: List<SupportHistoryDocument>?,
    onDismiss: () -> Unit,
    onOpen: (SupportHistoryDocument) -> Unit
) {
    val strings    = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFmt    = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = ElegantCream
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.History, null, tint = RoseGold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.supportHistory, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DeepRose)
            }
            Spacer(Modifier.height(8.dp))
            when {
                history == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RoseGold)
                }
                history.isEmpty() -> Text(
                    strings.supportHistoryEmpty,
                    fontSize  = 14.sp,
                    color     = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)
                )
                else -> LazyColumn(
                    contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history, key = { it.id }) { entry ->
                        SupportHistoryRow(
                            entry   = entry,
                            dateStr = dateFmt.formatIsolated(entry.closedAt),
                            onClick = { onOpen(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportHistoryRow(
    entry: SupportHistoryDocument,
    dateStr: String,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DashboardSurface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                dateStr,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = DeepRose,
                modifier   = Modifier.weight(1f)
            )
            Text(strings.supportMessagesCount(entry.messageCount), fontSize = 12.sp, color = TextFaint)
        }
        if (entry.lastMessage.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                entry.lastMessage,
                fontSize = 13.sp,
                color    = TextStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        if (entry.rating in 1..5) {
            StarRatingRow(rating = entry.rating, onRate = null, starSize = 16.dp)
        } else {
            Text(strings.notRated, fontSize = 12.sp, color = TextMuted)
        }
    }
}

/**
 * A closed conversation, read-only: its messages, then either the rating it was
 * given or — if it can still be rated — the rating card.
 */
@Composable
fun SupportTranscriptContent(
    entry: SupportHistoryDocument?,
    transcript: ChatViewModel.TranscriptState,
    ratingState: ChatViewModel.RatingState?,
    myUserId: String,
    contentPadding: PaddingValues,
    onSubmitRating: (historyId: String, stars: Int, comment: String) -> Unit
) {
    val strings   = LocalStrings.current
    val listState = rememberLazyListState()
    // Open at the end, where the conversation closed and the rating sits.
    LaunchedEffect(transcript.historyId, transcript.loading) {
        if (!transcript.loading && transcript.messages.isNotEmpty()) {
            listState.scrollToItem(transcript.messages.size)
        }
    }
    when {
        transcript.loading -> Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RoseGold)
        }
        else -> LazyColumn(
            state               = listState,
            contentPadding      = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.fillMaxSize()
        ) {
            if (transcript.failed) {
                item {
                    Text(
                        strings.actionFailedTitle,
                        fontSize  = 14.sp,
                        color     = DangerRed,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            }
            items(transcript.messages, key = { it.id.ifBlank { it.timestamp.toString() } }) { msg: ChatMessage ->
                ChatBubble(message = msg, isMine = msg.senderId == myUserId)
            }
            if (entry != null) {
                item(key = "rating_${entry.id}") {
                    Spacer(Modifier.height(8.dp))
                    when {
                        entry.canRate() || ratingState?.thanked == true -> SupportRatingCard(
                            entry    = entry,
                            state    = ratingState,
                            onSubmit = { stars, comment -> onSubmitRating(entry.id, stars, comment) },
                            onNotNow = null
                        )
                        entry.rating in 1..5 -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            StarRatingRow(rating = entry.rating, onRate = null, starSize = 22.dp)
                            if (entry.ratingComment.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    entry.ratingComment,
                                    fontSize  = 13.sp,
                                    color     = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
