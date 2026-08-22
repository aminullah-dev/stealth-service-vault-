package com.safebeauty.app.util

import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.ServiceTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device's copy of the slot layout rule.
 *
 * These are deliberately the same cases as functions/test/slots.test.js. The
 * server decides whether a booking is allowed; this decides which start times to
 * offer, and if the two ever disagree the customer is shown a slot she cannot
 * have and is refused at the moment she expects to pay. Keeping the assertions
 * identical is what makes that drift visible here rather than there.
 */
class SlotMathTest {

    private val colour = ServiceTiming(activeBefore = 45, processing = 30, activeAfter = 20)

    private fun salon(
        slotMinutes: Int = 30,
        timing: Map<String, ServiceTiming> = emptyMap(),
        durations: Map<String, Int> = emptyMap(),
    ) = SalonDocument(
        slotDurationMinutes = slotMinutes,
        serviceTiming = timing,
        durationPerService = durations,
    )

    @Test
    fun `a colour at 30-minute slots frees the development gap`() {
        val l = SlotMath.layoutFor(salon(30, mapOf("Colour" to colour)), listOf("Colour"))
        assertEquals("45 up = 2, 30 down = 1, 20 up = 1", 4, l.span)
        assertEquals(listOf(0, 1, 3), l.busyOffsets)
    }

    @Test
    fun `at 60-minute slots the gap is too small to exist`() {
        val l = SlotMath.layoutFor(salon(60, mapOf("Colour" to colour)), listOf("Colour"))
        assertEquals(listOf(0, 1), l.busyOffsets)
        assertEquals("no slot is claimed free that the stylist might need", 2, l.span)
    }

    @Test
    fun `processing rounds down and working time rounds up`() {
        val t = mapOf("X" to ServiceTiming(activeBefore = 10, processing = 50, activeAfter = 10))
        val l = SlotMath.layoutFor(salon(60, t), listOf("X"))
        assertEquals("fifty minutes of processing is not a free hour", listOf(0, 1), l.busyOffsets)
        assertEquals(2, l.span)
    }

    @Test
    fun `a service the stylist never works still occupies her somewhere`() {
        val t = mapOf("Soak" to ServiceTiming(activeBefore = 0, processing = 90, activeAfter = 0))
        val l = SlotMath.layoutFor(salon(60, t), listOf("Soak"))
        assertTrue("a booking conflicting with nothing can be booked over", l.busyOffsets.isNotEmpty())
        assertEquals(0, l.busyOffsets.first())
    }

    @Test
    fun `no timing configured behaves exactly as before`() {
        val a = SlotMath.layoutFor(salon(60), listOf("Cut", "Blowdry"))
        assertEquals(2, a.span)
        assertEquals(listOf(0, 1), a.busyOffsets)

        val b = SlotMath.layoutFor(salon(30, durations = mapOf("Long" to 120)), listOf("Long"))
        assertEquals(4, b.span)
        assertEquals(listOf(0, 1, 2, 3), b.busyOffsets)
    }

    @Test
    fun `services run back to back and the gap keeps its place`() {
        val l = SlotMath.layoutFor(
            salon(30, mapOf("Colour" to colour), mapOf("Cut" to 30)),
            listOf("Colour", "Cut"),
        )
        assertEquals("colour spans 4, cut adds 1", 5, l.span)
        assertEquals("the gap stays inside the colour", listOf(0, 1, 3, 4), l.busyOffsets)
    }

    @Test
    fun `no services is a single slot`() {
        val l = SlotMath.layoutFor(salon(60), emptyList())
        assertEquals(1, l.span)
        assertEquals(listOf(0), l.busyOffsets)
    }

    @Test
    fun `a service with no duration falls back to one whole slot`() {
        val l = SlotMath.layoutFor(salon(60), listOf("Unpriced"))
        assertEquals(1, l.span)
        assertEquals(listOf(0), l.busyOffsets)
    }
}
