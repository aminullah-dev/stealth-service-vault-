package com.safebeauty.app.util

import com.safebeauty.app.ui.theme.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date helpers shared between the customer slot logic and the provider time-off
 * editor. Blocked-off days are stored as "yyyy-MM-dd" strings in Kabul-local time
 * so the same key computed on any device (and on the server, which uses the
 * Asia/Kabul timezone) always matches — SafeBeauty operates only in Afghanistan.
 */
object DateUtils {
    private val KABUL: TimeZone = TimeZone.getTimeZone("Asia/Kabul")

    /** The "yyyy-MM-dd" calendar-day key for an instant, in Kabul-local time. */
    fun kabulDateKey(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = KABUL }
        return fmt.format(Date(epochMs))
    }
}

/**
 * The ICU locale that decides which calendar and which month names a displayed
 * date uses. This is what makes a Dari date read "۴ میزان ۱۴۰۵" (Afghan
 * Solar-Hijri month names) rather than the Iranian "۴ مهر" or a Gregorian
 * "26 Sep" — and it mirrors the iOS fix that switched to Locale "fa_AF".
 *
 *  - DARI    → fa_AF on the persian (Solar-Hijri) calendar, Afghan month names.
 *  - PASHTO  → ps_AF on the persian calendar, Pashto month names.
 *  - ENGLISH → gregorian, unchanged.
 *
 * `android.icu` (ICU4J) is used, not `java.text` — minSdk is 26, so it is
 * available, and `java.text.SimpleDateFormat` cannot render the persian
 * calendar at all.
 */
fun AppLanguage.icuLocale(): android.icu.util.ULocale = when (this) {
    AppLanguage.DARI    -> android.icu.util.ULocale("fa_AF@calendar=persian")
    AppLanguage.PASHTO  -> android.icu.util.ULocale("ps_AF@calendar=persian")
    AppLanguage.ENGLISH -> android.icu.util.ULocale("en@calendar=gregorian")
}

/**
 * Formats [epochMs] in Kabul time with [pattern], in this language's calendar
 * and month names, wrapped in a First-Strong Isolate (U+2068 … U+2069).
 *
 * Why First-Strong and not the plain LTR isolate the old `formatIsolated` used:
 * that helper wrapped dates in U+2066 (LEFT-TO-RIGHT ISOLATE) because the dates
 * were English and always LTR. A native Dari date ("۴ میزان ۱۴۰۵") is RTL, so
 * forcing LTR would reorder it wrongly. U+2068 FIRST STRONG ISOLATE auto-detects
 * the run's direction from its first strong character, so an English date stays
 * LTR and a Dari date stays RTL, each rendered as one indivisible unit inside a
 * paragraph of the opposite direction. U+2069 POP DIRECTIONAL ISOLATE closes it.
 *
 * The timezone is pinned to Asia/Kabul rather than the device timezone: this is
 * an Afghanistan-only product, and pinning it also fixes displayed slot times
 * drifting when a device is set to another zone.
 */
fun AppLanguage.formatDate(epochMs: Long, pattern: String): String {
    val fmt = android.icu.text.SimpleDateFormat(pattern, icuLocale())
    fmt.timeZone = android.icu.util.TimeZone.getTimeZone("Asia/Kabul")
    return "⁨" + fmt.format(Date(epochMs)) + "⁩"
}
