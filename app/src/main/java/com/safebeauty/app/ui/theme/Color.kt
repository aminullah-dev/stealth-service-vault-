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

/**
 * The two colour families a user can choose between. The names describe the
 * feeling, not the hex: ROSE is the original warm rose-and-gold, LAVENDER is a
 * softer violet for people who find the rose too warm.
 *
 * Carried inside [Palette] rather than in its own CompositionLocal so that the
 * ~27 screens which call `DashboardTheme { }` with no arguments inherit the
 * brand exactly the way they already inherit light/dark — no call site changes.
 */
enum class AppBrand { ROSE, LAVENDER, SAGE }

data class Palette(
    val brand: AppBrand,
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
val RoseLightPalette = Palette(
    brand            = AppBrand.ROSE,
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
val RoseDarkPalette = Palette(
    brand            = AppBrand.ROSE,
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

// ── Lavender ────────────────────────────────────────────────────────────────────
// The alternative family: the same soft, feminine register as the rose, shifted
// from warm pink to cool violet. Structurally identical to the rose palettes —
// every field is filled and every role keeps its meaning — so a screen written
// against `DeepRose` or `Gradients.ScreenBg` renders correctly in either family
// without knowing which one is active.
//
// The gold accents (warmGold, the Gold gradient) stay gold on purpose: they mark
// ratings and badges, which read as "valuable" in gold and merely decorative in
// violet, and keeping them constant ties the two families to one brand.

val LavenderLightPalette = Palette(
    brand            = AppBrand.LAVENDER,
    isDark           = false,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFF8E6FB0),   // primary accent
    deepRose         = Color(0xFF56347A),   // headline ink
    blushPink        = Color(0xFFE4D7F5),
    softPurple       = Color(0xFF7C6BA8),
    elegantCream     = Color(0xFFFBF7FF),   // page background
    warmGold         = Color(0xFFD4A853),
    dashboardSurface = Color(0xFFF3EBFC),
    chipActive       = Color(0xFF8E6FB0),
    chipInactive     = Color(0xFFE0D2F2),
    availableGreen   = Color(0xFF4CAF50),
    unavailableGrey  = Color(0xFF9E9E9E),
    cardBorder       = Color(0xFFE6DAF5),
    deeperRose       = Color(0xFF452A63),
    petalPink        = Color(0xFFF0E6FB),
    lilacMist        = Color(0xFFE3D6F3),
    softLavender     = Color(0xFFF2EAFB),
    rosePetal        = Color(0xFFB79AD8),
    dangerRed        = Color(0xFFC0392B),
    warningOrange    = Color(0xFFE67E22),
    neutralGrey      = Color(0xFF8C86A0),
    adminPurple      = Color(0xFF6E5EA0),
    textStrong       = Color(0xFF423951),
    textMuted        = Color(0xFF7B7290),
    textFaint        = Color(0xFFA79FB5),
    brandRose        = listOf(Color(0xFFC4A9E0), Color(0xFF8E6FB0), Color(0xFF56347A)),
    brandRoseSoft    = listOf(Color(0xFFB18FD0), Color(0xFF6B4A93)),
    screenBg         = listOf(Color(0xFFFBF7FF), Color(0xFFF5EDFC), Color(0xFFEFE8FA)),
    softPink         = listOf(Color(0xFFF0E6FB), Color(0xFFE6D8F7)),
    dreamy           = listOf(Color(0xFFF0E6FB), Color(0xFFE0D2F2)),
    petal            = listOf(Color(0xFFF7F2FE), Color(0xFFF0E6FB), Color(0xFFE7DAF8)),
    gold             = listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)),
)

val LavenderDarkPalette = Palette(
    brand            = AppBrand.LAVENDER,
    isDark           = true,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    // Deep enough that white button text reads on it, light enough to stay
    // legible as accent text on the dark background — the same balance the rose
    // dark palette strikes.
    roseGold         = Color(0xFF9B7BC4),
    deepRose         = Color(0xFFE6DAF5),   // headline ink, inverted for dark
    blushPink        = Color(0xFF352B4D),
    softPurple       = Color(0xFFB6A5D6),
    elegantCream     = Color(0xFF120F1A),
    warmGold         = Color(0xFFE0B968),
    dashboardSurface = Color(0xFF1E1830),
    chipActive       = Color(0xFF9B7BC4),
    chipInactive     = Color(0xFF2A2340),
    availableGreen   = Color(0xFF5CC462),
    unavailableGrey  = Color(0xFF8A8A8A),
    cardBorder       = Color(0xFF332A4A),
    deeperRose       = Color(0xFF4A3570),
    petalPink        = Color(0xFF272038),
    lilacMist        = Color(0xFF2B2342),
    softLavender     = Color(0xFF231D36),
    rosePetal        = Color(0xFFA98BC9),
    dangerRed        = Color(0xFFE06C5E),
    warningOrange    = Color(0xFFE9975A),
    neutralGrey      = Color(0xFFA096B5),
    adminPurple      = Color(0xFFB6A0D0),
    textStrong       = Color(0xFFD8CFE6),
    textMuted        = Color(0xFFAFA4C4),
    textFaint        = Color(0xFF867C9C),
    brandRose        = listOf(Color(0xFF6E56A0), Color(0xFF56417E), Color(0xFF3A2A5A)),
    brandRoseSoft    = listOf(Color(0xFF644E93), Color(0xFF432F6B)),
    screenBg         = listOf(Color(0xFF120F1A), Color(0xFF171223), Color(0xFF1A1428)),
    softPink         = listOf(Color(0xFF2A2340), Color(0xFF241E36)),
    dreamy           = listOf(Color(0xFF272038), Color(0xFF2B2342)),
    petal            = listOf(Color(0xFF1E1830), Color(0xFF272038), Color(0xFF1C1730)),
    gold             = listOf(Color(0xFFCBA26A), Color(0xFFB08430)),
)

