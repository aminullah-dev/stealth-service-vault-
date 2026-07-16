package com.safebeauty.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Theme-aware palette ──────────────────────────────────────────────────────────
// Every semantic color the app uses lives here so the whole palette can be swapped
// for a dark variant at runtime. The public top-level names below (RoseGold,
// DeepRose, …) are @Composable getters that read the palette DashboardTheme
// provides — so the hundreds of existing `DeepRose` / `Gradients.ScreenBg`
// references across the screens become dark-mode-aware with NO change at the call
// site. Only genuinely non-composable code (there is essentially none) would need
// to read LightPalette/DarkPalette directly.

data class Palette(
    val isDark: Boolean,
    val onPrimaryWhite: Color,
    val roseGold: Color,
    val deepRose: Color,
    val blushPink: Color,
    val softPurple: Color,
    val elegantCream: Color,
    val warmGold: Color,
    val dashboardSurface: Color,
    val chipActive: Color,
    val chipInactive: Color,
    val availableGreen: Color,
    val unavailableGrey: Color,
    val cardBorder: Color,
    val deeperRose: Color,
    val petalPink: Color,
    val lilacMist: Color,
    val softLavender: Color,
    val rosePetal: Color,
    // Semantic state colours (danger, warning, neutral secondary text, admin role).
    // Added so the admin screens stop hardcoding light-only literals and flip in
    // dark mode. Badge gold/green/silver reuse warmGold/availableGreen/unavailableGrey.
    val dangerRed: Color,
    val warningOrange: Color,
    val neutralGrey: Color,
    val adminPurple: Color,
    // Text hierarchy below the DeepRose headline level. The screens used to
    // hardcode ~7 different grey literals for these three roles; in dark mode a
    // dark grey on a dark background disappears, so they must flip with the theme.
    val textStrong: Color,   // emphasised body copy (was 0xFF444444 / 0xFF555555)
    val textMuted: Color,    // secondary copy      (was 0xFF666–999999 greys)
    val textFaint: Color,    // hints/placeholders  (was 0xFFAAAAAA)
    // Gradient colour stops, so brand gradients get a dark variant too.
    val brandRose: List<Color>,
    val brandRoseSoft: List<Color>,
    val screenBg: List<Color>,
    val softPink: List<Color>,
    val dreamy: List<Color>,
    val petal: List<Color>,
    val gold: List<Color>,
)

// The original, unchanged brand palette — light mode.
val LightPalette = Palette(
    isDark           = false,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFFB76E79),
    deepRose         = Color(0xFF8B3A47),
    blushPink        = Color(0xFFF9CBDA),
    softPurple       = Color(0xFF9C6B8A),
    elegantCream     = Color(0xFFFFF7FB),
    warmGold         = Color(0xFFD4A853),
    dashboardSurface = Color(0xFFFDEFF6),
    chipActive       = Color(0xFFB76E79),
    chipInactive     = Color(0xFFEECAD8),
    availableGreen   = Color(0xFF4CAF50),
    unavailableGrey  = Color(0xFF9E9E9E),
    cardBorder       = Color(0xFFF3D2E0),
    deeperRose       = Color(0xFF7A2F3D),
    petalPink        = Color(0xFFFCE4EF),
    lilacMist        = Color(0xFFE9D5F0),
    softLavender     = Color(0xFFF3E6F7),
    rosePetal        = Color(0xFFEBA9C0),
    dangerRed        = Color(0xFFC0392B),
    warningOrange    = Color(0xFFE67E22),
    neutralGrey      = Color(0xFF97878F),
    adminPurple      = Color(0xFF7B6FA0),
    textStrong       = Color(0xFF4A3E44),
    textMuted        = Color(0xFF8A7A81),
    textFaint        = Color(0xFFAA9AA1),
    brandRose        = listOf(Color(0xFFEBA9C0), Color(0xFFB76E79), Color(0xFF7A2F3D)),
    brandRoseSoft    = listOf(Color(0xFFD98CA8), Color(0xFF8B3A47)),
    screenBg         = listOf(Color(0xFFFFF7FB), Color(0xFFFDEAF3), Color(0xFFF5E7F6)),
    softPink         = listOf(Color(0xFFFCE4EF), Color(0xFFF9D3E1)),
    dreamy           = listOf(Color(0xFFFCE4EF), Color(0xFFE9D5F0)),
    petal            = listOf(Color(0xFFFFF0F6), Color(0xFFFCE4EF), Color(0xFFF7D9E7)),
    gold             = listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)),
)

