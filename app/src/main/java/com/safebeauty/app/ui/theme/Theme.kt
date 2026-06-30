package com.safebeauty.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DashboardColorScheme = lightColorScheme(
    primary          = RoseGold,
    onPrimary        = OnPrimaryWhite,
    primaryContainer = BlushPink,
    secondary        = SoftPurple,
    tertiary         = WarmGold,
    background       = ElegantCream,
    surface          = DashboardSurface,
    onBackground     = DeepRose,
    onSurface        = DeepRose
)

@Composable
fun DashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DashboardColorScheme,
        typography  = DashboardTypography,
        content     = content
    )
}
