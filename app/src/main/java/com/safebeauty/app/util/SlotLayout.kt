package com.safebeauty.app.util

import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.activeStaff

/**
 * Where a booking's working time and its idle time fall, on the device.
 *
 * A mirror of serviceLayout in functions/lib/slots.js — deliberately, and it must
 * stay one. The server decides whether a booking is allowed; this only decides
 * which start times to offer. If the two disagree the customer is shown a slot
 * she cannot have, taps it, and is refused at the moment she expects to pay.
 *
 * Some services leave the stylist free in the middle. Colouring hair is roughly
 * 45 minutes of application, 30 while the colour develops, then 20 to wash and
 * style — and during those 30 minutes the stylist can be cutting someone else's
 * hair. [busyOffsets] names the slots she is actually working; the rest of the
 * span is the client sitting there and the stylist elsewhere.
 *
 * Quantised to whole slots, because a 30-minute gap cannot be expressed inside a
 * 60-minute grid. The rounding is asymmetric on purpose — working time up, idle
 * time down — so a rounding error costs the salon capacity rather than seating
 * two customers with one stylist.
 */
data class SlotLayout(
    /** Total slots from the start until the client leaves. */
    val span: Int,
    /** Offsets within [span] where the stylist is unavailable. */
    val busyOffsets: List<Int>,
)

object SlotMath {

    /**
     * A wedding party's layout: everyone works, so the wall-clock is the total
     * work divided by however many stylists there are.
     *
     * Mirrors partySpan in functions/lib/party.js. Without it the picker would
     * treat a party as one stylist working through five guests in turn and offer
     * far fewer start times than the server would actually accept — safe, but
     * wrong, and it would hide the salon's whole afternoon from a bride.
     *
     * Busy throughout: nobody is idle during a wedding, so there is no gap to
     * sell the way a colour's development has one.
     */
    fun partyLayoutFor(
        salon: SalonDocument,
        guests: List<Pair<String, List<String>>>,
    ): SlotLayout {
        val step = salon.slotDurationMinutes.coerceAtLeast(1)
        val staff = salon.activeStaff().size.coerceAtLeast(1)
        val names = guests.flatMap { it.second }
        if (names.isEmpty()) return SlotLayout(1, listOf(0))

        val totalMinutes = names.sumOf { n ->
            val d = salon.durationPerService[n] ?: 0
            if (d > 0) d else step
        }
        val wallClock = ceilDiv(totalMinutes, staff)
        val span = ceilDiv(wallClock, step).coerceAtLeast(1)
        return SlotLayout(span = span, busyOffsets = (0 until span).toList())
    }

    fun layoutFor(salon: SalonDocument, serviceNames: List<String>): SlotLayout {
        val step = salon.slotDurationMinutes.coerceAtLeast(1)
        if (serviceNames.isEmpty()) return SlotLayout(1, listOf(0))

        val busy = mutableListOf<Int>()
        var cursor = 0
        for (name in serviceNames) {
            val t = salon.serviceTiming[name]
            var beforeSlots: Int
            val procSlots: Int
            val afterSlots: Int

            if (t != null && (t.activeBefore + t.processing + t.activeAfter) > 0) {
                beforeSlots = ceilDiv(t.activeBefore.coerceAtLeast(0), step)
                procSlots   = t.processing.coerceAtLeast(0) / step        // floor
                afterSlots  = ceilDiv(t.activeAfter.coerceAtLeast(0), step)
                // A service the stylist never works is not a service; it would
                // conflict with nothing and could be booked straight over.
                if (beforeSlots + afterSlots == 0) beforeSlots = 1
            } else {
                val d = salon.durationPerService[name] ?: 0
                beforeSlots = ceilDiv(if (d > 0) d else step, step)
                procSlots = 0
                afterSlots = 0
            }

            for (i in 0 until beforeSlots) busy.add(cursor + i)
            cursor += beforeSlots + procSlots
            for (i in 0 until afterSlots) busy.add(cursor + i)
            cursor += afterSlots
        }

        return SlotLayout(
            span = cursor.coerceAtLeast(1),
            busyOffsets = if (busy.isEmpty()) listOf(0) else busy,
        )
    }

    private fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) a else (a + b - 1) / b
}
