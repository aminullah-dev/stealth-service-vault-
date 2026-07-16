package com.safebeauty.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Built from the raw palette fields (NOT the @Composable colour getters, which
// can't be read here) so the two Material schemes stay in sync with Palette.
private val LightColorScheme = lightColorScheme(
    primary          = LightPalette.roseGold,
    onPrimary        = LightPalette.onPrimaryWhite,
    primaryContainer = LightPalette.blushPink,
    secondary        = LightPalette.softPurple,
    tertiary         = LightPalette.warmGold,
    background       = LightPalette.elegantCream,
    surface          = LightPalette.dashboardSurface,
    onBackground     = LightPalette.deepRose,
    onSurface        = LightPalette.deepRose,
)

private val DarkColorScheme = darkColorScheme(
    primary          = DarkPalette.roseGold,
    onPrimary        = DarkPalette.onPrimaryWhite,
    primaryContainer = DarkPalette.blushPink,
    secondary        = DarkPalette.softPurple,
    tertiary         = DarkPalette.warmGold,
    background       = DarkPalette.elegantCream,
    surface          = DarkPalette.dashboardSurface,
    onBackground     = DarkPalette.deepRose,
    onSurface        = DarkPalette.deepRose,
)

/**
 * Applied at the app root (MainActivity), and re-applied by a few screens that
 * wrap their own content. [darkTheme] selects the palette that every screen's
 * colour references resolve against (see Color.kt), so the whole app flips with a
 * single flag. It defaults to INHERITING the current theme, so a nested
 * `DashboardTheme { … }` keeps whatever the root set (dark or light) instead of
 * forcing light; the root passes an explicit value from the device/user setting.
 * Outside any theme (e.g. @Preview) LocalPalette defaults to light.
 */
@Composable
fun DashboardTheme(
    darkTheme: Boolean = LocalPalette.current.isDark,
    content: @Composable () -> Unit
) {
    val palette     = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = DashboardTypography,
            content     = content
        )
    }
}
