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
 * Result of asking the backend to start a booking + payment.
 * For an "ONLINE" booking, [checkoutUrl] is opened in a browser / Custom Tab.
 * For a "CASH" booking the appointment is already confirmed server-side and
 * [checkoutUrl] is blank — nothing to open, just show the confirmation.
 */
data class CheckoutSession(
    val paymentId: String,
    val appointmentId: String = "",   // empty for a gift card (no appointment)
    val checkoutUrl: String,
    val method: String,
    val amount: Long,            // final amount charged (after any discount)
    val listPrice: Long = 0L,        // original service price before discount
    val discountAmount: Long = 0L,   // promo discount applied (0 if none)
    val commissionAmount: Long = 0L,
    val providerNet: Long = 0L
)

/** Result of validating a promo code before booking (see previewPromo). */
data class PromoPreview(
    val valid: Boolean,
    val code: String,
    val listPrice: Long,
    val discountAmount: Long,
    val finalPrice: Long,
    val errorMessage: String? = null
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
     * Asks the backend to create a booking, either via a HesabPay checkout
     * session ([method] = "ONLINE") or confirmed immediately for in-person cash
     * payment ([method] = "CASH", server debits the platform's commission from
     * the provider's payout balance since no online transaction occurs).
     * Returns null on failure.
     */
    suspend fun createCheckout(
        salonId: String,
        serviceNames: List<String>,
        appointmentDateMs: Long,
        notes: String,
        email: String,
        method: String = "ONLINE",
        promoCode: String = "",
        staffId: String = "",
        packageId: String = ""
    ): CheckoutSession? = runCatching {
        val payload = hashMapOf(
            "salonId" to salonId,
            "serviceNames" to serviceNames,
            "appointmentDate" to appointmentDateMs,
            "notes" to notes,
            "email" to email,
            "method" to method,
            "promoCode" to promoCode,
            "staffId" to staffId,
            "packageId" to packageId
        )
        val result = functions
            .getHttpsCallable("createPaymentSession")
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val map = result.getData() as? Map<String, Any?> ?: return@runCatching null

        val session = CheckoutSession(
            paymentId        = map["paymentId"] as? String ?: "",
            appointmentId    = map["appointmentId"] as? String ?: "",
            checkoutUrl      = map["checkoutUrl"] as? String ?: "",
            method           = map["method"] as? String ?: method,
            amount           = (map["amount"] as? Number)?.toLong() ?: 0L,
            listPrice        = (map["listPrice"] as? Number)?.toLong() ?: 0L,
            discountAmount   = (map["discountAmount"] as? Number)?.toLong() ?: 0L,
            commissionAmount = (map["commissionAmount"] as? Number)?.toLong() ?: 0L,
            providerNet      = (map["providerNet"] as? Number)?.toLong() ?: 0L
        )
        // Online bookings must have a checkout URL to be usable; cash bookings
        // never have one (nothing to open) and are already confirmed.
        session.takeIf { it.method == "CASH" || it.checkoutUrl.isNotBlank() }
    }.onFailure { CrashReporter.recordNonFatal(it, "payment:createCheckout") }
        .getOrNull()

    /**
     * Buys a gift card of [amount] AFN for the user with [recipientPhone]. On
     * payment the recipient's wallet credit increases (server-side, via the
     * webhook). Returns a CheckoutSession whose checkoutUrl the caller opens, or
     * null with the [Result] on failure (e.g. unknown recipient → the callable
     * throws, surfaced by the caller via the returned Result).
     */
    suspend fun createGiftCard(recipientPhone: String, amount: Long, message: String): Result<CheckoutSession> =
        runCatching {
            val result = functions
                .getHttpsCallable("createGiftCardSession")
                .call(hashMapOf(
                    "recipientPhone" to recipientPhone,
                    "amount"         to amount,
                    "message"        to message
                ))
                .await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?> ?: emptyMap()
            CheckoutSession(
                paymentId   = map["paymentId"] as? String ?: "",
                checkoutUrl = map["checkoutUrl"] as? String ?: "",
                method      = "ONLINE",
                amount      = (map["amount"] as? Number)?.toLong() ?: amount
            )
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:createGiftCard") }

    /**
     * Sends an [amount] AFN tip for the caller's own booking [appointmentId]. The
     * whole amount goes to the provider (server credits their balance on the
     * webhook). Returns a CheckoutSession whose checkoutUrl the caller opens.
     */
    suspend fun sendTip(appointmentId: String, amount: Long): Result<CheckoutSession> =
        runCatching {
            val result = functions
                .getHttpsCallable("createTipSession")
                .call(hashMapOf(
                    "appointmentId" to appointmentId,
                    "amount"        to amount
                ))
                .await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?> ?: emptyMap()
            CheckoutSession(
                paymentId   = map["paymentId"] as? String ?: "",
                checkoutUrl = map["checkoutUrl"] as? String ?: "",
                method      = "ONLINE",
                amount      = (map["amount"] as? Number)?.toLong() ?: amount
            )
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:sendTip") }

    /**
     * Redeems [points] loyalty points into wallet credit (server-side, in whole
     * 100s at 1 AFN each). Returns a failed [Result] if the backend rejects it
     * (too few points), which the caller surfaces to the user.
     */
    suspend fun redeemLoyalty(points: Int): Result<Unit> =
        runCatching {
            functions
                .getHttpsCallable("redeemLoyaltyPoints")
                .call(hashMapOf("points" to points))
                .await()
            Unit
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:redeemLoyalty") }

    /**
     * Claims the one-time profile-completion loyalty bonus. Safe to call whenever
     * the profile looks complete — the backend awards it at most once. Returns
     * true only when points were actually granted (so the UI can celebrate once).
     */
    suspend fun claimProfileReward(): Boolean =
        runCatching {
            val result = functions
                .getHttpsCallable("claimProfileReward")
                .call()
                .await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?> ?: emptyMap()
            map["awarded"] as? Boolean ?: false
        }.getOrDefault(false)

    /**
     * Validates a promo [code] against a salon service before booking, so the
     * customer sees the discount applied up front. Returns a PromoPreview with
     * valid=false and a message when the code is rejected by the backend.
     */
    suspend fun previewPromo(code: String, salonId: String, serviceNames: List<String>): PromoPreview =
        runCatching {
            val result = functions
                .getHttpsCallable("previewPromo")
                .call(hashMapOf("code" to code, "salonId" to salonId, "serviceNames" to serviceNames))
                .await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?> ?: emptyMap()
            PromoPreview(
                valid          = map["valid"] == true,
                code           = map["code"] as? String ?: code.uppercase(),
                listPrice      = (map["listPrice"] as? Number)?.toLong() ?: 0L,
                discountAmount = (map["discountAmount"] as? Number)?.toLong() ?: 0L,
                finalPrice     = (map["finalPrice"] as? Number)?.toLong() ?: 0L
            )
        }.getOrElse { e ->
            // FirebaseFunctionsException carries the server's friendly message.
            val msg = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                ?: e.message
            PromoPreview(false, code.uppercase(), 0L, 0L, 0L, errorMessage = msg)
        }

    /** Admin-only: create or update a promo code. Returns true on success. */
    suspend fun upsertPromoCode(
        code: String,
        discountPercent: Int,
        discountAmount: Long,
        maxUses: Int,
        expiresAt: Long,
        active: Boolean
    ): Boolean = runCatching {
        functions.getHttpsCallable("upsertPromoCode").call(
            hashMapOf(
                "code" to code,
                "discountPercent" to discountPercent,
                "discountAmount" to discountAmount,
                "maxUses" to maxUses,
                "expiresAt" to expiresAt,
                "active" to active
            )
        ).await()
        true
    }.onFailure { CrashReporter.recordNonFatal(it, "payment:upsertPromoCode") }
        .getOrDefault(false)

    /** Admin-only: enable/disable a promo code. Returns true on success. */
    suspend fun setPromoActive(code: String, active: Boolean): Boolean = runCatching {
        functions.getHttpsCallable("setPromoActive")
            .call(hashMapOf("code" to code, "active" to active))
            .await()
        true
    }.onFailure { CrashReporter.recordNonFatal(it, "payment:setPromoActive") }
        .getOrDefault(false)

    /**
     * Provider-only: leaves post-appointment feedback about a customer — an
     * optional 1–5 [rating], an optional [noShow] flag, and an optional [flagged]
     * escalation to admin, with a [comment]. One report per appointment
     * (enforced server-side). Returns true on success.
     */
    suspend fun reportCustomer(
        appointmentId: String,
        rating: Int,
        noShow: Boolean,
        flagged: Boolean,
        comment: String
    ): Boolean = runCatching {
        functions.getHttpsCallable("reportCustomer").call(
            hashMapOf(
                "appointmentId" to appointmentId,
                "rating" to rating,
                "noShow" to noShow,
                "flagged" to flagged,
                "comment" to comment
            )
        ).await()
        true
    }.onFailure { CrashReporter.recordNonFatal(it, "payment:reportCustomer") }
        .getOrDefault(false)

    /**
     * Admin-only: closes out a flagged customer report. When [suspend] is true
     * the reported customer's account is suspended; otherwise the report is
     * simply dismissed. Either way it leaves the open-reports queue. Returns
     * true on success.
     */
    suspend fun resolveCustomerReport(reportId: String, suspend: Boolean): Boolean = runCatching {
        functions.getHttpsCallable("resolveCustomerReport").call(
            hashMapOf("reportId" to reportId, "suspend" to suspend)
        ).await()
        true
    }.onFailure { CrashReporter.recordNonFatal(it, "payment:resolveCustomerReport") }
        .getOrDefault(false)

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

    /**
     * Admin-only: approves or rejects a user's KYC submission. On reject, a
     * [rejectionReason] is required and shown to the user. Returns true on success.
     */
    suspend fun reviewKyc(targetUid: String, approve: Boolean, rejectionReason: String = ""): Boolean =
        runCatching {
            functions
                .getHttpsCallable("reviewKyc")
                .call(
                    hashMapOf(
                        "targetUid"       to targetUid,
                        "approve"         to approve,
                        "rejectionReason" to rejectionReason
                    )
                )
                .await()
            true
        }.onFailure { CrashReporter.recordNonFatal(it, "payment:reviewKyc") }
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
