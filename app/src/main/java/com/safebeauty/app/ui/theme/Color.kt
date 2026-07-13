package com.safebeauty.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val OnPrimaryWhite    = Color(0xFFFFFFFF)

// ── Dashboard palette ──────────────────────────────────────────────────────────
val RoseGold          = Color(0xFFB76E79)
val DeepRose          = Color(0xFF8B3A47)
val BlushPink         = Color(0xFFF9CBDA)
val SoftPurple        = Color(0xFF9C6B8A)
val ElegantCream      = Color(0xFFFFF7FB)   // soft pink-white — warmer, more feminine
val WarmGold          = Color(0xFFD4A853)
val DashboardSurface  = Color(0xFFFDEFF6)
val ChipActive        = Color(0xFFB76E79)
val ChipInactive      = Color(0xFFEECAD8)
val AvailableGreen    = Color(0xFF4CAF50)
val UnavailableGrey   = Color(0xFF9E9E9E)
val CardBorder        = Color(0xFFF3D2E0)

// A slightly deeper rose used as the far stop of the brand gradient, so
// buttons/headers read as a rich rose→plum sweep rather than one flat tone.
val DeeperRose        = Color(0xFF7A2F3D)

// ── Feminine accents ─────────────────────────────────────────────────────────────
// Soft petal pink + lilac to lend a dreamy, girly warmth to hero areas, chips,
// and highlights alongside the rose-gold brand core.
val PetalPink         = Color(0xFFFCE4EF)
val LilacMist         = Color(0xFFE9D5F0)
val SoftLavender      = Color(0xFFF3E6F7)
val RosePetal         = Color(0xFFEBA9C0)

// ── Brand gradients ──────────────────────────────────────────────────────────────
// Centralized so every surface pulls from the same set and the app reads as one
// designed system. Use BrandRose for primary buttons/headers, ScreenBg as the
// full-screen background (a soft blush→lilac wash), SoftPink for gentle chips,
// and Dreamy/Petal for feminine hero and decorative surfaces.
object Gradients {
    val BrandRose = Brush.linearGradient(listOf(RosePetal, RoseGold, DeeperRose))
    val BrandRoseSoft = Brush.linearGradient(listOf(Color(0xFFD98CA8), DeepRose))
    val ScreenBg = Brush.verticalGradient(
        listOf(Color(0xFFFFF7FB), Color(0xFFFDEAF3), Color(0xFFF5E7F6))
    )
    val SoftPink = Brush.linearGradient(listOf(Color(0xFFFCE4EF), Color(0xFFF9D3E1)))
    val Dreamy = Brush.linearGradient(listOf(PetalPink, LilacMist))
    val Petal = Brush.radialGradient(listOf(Color(0xFFFFF0F6), PetalPink, Color(0xFFF7D9E7)))
    val Gold = Brush.linearGradient(listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)))
}