// ── Sage ────────────────────────────────────────────────────────────────────────
// The third family: a soft green that reads calm and spa-like rather than
// clinical. Same structure and the same roles as the other two, so no screen
// needs to know it exists.
//
// The one place green needed care: availableGreen and the Verified badge are
// also green, and in a green theme a "verified" badge that matches the accent
// stops signalling anything. Both are pushed toward a deeper, cooler green than
// the sage accent so they still read as a distinct state rather than decoration.

val SageLightPalette = Palette(
    brand            = AppBrand.SAGE,
    isDark           = false,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFF6E9080),   // primary accent — muted sage
    deepRose         = Color(0xFF2F4F42),   // headline ink — deep pine
    blushPink        = Color(0xFFD4E6DD),
    softPurple       = Color(0xFF6E8C9B),
    elegantCream     = Color(0xFFF7FBF8),
    warmGold         = Color(0xFFD4A853),
    dashboardSurface = Color(0xFFEAF4EE),
    chipActive       = Color(0xFF6E9080),
    chipInactive     = Color(0xFFD3E5DB),
    // Deeper and cooler than the sage accent so "available" stays a state.
    availableGreen   = Color(0xFF2E7D32),
    unavailableGrey  = Color(0xFF9E9E9E),
    cardBorder       = Color(0xFFD8E8DF),
    deeperRose       = Color(0xFF24402F),
    petalPink        = Color(0xFFE7F3EC),
    lilacMist        = Color(0xFFD8E8DF),
    softLavender     = Color(0xFFEFF7F2),
    rosePetal        = Color(0xFF93B5A4),
    dangerRed        = Color(0xFFC0392B),
    warningOrange    = Color(0xFFE67E22),
    neutralGrey      = Color(0xFF819088),
    adminPurple      = Color(0xFF5F7F9B),
    textStrong       = Color(0xFF37453E),
    textMuted        = Color(0xFF74847C),
    textFaint        = Color(0xFF9DAAA3),
    brandRose        = listOf(Color(0xFFA6C9B6), Color(0xFF6E9080), Color(0xFF2F4F42)),
    brandRoseSoft    = listOf(Color(0xFF8FB9A3), Color(0xFF4C6E5D)),
    screenBg         = listOf(Color(0xFFF7FBF8), Color(0xFFEDF6F1), Color(0xFFE7F2EC)),
    softPink         = listOf(Color(0xFFE7F3EC), Color(0xFFD9EAE1)),
    dreamy           = listOf(Color(0xFFE7F3EC), Color(0xFFD8E8DF)),
    petal            = listOf(Color(0xFFF2F9F5), Color(0xFFE7F3EC), Color(0xFFDCEDE4)),
    gold             = listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)),
)

val SageDarkPalette = Palette(
    brand            = AppBrand.SAGE,
    isDark           = true,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFF6F9B85),
    deepRose         = Color(0xFFD9EBE1),   // headline ink, inverted for dark
    blushPink        = Color(0xFF263A31),
    softPurple       = Color(0xFF8FAEBD),
    elegantCream     = Color(0xFF0E1512),
    warmGold         = Color(0xFFE0B968),
    dashboardSurface = Color(0xFF17241E),
    chipActive       = Color(0xFF6F9B85),
    chipInactive     = Color(0xFF21332B),
    // Brighter than the accent so it still reads as a state on a green ground.
    availableGreen   = Color(0xFF7BD98A),
    unavailableGrey  = Color(0xFF8A8A8A),
    cardBorder       = Color(0xFF2A3D34),
    deeperRose       = Color(0xFF2C4A3B),
    petalPink        = Color(0xFF1D2C25),
    lilacMist        = Color(0xFF223129),
    softLavender     = Color(0xFF1A2721),
    rosePetal        = Color(0xFF87AF9A),
    dangerRed        = Color(0xFFE06C5E),
    warningOrange    = Color(0xFFE9975A),
    neutralGrey      = Color(0xFF95A69D),
    adminPurple      = Color(0xFF9DB6C7),
    textStrong       = Color(0xFFCEDDD5),
    textMuted        = Color(0xFFA0B2A8),
    textFaint        = Color(0xFF7C8C84),
    brandRose        = listOf(Color(0xFF4E7462), Color(0xFF3B5A4B), Color(0xFF273D32)),
    brandRoseSoft    = listOf(Color(0xFF466A58), Color(0xFF2E4839)),
    screenBg         = listOf(Color(0xFF0E1512), Color(0xFF121C17), Color(0xFF15201A)),
    softPink         = listOf(Color(0xFF21332B), Color(0xFF1C2A23)),
    dreamy           = listOf(Color(0xFF1D2C25), Color(0xFF223129)),
    petal            = listOf(Color(0xFF17241E), Color(0xFF1D2C25), Color(0xFF16211C)),
    gold             = listOf(Color(0xFFCBA26A), Color(0xFFB08430)),
)

// Provided by DashboardTheme; defaults to light for any composable rendered
// outside the theme (e.g. @Preview).
val LocalPalette = staticCompositionLocalOf { RoseLightPalette }

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
