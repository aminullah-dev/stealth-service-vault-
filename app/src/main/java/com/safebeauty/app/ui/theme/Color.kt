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
enum class AppBrand { ROSE, LAVENDER, SAGE, OCEAN, HONEY, MAROON }

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

// ── Ocean ───────────────────────────────────────────────────────────────────────
// A calm blue. The coolest of the families and the only one that risks reading
// "corporate", so it is kept soft and slightly grey-blue rather than a saturated
// tech blue, and the gold accents do the warming.

val OceanLightPalette = Palette(
    brand            = AppBrand.OCEAN,
    isDark           = false,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFF5E86A8),
    deepRose         = Color(0xFF294863),
    blushPink        = Color(0xFFD5E4F0),
    softPurple       = Color(0xFF6E7FA8),
    elegantCream     = Color(0xFFF7FAFD),
    warmGold         = Color(0xFFD4A853),
    dashboardSurface = Color(0xFFE9F1F8),
    chipActive       = Color(0xFF5E86A8),
    chipInactive     = Color(0xFFD4E3EF),
    availableGreen   = Color(0xFF3B9C46),
    unavailableGrey  = Color(0xFF9E9E9E),
    cardBorder       = Color(0xFFD8E6F1),
    deeperRose       = Color(0xFF1E3950),
    petalPink        = Color(0xFFE6F0F8),
    lilacMist        = Color(0xFFD8E6F1),
    softLavender     = Color(0xFFEEF5FA),
    rosePetal        = Color(0xFF8FB0CB),
    dangerRed        = Color(0xFFC0392B),
    warningOrange    = Color(0xFFE67E22),
    neutralGrey      = Color(0xFF7D8A97),
    adminPurple      = Color(0xFF6C6FA6),
    textStrong       = Color(0xFF35424E),
    textMuted        = Color(0xFF71808E),
    textFaint        = Color(0xFF9BA8B4),
    brandRose        = listOf(Color(0xFF9CC0DA), Color(0xFF5E86A8), Color(0xFF294863)),
    brandRoseSoft    = listOf(Color(0xFF83AECD), Color(0xFF3F6485)),
    screenBg         = listOf(Color(0xFFF7FAFD), Color(0xFFEDF4FA), Color(0xFFE7F0F8)),
    softPink         = listOf(Color(0xFFE6F0F8), Color(0xFFD7E7F3)),
    dreamy           = listOf(Color(0xFFE6F0F8), Color(0xFFD8E6F1)),
    petal            = listOf(Color(0xFFF2F8FC), Color(0xFFE6F0F8), Color(0xFFDAEAF5)),
    gold             = listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)),
)

val OceanDarkPalette = Palette(
    brand            = AppBrand.OCEAN,
    isDark           = true,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFF6791B5),
    deepRose         = Color(0xFFD9E7F2),
    blushPink        = Color(0xFF23364A),
    softPurple       = Color(0xFF93A4C6),
    elegantCream     = Color(0xFF0D131A),
    warmGold         = Color(0xFFE0B968),
    dashboardSurface = Color(0xFF16212D),
    chipActive       = Color(0xFF6791B5),
    chipInactive     = Color(0xFF1F2E3D),
    availableGreen   = Color(0xFF5CC462),
    unavailableGrey  = Color(0xFF8A8A8A),
    cardBorder       = Color(0xFF283847),
    deeperRose       = Color(0xFF2A4762),
    petalPink        = Color(0xFF1B2733),
    lilacMist        = Color(0xFF202E3D),
    softLavender     = Color(0xFF19232F),
    rosePetal        = Color(0xFF80A5C3),
    dangerRed        = Color(0xFFE06C5E),
    warningOrange    = Color(0xFFE9975A),
    neutralGrey      = Color(0xFF94A2B0),
    adminPurple      = Color(0xFFA3A7D6),
    textStrong       = Color(0xFFCBD8E4),
    textMuted        = Color(0xFF9DACBA),
    textFaint        = Color(0xFF77868F),
    brandRose        = listOf(Color(0xFF456C8D), Color(0xFF35536E), Color(0xFF223749)),
    brandRoseSoft    = listOf(Color(0xFF3E6383), Color(0xFF294156)),
    screenBg         = listOf(Color(0xFF0D131A), Color(0xFF111A24), Color(0xFF141E29)),
    softPink         = listOf(Color(0xFF1F2E3D), Color(0xFF1A2733)),
    dreamy           = listOf(Color(0xFF1B2733), Color(0xFF202E3D)),
    petal            = listOf(Color(0xFF16212D), Color(0xFF1B2733), Color(0xFF151F2A)),
    gold             = listOf(Color(0xFFCBA26A), Color(0xFFB08430)),
)

