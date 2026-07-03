package com.safebeauty.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val OnPrimaryWhite    = Color(0xFFFFFFFF)

// ── Dashboard palette ──────────────────────────────────────────────────────────
val RoseGold          = Color(0xFFB76E79)
val DeepRose          = Color(0xFF8B3A47)
val BlushPink         = Color(0xFFF8C8D4)
val SoftPurple        = Color(0xFF9C6B8A)
val ElegantCream      = Color(0xFFFFF8F0)
val WarmGold          = Color(0xFFD4A853)
val DashboardSurface  = Color(0xFFFDF0F5)
val ChipActive        = Color(0xFFB76E79)
val ChipInactive      = Color(0xFFE8C8D0)
val AvailableGreen    = Color(0xFF4CAF50)
val UnavailableGrey   = Color(0xFF9E9E9E)
val CardBorder        = Color(0xFFF0D0D8)

// A slightly deeper rose used as the far stop of the brand gradient, so
// buttons/headers read as a rich rose→plum sweep rather than one flat tone.
val DeeperRose        = Color(0xFF7A2F3D)

// ── Brand gradients ──────────────────────────────────────────────────────────────
// Centralized so every surface pulls from the same set and the app reads as one
// designed system. Use BrandRose for primary buttons/headers, ScreenBg as the
// full-screen background (a soft cream wash that lifts flat screens), and
// SoftPink for gentle chips/highlights.
object Gradients {
    val BrandRose = Brush.linearGradient(listOf(RoseGold, DeeperRose))
    val BrandRoseSoft = Brush.linearGradient(listOf(Color(0xFFC98490), DeepRose))
    val ScreenBg = Brush.verticalGradient(
        listOf(Color(0xFFFFFBF6), Color(0xFFFDF1F1), Color(0xFFFBE9EC))
    )
    val SoftPink = Brush.linearGradient(listOf(Color(0xFFFCE4E9), Color(0xFFF8D3DC)))
    val Gold = Brush.linearGradient(listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)))
}
