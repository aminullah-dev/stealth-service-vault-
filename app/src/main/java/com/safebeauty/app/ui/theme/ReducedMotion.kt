package com.safebeauty.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Accessibility: honour the OS "Remove animations" setting (Settings →
 * Accessibility), which zeroes the global animator duration scale. Users who
 * enable it are telling every app they are sensitive to motion — so we skip
 * non-essential transitions instead of playing them.
 *
 * Provided once by [DashboardTheme]; read at any animation site via
 * `LocalReducedMotion.current`. Defaults to false outside the theme (@Preview).
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the system animator duration scale; 0 means animations are removed. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * A [tween] that collapses to an instant [snap] when the user has reduced
 * motion. Use in place of a raw `tween(...)` so a single call site respects the
 * accessibility setting. Default duration follows the 150–300 ms guideline.
 */
@Composable
@ReadOnlyComposable
fun <T> motionTween(durationMillis: Int = 220): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(durationMillis)