// ── Honey ───────────────────────────────────────────────────────────────────────
// Warm cream and gold: the closest to the original rose in temperature but with
// the pink taken out, for anyone who wants warmth without a colour that reads as
// explicitly girlish. Its accent IS gold, so warmGold is nudged darker than the
// accent to keep rating stars and badges distinguishable from ordinary chrome —
// the same problem the sage family had with green.

val HoneyLightPalette = Palette(
    brand            = AppBrand.HONEY,
    isDark           = false,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFFB08A4A),
    deepRose         = Color(0xFF6B4E1E),
    blushPink        = Color(0xFFF2E4C8),
    softPurple       = Color(0xFF9C8468),
    elegantCream     = Color(0xFFFFFBF3),
    warmGold         = Color(0xFF8A6A1F),   // darker than the accent, so stars still read
    dashboardSurface = Color(0xFFFAF2E2),
    chipActive       = Color(0xFFB08A4A),
    chipInactive     = Color(0xFFEEE1C9),
    availableGreen   = Color(0xFF3B8C46),
    unavailableGrey  = Color(0xFF9E9E9E),
    cardBorder       = Color(0xFFEDE0C6),
    deeperRose       = Color(0xFF553C14),
    petalPink        = Color(0xFFF8EEDA),
    lilacMist        = Color(0xFFEDE0C6),
    softLavender     = Color(0xFFFBF4E6),
    rosePetal        = Color(0xFFD3B37A),
    dangerRed        = Color(0xFFC0392B),
    warningOrange    = Color(0xFFCF6F16),
    neutralGrey      = Color(0xFF938872),
    adminPurple      = Color(0xFF8A7BA0),
    textStrong       = Color(0xFF4A4032),
    textMuted        = Color(0xFF867B67),
    textFaint        = Color(0xFFAFA48D),
    brandRose        = listOf(Color(0xFFE0C68C), Color(0xFFB08A4A), Color(0xFF6B4E1E)),
    brandRoseSoft    = listOf(Color(0xFFCDAE6E), Color(0xFF8A6733)),
    screenBg         = listOf(Color(0xFFFFFBF3), Color(0xFFFBF4E6), Color(0xFFF7EEDC)),
    softPink         = listOf(Color(0xFFF8EEDA), Color(0xFFF1E3C8)),
    dreamy           = listOf(Color(0xFFF8EEDA), Color(0xFFEDE0C6)),
    petal            = listOf(Color(0xFFFDF8EE), Color(0xFFF8EEDA), Color(0xFFF3E7CF)),
    gold             = listOf(Color(0xFFCE9F45), Color(0xFF9A7325)),
)

val HoneyDarkPalette = Palette(
    brand            = AppBrand.HONEY,
    isDark           = true,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFFBE9553),
    deepRose         = Color(0xFFF0E3C9),
    blushPink        = Color(0xFF3A3020),
    softPurple       = Color(0xFFBFAA8C),
    elegantCream     = Color(0xFF15120B),
    warmGold         = Color(0xFFF0C978),   // brighter than the accent in the dark
    dashboardSurface = Color(0xFF221C11),
    chipActive       = Color(0xFFBE9553),
    chipInactive     = Color(0xFF2E2617),
    availableGreen   = Color(0xFF6BC470),
    unavailableGrey  = Color(0xFF8A8A8A),
    cardBorder       = Color(0xFF3A301D),
    deeperRose       = Color(0xFF4E3C18),
    petalPink        = Color(0xFF2A2214),
    lilacMist        = Color(0xFF322818),
    softLavender     = Color(0xFF241E12),
    rosePetal        = Color(0xFFC4A56E),
    dangerRed        = Color(0xFFE06C5E),
    warningOrange    = Color(0xFFE9975A),
    neutralGrey      = Color(0xFFA99C84),
    adminPurple      = Color(0xFFB6A7CC),
    textStrong       = Color(0xFFE0D5BE),
    textMuted        = Color(0xFFB3A488),
    textFaint        = Color(0xFF8A7F69),
    brandRose        = listOf(Color(0xFF8A6B34), Color(0xFF6B5228), Color(0xFF473518)),
    brandRoseSoft    = listOf(Color(0xFF7C5F2E), Color(0xFF52401D)),
    screenBg         = listOf(Color(0xFF15120B), Color(0xFF1B1710), Color(0xFF1F1A11)),
    softPink         = listOf(Color(0xFF2E2617), Color(0xFF272013)),
    dreamy           = listOf(Color(0xFF2A2214), Color(0xFF322818)),
    petal            = listOf(Color(0xFF221C11), Color(0xFF2A2214), Color(0xFF1E1910)),
    gold             = listOf(Color(0xFFDCB367), Color(0xFFBE9139)),
)

