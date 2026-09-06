package com.safebeauty.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.text.Bidi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Test

/**
 * A date must survive being written into a right-to-left screen.
 *
 * This is not a hypothetical: a customer's booking card read
 * "Sep, 5:00 AM 9" — the day number resolved as its own bidirectional run and
 * the paragraph's order put it after the time. The same happened to every chat
 * timestamp ("AM 5:00"), every payout date and every review date, in Dari and
 * Pashto, on all three of customer, provider and admin.
 *
 * The test reorders the string the way a renderer does rather than asserting on
 * the isolate characters, because the characters are the mechanism and the
 * reading order is the thing that was wrong.
 */
class DateIsolationTest {

    /** The visual, left-to-right reading order of [s] inside an RTL paragraph. */
    private fun visual(s: String): String {
        val bidi = Bidi(s, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val n = bidi.runCount
        val levels = ByteArray(n) { bidi.getRunLevel(it).toByte() }
        val runs = Array<Any>(n) { s.substring(bidi.getRunStart(it), bidi.getRunLimit(it)) }
        Bidi.reorderVisually(levels, 0, runs, 0, n)
        return runs.joinToString("").filter { it != '⁦' && it != '⁩' }
    }

    private fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.US)

    // 9 September 2026, 05:00 Kabul-ish — the booking in the screenshot.
    private val instant = Date(1789_000_000_000L)

    // The label the app actually puts in front of a date. It is a neutral
    // character, so an RTL paragraph correctly moves it to the right-hand side —
    // that is not the bug and the assertions below do not test for it. What
    // matters is whether the date itself stays in one piece.
    private val leading = "📅 "

    @Test
    fun `a day-first date is not taken apart by a right-to-left screen`() {
        val f = fmt("d MMM, h:mm a")
        val date = f.format(instant)

        // The defect, reproduced: the date does not survive as one run, and the
        // day number in particular ends up after the time.
        val broken = visual(leading + date)
        assertNotEquals("this case is supposed to reproduce the defect", date,
                        broken.replace("📅", "").trim())

        // The fix: the date reads exactly as written, in one piece.
        assertTrue("read as: $broken", visual(leading + f.formatIsolated(instant)).contains(date))
    }

    @Test
    fun `every pattern the app formats with survives`() {
        for (pattern in listOf(
            "d MMM, h:mm a", "h:mm a", "d MMM", "dd MMM yyyy, HH:mm",
            "dd MMM", "d MMM yyyy", "MMM d, HH:mm", "dd MMM, HH:mm", "MMMM yyyy",
        )) {
            val f = fmt(pattern)
            val date = f.format(instant)
            val read = visual(leading + f.formatIsolated(instant))
            assertTrue("pattern $pattern read as: $read", read.contains(date))
        }
    }

    @Test
    fun `the isolate wraps the date and nothing else`() {
        val f = fmt("d MMM")
        assertEquals("⁦${f.format(instant)}⁩", f.formatIsolated(instant))
        // The epoch overload is the same date, not a different one.
        assertEquals(f.formatIsolated(instant), f.formatIsolated(instant.time))
    }
}
