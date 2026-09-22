package com.safebeauty.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.text.Bidi
import java.text.SimpleDateFormat
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
 * `AppLanguage.formatDate` (util/DateUtils.kt) wraps its output in a First-Strong
 * Isolate — U+2068 … U+2069 — so a formatted date reads as one indivisible run
 * whose direction is decided by its own first strong character. That is what
 * these tests guard. `formatDate` itself uses `android.icu`, which is NOT on the
 * JVM unit-test classpath, so the Solar-Hijri calendar / Afghan month names are
 * verified on a real device, not here; what a plain unit test *can* prove is the
 * isolation contract, so these tests mirror `formatDate`'s exact wrapping and
 * assert the reading order it produces.
 */
class DateIsolationTest {

    // The exact isolate characters formatDate wraps with. First-Strong (U+2068),
    // not the plain LTR isolate (U+2066) the old English-only dates used: a
    // native Dari date is right-to-left, so forcing LTR would reorder it.
    private val FSI = '⁨'
    private val PDI = '⁩'

    /** Wrap exactly as AppLanguage.formatDate does. */
    private fun isolated(s: String) = "$FSI$s$PDI"

    /** The visual, left-to-right reading order of [s] inside an RTL paragraph. */
    private fun visual(s: String): String {
        val bidi = Bidi(s, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val n = bidi.runCount
        val levels = ByteArray(n) { bidi.getRunLevel(it).toByte() }
        val runs = Array<Any>(n) { s.substring(bidi.getRunStart(it), bidi.getRunLimit(it)) }
        Bidi.reorderVisually(levels, 0, runs, 0, n)
        // Strip every directional-isolate control so the comparison is on glyphs.
        return runs.joinToString("").filter { it !in "⁦⁧⁨⁩" }
    }

    // 9 September 2026, 05:00-ish — the booking in the screenshot.
    private val instant = 1789_000_000_000L

    // The label the app puts in front of a date. It is a neutral character, so an
    // RTL paragraph correctly moves it to the right-hand side — that is not the
    // bug. What matters is whether the date itself stays in one piece.
    private val leading = "📅 "

    private fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.US).format(java.util.Date(instant))

    @Test
    fun `a day-first date is taken apart by a right-to-left screen without the isolate`() {
        val date = fmt("d MMM, h:mm a")
        // The defect, reproduced: unwrapped, the date does not survive as one
        // run and the day number ends up after the time.
        val broken = visual(leading + date)
        assertNotEquals(
            "this case is supposed to reproduce the defect",
            date, broken.replace("📅", "").trim()
        )
    }

    @Test
    fun `a left-to-right (English) date survives the isolate in an RTL screen`() {
        val date = fmt("d MMM, h:mm a")
        assertTrue(
            "read as: ${visual(leading + isolated(date))}",
            visual(leading + isolated(date)).contains(date)
        )
    }

    @Test
    fun `an Afghan Solar-Hijri date is right-to-left, so First-Strong keeps it RTL not LTR`() {
        // A hand-built stand-in for what formatDate returns under DARI: Persian
        // digits and an Afghan month name. Its first STRONG character is the
        // letter م — right-to-left — so a First-Strong Isolate (U+2068) renders
        // the whole date RTL. The old LTR isolate (U+2066) would have forced it
        // left-to-right and mangled it; this is exactly why the isolate changed.
        val dari = "۴ میزان ۱۴۰۵"
        val base = Bidi(dari, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        assertFalse(
            "a native Dari date must resolve as right-to-left under first-strong",
            base.baseIsLeftToRight()
        )
        // An English date, by contrast, is left-to-right — First-Strong keeps
        // each in its own natural direction, which a fixed LTR isolate cannot.
        val english = Bidi(fmt("d MMM yyyy"), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        assertTrue("an English date must resolve as left-to-right", english.baseIsLeftToRight())
        // Iranian month names must never appear; the real Afghan-vs-Iranian
        // rendering is verified on the emulator, this only guards the fixture.
        assertFalse("Iranian month name leaked in", dari.contains("مهر"))
    }

    @Test
    fun `every pattern the app formats with survives the isolate`() {
        for (pattern in listOf(
            "d MMM, h:mm a", "h:mm a", "d MMM", "dd MMM yyyy, HH:mm",
            "dd MMM", "d MMM yyyy", "MMM d, HH:mm", "dd MMM, HH:mm",
        )) {
            val date = fmt(pattern)
            val read = visual(leading + isolated(date))
            assertTrue("pattern $pattern read as: $read", read.contains(date))
        }
    }
}
