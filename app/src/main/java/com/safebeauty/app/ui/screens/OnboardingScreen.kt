package com.safebeauty.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.ui.components.BrandBackground
import com.safebeauty.app.ui.components.GradientButton
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.Gradients
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextMuted

private data class OnboardingSlide(
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

/**
 * First-launch, one-time intro (see OnboardingRepository/OnboardingViewModel —
 * gated by a persisted flag so returning users never see this again).
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val strings = LocalStrings.current
    val slides = remember(strings) {
        listOf(
            OnboardingSlide(Icons.Default.Search, strings.onboardingTitle1, strings.onboardingSubtitle1),
            OnboardingSlide(Icons.Default.CalendarMonth, strings.onboardingTitle2, strings.onboardingSubtitle2),
            OnboardingSlide(Icons.Default.VerifiedUser, strings.onboardingTitle3, strings.onboardingSubtitle3)
        )
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val isLast = page == slides.lastIndex

    BrandBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLast) {
                    TextButton(onClick = onFinish) {
                        Text(strings.onboardingSkip, color = RoseGold, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Crossfade(targetState = page, label = "onboarding_slide") { i ->
                    val slide = slides[i]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Gradients.SoftPink)
                        ) {
                            Icon(slide.icon, null, tint = DeepRose, modifier = Modifier.size(56.dp))
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(
                            slide.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepRose,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            slide.subtitle,
                            fontSize = 14.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                slides.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == page) 22.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == page) RoseGold else BlushPink)
                    )
                }
            }

            GradientButton(
                text = if (isLast) strings.onboardingGetStarted else strings.onboardingNext,
                onClick = { if (isLast) onFinish() else page++ }
            )
        }
    }
}
