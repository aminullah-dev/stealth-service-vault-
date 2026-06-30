package com.security.stealthapp.data.firebase

import com.google.firebase.firestore.PropertyName

/**
 * Firestore document representations.
 * All fields have defaults so Firestore can deserialize via the no-arg constructor.
 */

data class UserDocument(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",                 // optional real email address
    val role: String = "CUSTOMER",          // "CUSTOMER" | "PROVIDER" | "ADMIN"
    val pinHash: String = "",               // PBKDF2(pin, salt) — for local verification
    val salt: String = "",
    val status: String = "APPROVED",        // "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED"
    val rejectionReason: String = "",       // set by admin when status becomes REJECTED
    val firebaseEmail: String = "",         // synthetic internal email for Firebase Auth
    val fcmToken: String = "",
    val createdAt: Long = 0L,
    val decoyPinHash: String = "",          // PBKDF2 hash of decoy PIN — shows fake notepad
    val decoySalt: String = "",
    val loyaltyPoints: Int = 0,             // +10 per confirmed appointment
    val profilePhotoBase64: String = "",    // legacy — kept for backward compat; prefer profilePhotoUrl
    val profilePhotoUrl: String = ""        // Firebase Storage download URL (preferred)
)

enum class LoyaltyTier { NEWCOMER, REGULAR, VIP }

fun UserDocument.loyaltyTier(): LoyaltyTier = when {
    loyaltyPoints >= 150 -> LoyaltyTier.VIP
    loyaltyPoints >= 50  -> LoyaltyTier.REGULAR
    else                 -> LoyaltyTier.NEWCOMER
}

data class WorkingHours(
    val dayOfWeek: Int = 2,
    // Same JavaBeans issue as SalonDocument.isAvailable — must force the field name.
    @get:PropertyName("isOpen") @set:PropertyName("isOpen")
    var isOpen: Boolean = false,
    val openHour: Int = 9,
    val openMinute: Int = 0,
    val closeHour: Int = 18,
    val closeMinute: Int = 0
)

data class SalonDocument(
    val id: String = "",                    // Firestore document ID (set after read)
    val providerId: String = "",
    val providerName: String = "",
    val salonName: String = "",
    val district: String = "",
    val services: List<String> = emptyList(),
    // @PropertyName forces Firestore to use "isAvailable" as the field name.
    // Without it, the JavaBeans convention for Boolean getters strips the "is"
    // prefix, storing the field as "available" instead and breaking all queries.
    @get:PropertyName("isAvailable") @set:PropertyName("isAvailable")
    var isAvailable: Boolean = false,
    val rating: Double = 0.0,
    val workingHours: List<WorkingHours> = emptyList(),
    val slotDurationMinutes: Int = 60,
    val pricePerService: Map<String, Int> = emptyMap(),
    val confirmedCount: Int = 0,
    // Same JavaBeans issue — must force "isVerified" so Firestore doesn't strip "is".
    @get:PropertyName("isVerified") @set:PropertyName("isVerified")
    var isVerified: Boolean = false
)

enum class SalonBadge { NONE, SILVER, GOLD, VERIFIED }

fun SalonDocument.badge(): SalonBadge = when {
    isVerified                            -> SalonBadge.VERIFIED
    rating >= 4.5 && confirmedCount >= 25 -> SalonBadge.GOLD
    rating >= 4.0 && confirmedCount >= 10 -> SalonBadge.SILVER
    else                                  -> SalonBadge.NONE
}

data class AppointmentDocument(
    val id: String = "",                    // Firestore document ID
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val serviceName: String = "",
    val appointmentDate: Long = 0L,         // epoch millis (date + time)
    val status: String = "PENDING",         // "PENDING" | "CONFIRMED" | "CANCELLED"
    val createdAt: Long = 0L,
    val notes: String = ""               // optional customer request/note
)

data class ReviewDocument(
    val id: String = "",                    // Firestore document ID
    val salonId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val rating: Int = 0,                    // 1–5 stars
    val comment: String = "",
    val createdAt: Long = 0L,
    val providerReply: String = "",         // Provider's response (blank = not replied)
    val repliedAt: Long = 0L
)