// ── Maroon ──────────────────────────────────────────────────────────────────────
// Deep burgundy — the most serious of the families, for anyone who wants the
// warmth of the rose without its softness.
//
// This one had the sharpest version of the problem sage and honey each hit: the
// accent is a deep red and dangerRed is red, so a delete button or an error
// message would have read as ordinary chrome. dangerRed is therefore pushed to a
// brighter, more orange red than the accent in both modes — far enough that
// "this will destroy something" still stops the eye on a maroon surface.

val MaroonLightPalette = Palette(
    brand            = AppBrand.MAROON,
    isDark           = false,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFF8E3B44),
    deepRose         = Color(0xFF5C1F2A),
    blushPink        = Color(0xFFEFD6D9),
    softPurple       = Color(0xFF95606E),
    elegantCream     = Color(0xFFFFF8F8),
    warmGold         = Color(0xFFB98A34),
    dashboardSurface = Color(0xFFF9EAEB),
    chipActive       = Color(0xFF8E3B44),
    chipInactive     = Color(0xFFEBD5D7),
    availableGreen   = Color(0xFF2E7D32),
    unavailableGrey  = Color(0xFF9E9E9E),
    cardBorder       = Color(0xFFEBD3D6),
    deeperRose       = Color(0xFF46141E),
    petalPink        = Color(0xFFF7E3E5),
    lilacMist        = Color(0xFFEBD3D6),
    softLavender     = Color(0xFFFAEDEE),
    rosePetal        = Color(0xFFC08088),
    // Brighter and more orange than the maroon accent, so destructive actions
    // and errors do not blend into the theme.
    dangerRed        = Color(0xFFE04A2F),
    warningOrange    = Color(0xFFE67E22),
    neutralGrey      = Color(0xFF917E82),
    adminPurple      = Color(0xFF7B5E86),
    textStrong       = Color(0xFF4A3A3D),
    textMuted        = Color(0xFF877377),
    textFaint        = Color(0xFFB09A9E),
    brandRose        = listOf(Color(0xFFC4737D), Color(0xFF8E3B44), Color(0xFF5C1F2A)),
    brandRoseSoft    = listOf(Color(0xFFAE5A66), Color(0xFF6E2833)),
    screenBg         = listOf(Color(0xFFFFF8F8), Color(0xFFFBEEEF), Color(0xFFF7E7E9)),
    softPink         = listOf(Color(0xFFF7E3E5), Color(0xFFF0D5D8)),
    dreamy           = listOf(Color(0xFFF7E3E5), Color(0xFFEBD3D6)),
    petal            = listOf(Color(0xFFFDF3F3), Color(0xFFF7E3E5), Color(0xFFF1D9DC)),
    gold             = listOf(Color(0xFFE6C06A), Color(0xFFC79A3C)),
)

val MaroonDarkPalette = Palette(
    brand            = AppBrand.MAROON,
    isDark           = true,
    onPrimaryWhite   = Color(0xFFFFFFFF),
    roseGold         = Color(0xFFA85260),
    deepRose         = Color(0xFFF2D8DC),
    blushPink        = Color(0xFF3E2028),
    softPurple       = Color(0xFFC49AA6),
    elegantCream     = Color(0xFF160C0F),
    warmGold         = Color(0xFFE0B968),
    dashboardSurface = Color(0xFF25141A),
    chipActive       = Color(0xFFA85260),
    chipInactive     = Color(0xFF331A21),
    availableGreen   = Color(0xFF5CC462),
    unavailableGrey  = Color(0xFF8A8A8A),
    cardBorder       = Color(0xFF3B2027),
    deeperRose       = Color(0xFF5A222E),
    petalPink        = Color(0xFF2C171D),
    lilacMist        = Color(0xFF331A21),
    softLavender     = Color(0xFF221218),
    rosePetal        = Color(0xFFC17F8B),
    dangerRed        = Color(0xFFFF7A5C),
    warningOrange    = Color(0xFFE9975A),
    neutralGrey      = Color(0xFFAC9096),
    adminPurple      = Color(0xFFC2A0CE),
    textStrong       = Color(0xFFE2CDD2),
    textMuted        = Color(0xFFB79CA3),
    textFaint        = Color(0xFF8C757C),
    brandRose        = listOf(Color(0xFF8A3844), Color(0xFF6B2B36), Color(0xFF471A23)),
    brandRoseSoft    = listOf(Color(0xFF7C323E), Color(0xFF52202A)),
    screenBg         = listOf(Color(0xFF160C0F), Color(0xFF1D1014), Color(0xFF221318)),
    softPink         = listOf(Color(0xFF331A21), Color(0xFF2B161C)),
    dreamy           = listOf(Color(0xFF2C171D), Color(0xFF331A21)),
    petal            = listOf(Color(0xFF25141A), Color(0xFF2C171D), Color(0xFF201217)),
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