// Dark mode. Colors used as TEXT on light backgrounds (deepRose, roseGold) become
// light so they stay readable; backgrounds/surfaces become deep warm plums. First
// pass — we tune specific values by eye once it's on a device.
val DarkPalette = Palette(
    isDark           = true,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    // A medium rose: deep enough that white button text reads (buttonColors use
    // roseGold as a container), light enough to stay legible as accent text/icons
    // on the dark background.
    roseGold         = Color(0xFFC56E7E),
    deepRose         = Color(0xFFF2D6DE),
    blushPink        = Color(0xFF3A2A31),
    softPurple       = Color(0xFFC4A0BA),
    elegantCream     = Color(0xFF15100F),
    warmGold         = Color(0xFFE0B968),
    dashboardSurface = Color(0xFF241A20),
    chipActive       = Color(0xFFC77E8A),
    chipInactive     = Color(0xFF33262D),
    availableGreen   = Color(0xFF5CC462),
    unavailableGrey  = Color(0xFF8A8A8A),
    cardBorder       = Color(0xFF3A2C33),
    deeperRose       = Color(0xFF5A2029),
    petalPink        = Color(0xFF2E2028),
    lilacMist        = Color(0xFF2A2233),
    softLavender     = Color(0xFF241E2C),
    rosePetal        = Color(0xFFC77E90),
    dangerRed        = Color(0xFFE06C5E),
    warningOrange    = Color(0xFFE9975A),
    neutralGrey      = Color(0xFFA895A0),
    adminPurple      = Color(0xFFB6A0D0),
    textStrong       = Color(0xFFDCCDD4),
    textMuted        = Color(0xFFB79FA9),
    textFaint        = Color(0xFF8F7B84),
    brandRose        = listOf(Color(0xFF8B4A57), Color(0xFF6E3A45), Color(0xFF4A222B)),
    brandRoseSoft    = listOf(Color(0xFF7A4653), Color(0xFF5A2E38)),
    screenBg         = listOf(Color(0xFF15100F), Color(0xFF1A1218), Color(0xFF17141F)),
    softPink         = listOf(Color(0xFF33262D), Color(0xFF2C2028)),
    dreamy           = listOf(Color(0xFF2E2028), Color(0xFF2A2233)),
    petal            = listOf(Color(0xFF241A20), Color(0xFF2E2028), Color(0xFF201820)),
    gold             = listOf(Color(0xFFCBA26A), Color(0xFFB08430)),
)

// Provided by DashboardTheme; defaults to light for any composable rendered
// outside the theme (e.g. @Preview).
val LocalPalette = staticCompositionLocalOf { LightPalette }

// ── Public colour names (theme-aware) ────────────────────────────────────────────
// Same names the whole codebase already imports and uses. Each reads the current
// palette, so a value automatically flips in dark mode.
val OnPrimaryWhite:   Color @Composable get() = LocalPalette.current.onPrimaryWhite
val RoseGold:         Color @Composable get() = LocalPalette.current.roseGold
val DeepRose:         Color @Composable get() = LocalPalette.current.deepRose
val BlushPink:        Color @Composable get() = LocalPalette.current.blushPink
val SoftPurple:       Color @Composable get() = LocalPalette.current.softPurple
val ElegantCream:     Color @Composable get() = LocalPalette.current.elegantCream
val WarmGold:         Color @Composable get() = LocalPalette.current.warmGold
val DashboardSurface: Color @Composable get() = LocalPalette.current.dashboardSurface
val ChipActive:       Color @Composable get() = LocalPalette.current.chipActive
val ChipInactive:     Color @Composable get() = LocalPalette.current.chipInactive
val AvailableGreen:   Color @Composable get() = LocalPalette.current.availableGreen
val UnavailableGrey:  Color @Composable get() = LocalPalette.current.unavailableGrey
val CardBorder:       Color @Composable get() = LocalPalette.current.cardBorder
val DeeperRose:       Color @Composable get() = LocalPalette.current.deeperRose
val PetalPink:        Color @Composable get() = LocalPalette.current.petalPink
val LilacMist:        Color @Composable get() = LocalPalette.current.lilacMist
val SoftLavender:     Color @Composable get() = LocalPalette.current.softLavender
val RosePetal:        Color @Composable get() = LocalPalette.current.rosePetal
val DangerRed:        Color @Composable get() = LocalPalette.current.dangerRed
val WarningOrange:    Color @Composable get() = LocalPalette.current.warningOrange
val NeutralGrey:      Color @Composable get() = LocalPalette.current.neutralGrey
val AdminPurple:      Color @Composable get() = LocalPalette.current.adminPurple
val TextStrong:       Color @Composable get() = LocalPalette.current.textStrong
val TextMuted:        Color @Composable get() = LocalPalette.current.textMuted
val TextFaint:        Color @Composable get() = LocalPalette.current.textFaint

// ── Brand gradients (theme-aware) ────────────────────────────────────────────────
// Same object/name the screens use. Each getter rebuilds the brush from the
// current palette's stops, so gradients (including the full-screen ScreenBg
// background) darken in dark mode.
object Gradients {
    val BrandRose:     Brush @Composable get() = Brush.linearGradient(LocalPalette.current.brandRose)
    val BrandRoseSoft: Brush @Composable get() = Brush.linearGradient(LocalPalette.current.brandRoseSoft)
    val ScreenBg:      Brush @Composable get() = Brush.verticalGradient(LocalPalette.current.screenBg)
    val SoftPink:      Brush @Composable get() = Brush.linearGradient(LocalPalette.current.softPink)
    val Dreamy:        Brush @Composable get() = Brush.linearGradient(LocalPalette.current.dreamy)
    val Petal:         Brush @Composable get() = Brush.radialGradient(LocalPalette.current.petal)
    val Gold:          Brush @Composable get() = Brush.linearGradient(LocalPalette.current.gold)
}