/**
 * One portfolio / sample-work photo for a salon. Stored in its own
 * "salon_gallery" collection (one image per document) so each Base64 payload
 * stays well under the 1 MB Firestore document limit and a salon can have many.
 */
data class GalleryImageDocument(
    val id: String = "",                    // Firestore document ID
    val salonId: String = "",
    val imageBase64: String = "",           // legacy — kept for backward compat; prefer imageUrl
    val imageUrl: String = "",              // Firebase Storage download URL (preferred)
    val storagePath: String = "",           // Storage path used to delete the file
    val createdAt: Long = 0L
)

data class BroadcastDocument(
    val id: String = "",                    // Firestore document ID
    val message: String = "",
    val sentBy: String = "admin",
    val createdAt: Long = 0L
)

data class WaitlistEntry(
    val id: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val requestedDate: Long = 0L,           // start-of-day millis for the requested date
    val status: String = "WAITING",         // "WAITING" | "SLOT_AVAILABLE" | "EXPIRED"
    val createdAt: Long = 0L
)

data class NotificationDocument(
    val id: String = "",
    val recipientId: String = "",
    val type: String = "SYSTEM",            // "BOOKING_CONFIRMED" | "BOOKING_CANCELLED" | "NEW_BOOKING" | "WAITLIST" | "SYSTEM"
    val title: String = "",
    val body: String = "",
    // Boolean field starting with "is" requires explicit @PropertyName to avoid JavaBeans stripping.
    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false,
    val createdAt: Long = 0L,
    val relatedId: String = ""              // optional appointmentId or other related doc id
)

/**
 * One payment record per booking. Created and updated exclusively by the
 * createPaymentSession / hesabPayWebhook Cloud Functions — the client only reads
 * it to observe payment status. The amount is split into the platform's
 * commission and the provider's net (commission % is set globally by the admin).
 *
 * status: "PENDING" | "PAID" | "FAILED"
 */
data class PaymentDocument(
    val id: String = "",
    val appointmentId: String = "",
    val customerId: String = "",
    val providerId: String = "",
    val salonId: String = "",
    val serviceName: String = "",
    val amount: Long = 0L,                  // total charged to the customer
    val commissionPercent: Double = 0.0,
    val commissionAmount: Long = 0L,        // platform's cut
    val providerNet: Long = 0L,             // what the provider is owed
    val currency: String = "AFN",
    val status: String = "PENDING",
    val hesabSessionId: String = "",
    val createdAt: Long = 0L,
    val paidAt: Long = 0L
)

/**
 * Singleton platform-wide settings document at platform_config/general.
 * Only admins may write it (see firestore.rules); the commission percent is
 * read server-side by the payment Cloud Function so the client can't tamper.
 */
data class PlatformConfigDocument(
    val commissionPercent: Double = 10.0
)

/**
 * Running ledger of what the platform owes each provider. Maintained by the
 * hesabPayWebhook Cloud Function (clients can't write it). owedAmount
 * accumulates each booking's providerNet; the admin reads this to know payouts.
 */
data class ProviderBalance(
    val providerId: String = "",
    val owedAmount: Long = 0L,
    val updatedAt: Long = 0L,
    // Joined in client-side for display — not stored on the doc.
    val providerName: String = ""
)

/**
 * History row for a platform→provider payout. Written only by the
 * recordProviderPayout Cloud Function; admins read all, providers read own.
 */
data class PayoutDocument(
    val id: String = "",
    val providerId: String = "",
    val amount: Long = 0L,
    val method: String = "MANUAL",
    val paidBy: String = "",
    val createdAt: Long = 0L,
    // Joined client-side for display.
    val providerName: String = ""
)

/**
 * A customer-cancellation refund awaiting manual processing. Written only by
 * the cancelAppointment Cloud Function; admins mark it PROCESSED via
 * recordRefundProcessed once the money has actually been sent back (HesabPay
 * has no automated refund API wired).
 */
data class RefundRequestDocument(
    val id: String = "",
    val appointmentId: String = "",
    val paymentId: String = "",
    val customerId: String = "",
    val providerId: String = "",
    val salonId: String = "",
    val amount: Long = 0L,
    val status: String = "PENDING",          // "PENDING" | "PROCESSED"
    val createdAt: Long = 0L,
    // Joined client-side for display.
    val customerName: String = "",
    val salonName: String = ""
)
