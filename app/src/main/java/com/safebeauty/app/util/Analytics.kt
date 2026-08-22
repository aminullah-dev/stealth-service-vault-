package com.safebeauty.app.util

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * The booking funnel.
 *
 * The app shipped with firebase-analytics on the classpath but never logged a
 * single event, so the only data available was automatic screen views — enough
 * to know people open the app, useless for knowing where they give up. These
 * events answer the one question that actually drives the roadmap: of the people
 * who browse salons, how many open one, how many start a booking, and how many
 * finish paying?
 *
 * Deliberately no personal data. Salon ids and service names are business data;
 * names, phone numbers and locations never go to Analytics.
 */
object Analytics {

    private val fa: FirebaseAnalytics by lazy { Firebase.analytics }

    // ── Funnel steps, in order ────────────────────────────────────────────────
    private const val SALON_LIST_VIEWED = "salon_list_viewed"
    private const val SALON_OPENED      = "salon_opened"
    private const val BOOKING_STARTED   = "booking_started"
    private const val BOOKING_COMPLETED = "booking_completed"
    private const val BOOKING_FAILED    = "booking_failed"

    private fun log(name: String, build: Bundle.() -> Unit = {}) {
        runCatching { fa.logEvent(name, Bundle().apply(build)) }
    }

    /** Step 1 — the browse list rendered with [count] salons under the current filters. */
    fun salonListViewed(count: Int, filtered: Boolean) = log(SALON_LIST_VIEWED) {
        putInt("result_count", count)
        putBoolean("filtered", filtered)
    }

    /** Step 2 — a customer opened one salon's detail sheet. */
    fun salonOpened(salonId: String) = log(SALON_OPENED) {
        putString("salon_id", salonId)
    }

    /** Step 3 — checkout was requested (the point of intent, before payment). */
    fun bookingStarted(salonId: String, serviceCount: Int, cash: Boolean) = log(BOOKING_STARTED) {
        putString("salon_id", salonId)
        putInt("service_count", serviceCount)
        putString("payment_method", if (cash) "cash" else "online")
    }

    /** Step 4 — the booking exists and is paid or cash-confirmed. */
    fun bookingCompleted(cash: Boolean) = log(BOOKING_COMPLETED) {
        putString("payment_method", if (cash) "cash" else "online")
    }

    /** Drop-off with a reason, so a spike in one failure mode is visible. */
    fun bookingFailed(reason: String) = log(BOOKING_FAILED) {
        // Truncated: Analytics rejects long values, and the reason is only ever
        // used to group failures, never to read back a message.
        putString("reason", reason.take(90))
    }
}
