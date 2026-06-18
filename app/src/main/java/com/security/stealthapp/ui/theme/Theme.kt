package com.security.stealthapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NotepadColorScheme = lightColorScheme(
    primary          = NotepadPrimary,
    onPrimary        = NotepadOnPrimary,
    secondary        = NotepadSecondary,
    background       = NotepadBg,
    surface          = NotepadSurface,
    onBackground     = NotepadPrimary,
    onSurface        = NotepadPrimary
)

private val DashboardColorScheme = lightColorScheme(
    primary          = RoseGold,
    onPrimary        = NotepadOnPrimary,
    primaryContainer = BlushPink,
    secondary        = SoftPurple,
    tertiary         = WarmGold,
    background       = ElegantCream,
    surface          = DashboardSurface,
    onBackground     = DeepRose,
    onSurface        = DeepRose
)

@Composable
fun NotepadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NotepadColorScheme,
        typography  = NotepadTypography,
        content     = content
    )
}

@Composable
fun DashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DashboardColorScheme,
        typography  = DashboardTypography,
        content     = content
    )
}
