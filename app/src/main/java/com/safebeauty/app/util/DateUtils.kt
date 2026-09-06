package com.safebeauty.app.util

import java.text.DateFormat
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
 * A formatted date, isolated so right-to-left text cannot take it apart.
 *
 * "9 Sep, 5:00 AM" inside a Dari screen rendered as "Sep, 5:00 AM 9": the
 * bidirectional algorithm resolves the leading day number as its own run and the
 * paragraph's right-to-left order puts it after the month. Confirmed by running
 * java.text.Bidi on the exact string — the visual order of
 * "📅 9 Sep, 5:00 AM" under an RTL paragraph is "Sep, 5:00 AM 9📅", which is what
 * a customer saw on every booking card.
 *
 * The isolate characters — U+2066 LEFT-TO-RIGHT ISOLATE and U+2069 POP
 * DIRECTIONAL ISOLATE — say "this run is left-to-right and its direction does
 * not leak either way". A plain LRM would not do: the problem is not the
 * surrounding text's direction but the date being split into runs at all.
 *
 * Wrapping the whole date rather than each number is deliberate. A date is one
 * thing to read, and the pieces that make it up have no meaning apart.
 */
fun DateFormat.formatIsolated(date: Date): String = "\u2066" + format(date) + "\u2069"

fun DateFormat.formatIsolated(epochMs: Long): String = formatIsolated(Date(epochMs))
