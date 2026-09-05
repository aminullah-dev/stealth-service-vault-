package com.safebeauty.app.util

import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.ServiceTiming
import com.safebeauty.app.data.firebase.StaffMember
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

    // ── Parties ──────────────────────────────────────────────────────────────
    // Same cases as functions/test/party.test.js. The server decides whether the
    // salon is free; this decides which start times a bride is shown.

    private fun partySalon(slotMinutes: Int = 60, staff: Int = 1) = SalonDocument(
        slotDurationMinutes = slotMinutes,
        durationPerService = mapOf("Makeup" to 60, "Hair" to 90),
        staff = (1..staff).map { StaffMember(id = "s$it", name = "S$it", active = true) },
    )

    @Test
    fun `more stylists means the same party finishes sooner`() {
        val three = listOf(
            "A" to listOf("Makeup"), "B" to listOf("Makeup"), "C" to listOf("Makeup"),
        )
        assertEquals(3, SlotMath.partyLayoutFor(partySalon(60, 1), three).span)
        assertEquals(1, SlotMath.partyLayoutFor(partySalon(60, 3), three).span)
        assertEquals(2, SlotMath.partyLayoutFor(partySalon(60, 2), three).span)
    }

    @Test
    fun `a party is busy throughout, with no gap to sell`() {
        val l = SlotMath.partyLayoutFor(partySalon(60, 1), listOf("A" to listOf("Makeup", "Hair")))
        assertEquals(l.span, l.busyOffsets.size)
        assertEquals((0 until l.span).toList(), l.busyOffsets)
    }

    @Test
    fun `a salon with no named staff is one stylist, not zero`() {
        val l = SlotMath.partyLayoutFor(partySalon(60, 0), listOf("A" to listOf("Makeup")))
        assertEquals(1, l.span)
    }

    @Test
    fun `an empty party is still a real booking`() {
        val l = SlotMath.partyLayoutFor(partySalon(60, 3), emptyList())
        assertEquals(1, l.span)
        assertEquals(listOf(0), l.busyOffsets)
    }

    @Test
    fun `a party layout says it needs the whole salon`() {
        // The server widens hasSlotConflict to every chair when the request is a
        // party. If this flag is ever dropped, the picker goes back to offering a
        // bride a time when two of three stylists are free — which the server then
        // refuses at the till.
        val l = SlotMath.partyLayoutFor(partySalon(60, 3), listOf("A" to listOf("Makeup")))
        assertEquals(true, l.wholeSalon)
    }

    @Test
    fun `an ordinary booking does not take the whole salon`() {
        assertEquals(false, SlotMath.layoutFor(partySalon(60, 3), listOf("Makeup")).wholeSalon)
        assertEquals(false, SlotMath.layoutFor(salon(30, mapOf("Colour" to colour)), listOf("Colour")).wholeSalon)
    }

    @Test
    fun `even an empty party takes the salon`() {
        // Otherwise a guest list that normalises down to nothing would quietly
        // book as an ordinary one-chair appointment.
        assertEquals(true, SlotMath.partyLayoutFor(partySalon(60, 3), emptyList()).wholeSalon)
    }
}
