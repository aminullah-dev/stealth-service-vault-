package com.safebeauty.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.util.HintPrefs

/**
 * A one-line hint that a screen can be swiped, shown a few times and then never
 * again.
 *
 * The hand icon drifts left and right on a slow loop. That is the whole point of
 * the component: the sentence says what to do and the movement shows it, which
 * is how you teach a gesture to someone who has never been told a screen could
 * have one. The drift is small and slow — 6dp over a second and a half — so it
 * reads as an invitation rather than something demanding attention.
 *
 * [hintKey] identifies the hint, not the screen: two screens teaching the same
 * gesture should share a key so the user is taught once.
 */
@Composable
fun SwipeHint(
    text: String,
    hintKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Decided once, on entry. Re-reading it every recomposition would let the
    // hint vanish mid-frame the moment recordShown pushed the count to three.
    var visible by remember(hintKey) { mutableStateOf(HintPrefs.shouldShow(context, hintKey)) }

    LaunchedEffect(hintKey) {
        if (visible) HintPrefs.recordShown(context, hintKey)
    }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(320)) + expandVertically(tween(320)),
        exit    = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        modifier = modifier,
    ) {
        val drift = rememberInfiniteTransition(label = "swipeHint")
        val offset by drift.animateFloat(
            initialValue  = -6f,
            targetValue   = 6f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "drift",
        )

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BlushPink.copy(alpha = 0.55f))
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                Icons.Default.SwipeLeft,
                contentDescription = null,
                tint = RoseGold,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { translationX = offset },
            )
            Text(
                text       = text,
                fontSize   = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                color      = DeepRose,
                modifier   = Modifier.weight(1f),
            )
            IconButton(
                onClick = { visible = false; HintPrefs.dismiss(context, hintKey) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = RoseGold,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
