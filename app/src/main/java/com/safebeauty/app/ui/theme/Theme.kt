package com.safebeauty.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Builds the Material scheme from whichever [Palette] is active.
 *
 * It used to be two constants built from the two palettes at file scope. With a
 * second colour family that would have meant four hand-maintained copies of the
 * same nine mappings, and a fifth the day another family is added — so the
 * mapping is expressed once and applied to whatever palette it is handed.
 */
private fun schemeFor(p: Palette) = if (p.isDark) {
    darkColorScheme(
        primary          = p.roseGold,
        onPrimary        = p.onPrimaryWhite,
        primaryContainer = p.blushPink,
        secondary        = p.softPurple,
        tertiary         = p.warmGold,
        background       = p.elegantCream,
        surface          = p.dashboardSurface,
        onBackground     = p.deepRose,
        onSurface        = p.deepRose,
    )
} else {
    lightColorScheme(
        primary          = p.roseGold,
        onPrimary        = p.onPrimaryWhite,
        primaryContainer = p.blushPink,
        secondary        = p.softPurple,
        tertiary         = p.warmGold,
        background       = p.elegantCream,
        surface          = p.dashboardSurface,
        onBackground     = p.deepRose,
        onSurface        = p.deepRose,
    )
}

/** The four palettes, as brand × mode. */
fun paletteFor(brand: AppBrand, dark: Boolean): Palette = when (brand) {
    AppBrand.ROSE     -> if (dark) RoseDarkPalette else RoseLightPalette
    AppBrand.LAVENDER -> if (dark) LavenderDarkPalette else LavenderLightPalette
}

/**
 * Applied at the app root (MainActivity), and re-applied by the screens that
 * wrap their own content. [darkTheme] and [brand] select the palette that every
 * colour reference in the app resolves against (see Color.kt), so the whole app
 * changes with two flags.
 *
 * Both parameters default to INHERITING the palette already in scope, which is
 * why the ~27 nested `DashboardTheme { … }` calls need no arguments and did not
 * change when the second colour family was added: brand travels inside the
 * palette itself. The root passes explicit values from the user's settings.
 * Outside any theme (e.g. @Preview) LocalPalette defaults to rose light.
 */
@Composable
fun DashboardTheme(
    darkTheme: Boolean = LocalPalette.current.isDark,
    brand: AppBrand = LocalPalette.current.brand,
    content: @Composable () -> Unit
) {
    val palette     = paletteFor(brand, darkTheme)
    val colorScheme = remember(palette) { schemeFor(palette) }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = DashboardTypography,
            content     = content
        )
    }
}
