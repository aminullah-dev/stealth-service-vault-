package com.safebeauty.app.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of asking the backend to start a HesabPay checkout.
 * [checkoutUrl] is opened in a browser / Custom Tab; the rest is shown to the
 * customer so they see exactly what they're paying and the salon's net.
 */
data class CheckoutSession(
    val paymentId: String,
    val appointmentId: String,
    val checkoutUrl: String,
    val amount: Long,
    val commissionAmount: Long,
    val providerNet: Long
)

/**
 * Talks to the payment Cloud Functions. The HesabPay API key lives only in the
 * backend — this class never sees it. The client's job is to (1) ask the backend
 * for a checkout URL and (2) observe the resulting payment document's status,
 * which the webhook flips to PAID once HesabPay confirms.
 */
@Singleton
class PaymentRepository @Inject constructor() {

    private val functions = FirebaseFunctions.getInstance()
    private val paymentsCol = FirebaseFirestore.getInstance().collection("payments")

    /**
     * Asks the backend to create an appointment (AWAITING_PAYMENT) and a HesabPay
     * checkout session for it. Returns null on failure.
     */
    suspend fun createCheckout(
        salonId: String,
        serviceName: String,
        appointmentDateMs: Long,
        notes: String,
        email: String
    ): CheckoutSession? = runCatching {
        val payload = hashMapOf(
            "salonId" to salonId,
            "serviceName" to serviceName,
            "appointmentDate" to appointmentDateMs,
            "notes" to notes,
            "email" to email
        )
        val result = functions
            .getHttpsCallable("createPaymentSession")
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val map = result.getData() as? Map<String, Any?> ?: return@runCatching null

        CheckoutSession(
            paymentId        = map["paymentId"] as? String ?: "",
            appointmentId    = map["appointmentId"] as? String ?: "",
            checkoutUrl      = map["checkoutUrl"] as? String ?: "",
            amount           = (map["amount"] as? Number)?.toLong() ?: 0L,
            commissionAmount = (map["commissionAmount"] as? Number)?.toLong() ?: 0L,
            providerNet      = (map["providerNet"] as? Number)?.toLong() ?: 0L
        ).takeIf { it.checkoutUrl.isNotBlank() }
    }.onFailure { CrashReporter.recordNonFatal(it, "payment:createCheckout") }
        .getOrNull()

    /**
     * Admin-only: records a payout to [providerId] (settles their owed balance to
     * zero server-side). Returns the amount paid, or null on failure.
     */
    suspend fun recordProviderPayout(providerId: String, method: String = "MANUAL"): Long? =
        runCatching {
            val result = functions
                .getHttpsCallable("recordProviderPayout")
                .call(hashMapOf("providerId" to providerId, "method" to method))
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?>
            (map?.get("amount") as? Number)?.toLong()
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:recordProviderPayout") }
            .getOrNull()

    /**
     * Admin-only: marks a refund request as processed (the admin has sent the
     * money back outside the app) and flips the underlying payment to REFUNDED.
     */
    suspend fun recordRefundProcessed(refundRequestId: String): Boolean =
        runCatching {
            functions
                .getHttpsCallable("recordRefundProcessed")
                .call(hashMapOf("refundRequestId" to refundRequestId))
                .await()
            true
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:recordRefundProcessed") }
            .getOrDefault(false)

    /**
     * Cancels a PENDING or CONFIRMED appointment server-side. Every appointment
     * in that state has already been paid, so this also flags the payment for a
     * manual refund and reverses the provider's owed balance — a direct
     * Firestore status write (the old client-side path) could not do that
     * safely. Returns true on success.
     */
    suspend fun cancelAppointment(appointmentId: String): Boolean =
        runCatching {
            functions
                .getHttpsCallable("cancelAppointment")
                .call(hashMapOf("appointmentId" to appointmentId))
                .await()
            true
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:cancelAppointment") }
            .getOrDefault(false)

    /**
     * Customer moving a booking to a new time. Server-side because appointment
     * updates are rules-denied for clients; the function validates ownership,
     * returns the booking to PENDING, and notifies the provider to re-confirm.
     */
    suspend fun rescheduleAppointment(appointmentId: String, newDateMs: Long): Boolean =
        runCatching {
            functions
                .getHttpsCallable("rescheduleAppointment")
                .call(hashMapOf("appointmentId" to appointmentId, "newDate" to newDateMs))
                .await()
            true
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:rescheduleAppointment") }
            .getOrDefault(false)

    /**
     * Provider confirming a PENDING appointment. Server-side so the status
     * flip, salon confirmed-count, customer loyalty points, and the customer's
     * notification happen in ONE atomic transaction — and so `notifications`
     * creation can stay locked to the Admin SDK (every notification doc now
     * becomes a real FCM push).
     */
    suspend fun confirmAppointment(appointmentId: String): Boolean =
        runCatching {
            functions
                .getHttpsCallable("confirmAppointment")
                .call(hashMapOf("appointmentId" to appointmentId))
                .await()
            true
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:confirmAppointment") }
            .getOrDefault(false)

    /**
     * Provider declining a PENDING appointment — same money-correctness reasons
     * as [cancelAppointment]: the booking is already paid, so this also flags
     * the payment for a manual refund instead of a bare status flip.
     */
    suspend fun providerDeclineAppointment(appointmentId: String): Boolean =
        runCatching {
            functions
                .getHttpsCallable("providerDeclineAppointment")
                .call(hashMapOf("appointmentId" to appointmentId))
                .await()
            true
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:providerDeclineAppointment") }
            .getOrDefault(false)

    /** Emits the live status ("PENDING" | "PAID" | "FAILED") of a payment. */
    fun observePaymentStatus(paymentId: String): Flow<String> = callbackFlow {
        val listener = paymentsCol.document(paymentId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    CrashReporter.recordNonFatal(err, "payment:observeStatus")
                    return@addSnapshotListener
                }
                trySend(snap?.getString("status") ?: "PENDING")
            }
        awaitClose { listener.remove() }
    }
}
