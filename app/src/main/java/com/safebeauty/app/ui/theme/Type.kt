package com.safebeauty.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.safebeauty.app.R

/**
 * Vazirmatn (OFL) — a Persian/Dari/Pashto-first typeface with full Latin
 * support. The platform default renders Arabic-script text with generic,
 * cramped glyphs; a purpose-built face is the single biggest app-wide visual
 * upgrade for a Dari/Pashto-first audience. Weights map so existing
 * `FontWeight.Bold` etc. throughout the app pick the right file automatically.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular,  FontWeight.Normal),
    Font(R.font.vazirmatn_medium,   FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold,     FontWeight.Bold)
)

// Display/headline styles carry an explicit lineHeight so multi-line Persian and
// Pashto headings breathe instead of stacking tightly (Arabic-script ascenders
// need more leading than the default 1.0×). No letterSpacing on any script-bearing
// style — tracking breaks the connected letterforms of Arabic script.
val DashboardTypography = Typography(
    displayLarge  = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,     fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,     fontSize = 30.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,     fontSize = 26.sp, lineHeight = 34.sp),
    headlineMedium= TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,     fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge    = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,     fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium   = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,   fontSize = 15.sp),
    bodyMedium    = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,   fontSize = 13.sp),
    bodySmall     = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelLarge    = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    labelMedium   = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,   fontSize = 12.sp),
    labelSmall    = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,   fontSize = 11.sp)
)
