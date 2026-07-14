package com.safebeauty.app.util

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
