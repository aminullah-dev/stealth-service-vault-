package com.safebeauty.app.data.firebase

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
    val loyaltyPoints: Int = 0,             // +10 per confirmed appointment
    val profilePhotoBase64: String = "",    // legacy — kept for backward compat; prefer profilePhotoUrl
    val profilePhotoUrl: String = "",       // Firebase Storage download URL (preferred)
    // Provider's HesabPay account number (phone number), where the admin sends
    // payouts. Not yet used to automate a transfer via the HesabPay Multi-Vendor
    // Payment API — just recorded so the admin knows where to send money for
    // the current manual payout flow.
    val hesabAccountNumber: String = "",
    // ── Identity verification (KYC) ────────────────────────────────────────────
    // Sensitive PII — the tazkira/selfie photos live in a private Storage path
    // (kyc/{uid}/…) readable only by the owner and admins. All KYC fields below
    // are written ONLY by the submitKyc / reviewKyc Cloud Functions; the rules
    // freeze them against direct client writes so a user can't self-approve.
    val kycStatus: String = "NONE",         // NONE | PENDING | APPROVED | REJECTED
    val kycRejectionReason: String = "",
    val tazkiraNumber: String = "",
    // Storage paths, not URLs — "kyc/{uid}/tazkira.jpg". Written by submitKyc,
    // which derives them from the caller's own uid, and read back through the
    // Storage SDK so that storage.rules is consulted on every access.
    val tazkiraPhotoPath: String = "",
    val selfiePhotoPath: String = "",
    // The token URLs these replaced. Kept only so a document written before the
    // change still renders while the backfill works through them; nothing
    // writes them any more, and adminRevokeKycUrls empties them for good.
    val tazkiraPhotoUrl: String = "",
    val selfiePhotoUrl: String = "",
    val addressProvince: String = "",
    val addressDetail: String = "",
    // Admin-curated identity details (filled in by the platform admin from the
    // uploaded tazkira). Written only by the adminUpdateUser Cloud Function;
    // frozen against client self-edits in firestore.rules.
    val birthYear: String = "",
    val tazkiraIssueDate: String = "",
    val tazkiraExpiryDate: String = "",
    // ── Referral program ───────────────────────────────────────────────────────
    // referralCode is this user's own shareable code (derived from their uid at
    // registration). referredBy is the code they signed up with (blank if none).
    // referralCredit (AFN) is a server-controlled balance auto-applied at
    // checkout — the rules freeze it against client writes, so only the
    // reviewKyc / createPaymentSession Cloud Functions can change it. Rewards are
    // granted when the referred user's identity is verified (see reviewKyc), which
    // gates against fake-account farming since KYC needs a real tazkira + selfie.
    val referralCode: String = "",
    val referredBy: String = "",
    val referralCredit: Long = 0L,
    val referralRewarded: Boolean = false,
    // Set once by the claimProfileReward Cloud Function when the profile-completion
    // loyalty bonus is granted (frozen against client writes in firestore.rules).
    val profileRewardClaimed: Boolean = false,
    // ── Customer reputation (two-way ratings) ───────────────────────────────────
    // Providers rate/report customers after an appointment via the reportCustomer
    // Cloud Function; these aggregates are server-controlled (frozen against
    // client writes in firestore.rules) so a provider can't arbitrarily tank a
    // customer — every change is tied to a real, one-per-appointment report.
    // Average = customerRatingSum / customerRatingCount.
    val customerRatingSum: Long = 0L,
    val customerRatingCount: Int = 0,
    val noShowCount: Int = 0,
    // ── Re-engagement (server-controlled, frozen against client writes) ──────────
    // lastVisitAt is stamped by completePastAppointments when an appointment flips
    // to COMPLETED; sendReengagementNudges scans for customers idle ≥30 days and
    // stamps lastNudgedAt so a "we miss you" nudge fires at most once per 30 days.
    val lastVisitAt: Long = 0L,
    val lastNudgedAt: Long = 0L
)

/** Average customer rating (0.0 if never rated). */
fun UserDocument.customerRating(): Double =
    if (customerRatingCount > 0) customerRatingSum.toDouble() / customerRatingCount else 0.0

/**
 * A provider's post-appointment feedback about a customer. Written only by the
 * reportCustomer Cloud Function (one per appointment). MISCONDUCT reports carry
 * status OPEN for admin review; ratings/no-shows are informational and settle
 * into the customer's user-doc aggregates.
 */
data class CustomerReportDocument(
    val id: String = "",
    val appointmentId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val providerId: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val rating: Int = 0,               // 1–5, or 0 if none given
    val noShow: Boolean = false,
    val flagged: Boolean = false,      // escalated to admin (misconduct)
    val comment: String = "",
    val status: String = "OPEN",       // "OPEN" | "REVIEWED" (only relevant when flagged)
    val createdAt: Long = 0L
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

/**
 * A single staff member (stylist / beautician) working at a salon. Stored as an
 * array on the salon document — a salon has only a handful of staff, so an
 * embedded list avoids an extra collection, extra reads, and extra rules. A
 * customer may book a specific staff member; "active = false" hides them from
 * the booking picker without losing their history.
 */
/**
 * A discounted bundle of services a salon offers (e.g. "4 nail sessions, 15% off").
 * Booking all of a package's [services] together applies [discountPercent] off
 * those services' subtotal at checkout (server-authoritative, tested in money.js).
 */
data class ServicePackage(
    val id: String = "",
    val name: String = "",
    val services: List<String> = emptyList(),
    val discountPercent: Int = 0
)

/**
 * How one service divides between the stylist working and the client waiting.
 *
 * Minutes. Total duration is the sum; [durationPerService] stays the fallback for
 * services that have no breakdown. During [processing] the client is still in the
 * salon but the stylist is free to take someone else — which is the whole reason
 * this exists.
 */
data class ServiceTiming(
    val activeBefore: Int = 0,
    val processing: Int = 0,
    val activeAfter: Int = 0,
) {
    val totalMinutes: Int get() = activeBefore + processing + activeAfter
}

data class StaffMember(
    val id: String = "",                    // stable UUID, generated when added
    val name: String = "",
    val specialty: String = "",             // e.g. "Hair", "Makeup", "Nails"
    val photoUrls: List<String> = emptyList(), // portfolio / sample-work photos
    @get:PropertyName("active") @set:PropertyName("active")
    var active: Boolean = true
)

data class SalonDocument(
    val id: String = "",                    // Firestore document ID (set after read)
    val providerId: String = "",
    val providerName: String = "",
    val salonName: String = "",
    val district: String = "",
    // Derived server-side by deriveSalonFields from `district` and `services`,
    // and frozen against client writes. These are what the category and
    // neighbourhood filters can actually match on: `district` is free text on
    // older salons, and `services` is free text on all of them.
    val districtKey: String = "",
    // Derived server-side from districtKey's prefix (KBL_, HRT_, …). Never set by
    // the salon: two fields that can disagree about where a salon is would be
    // one field too many.
    val city: String = "",
    val categories: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    // Staff who work here. Empty = a solo salon (the classic single-chair case);
    // the booking flow only shows a staff picker when this has active members.
    val staff: List<StaffMember> = emptyList(),
    // @PropertyName forces Firestore to use "isAvailable" as the field name.
    // Without it, the JavaBeans convention for Boolean getters strips the "is"
    // prefix, storing the field as "available" instead and breaking all queries.
    @get:PropertyName("isAvailable") @set:PropertyName("isAvailable")
    var isAvailable: Boolean = false,
    val rating: Double = 0.0,
    val workingHours: List<WorkingHours> = emptyList(),
    val slotDurationMinutes: Int = 60,
    val pricePerService: Map<String, Int> = emptyMap(),
    // Optional per-service duration in minutes (parallel to pricePerService).
    // A service with no entry (or 0) falls back to one slot (slotDurationMinutes),
    // so an unset map behaves exactly like the old one-slot-per-service model.
    val durationPerService: Map<String, Int> = emptyMap(),
    // Where a service leaves the stylist free. Colouring hair is application,
    // then development while she is elsewhere, then washing and styling — and
    // that middle stretch is the most valuable unsold time in the salon. A
    // service with no entry here is treated as working throughout, which is what
    // every salon means today.
    val serviceTiming: Map<String, ServiceTiming> = emptyMap(),
    // Days the salon is closed off (time-off/holidays), as "yyyy-MM-dd" strings in
    // Kabul-local time. No slots are offered on these days, and the booking
    // function rejects them server-side as defense in depth.
    val blockedDates: List<String> = emptyList(),
    // Last-minute deal: an automatic discount for booking a slot within
    // lastMinuteWindowHours of now, to help fill empty chairs. Applied server-side
    // at checkout (createPaymentSession, tested in lib/money.js).
    @get:PropertyName("lastMinuteEnabled") @set:PropertyName("lastMinuteEnabled")
    var lastMinuteEnabled: Boolean = false,
    val lastMinutePercent: Int = 0,
    val lastMinuteWindowHours: Int = 0,
    // Discounted service bundles (Feature: packages). Booking a package applies
    // its discount at checkout server-side.
    val packages: List<ServicePackage> = emptyList(),
    // Cover photo for the browse card — one of the salon_gallery image URLs the
    // provider marks as the cover. Empty ⇒ the card/sheet fall back to the
    // gradient monogram. Just a stored URL, so it reuses the existing public
    // gallery path (no new Storage path or rule needed).
    val coverImageUrl: String = "",
    val confirmedCount: Int = 0,
    // Geographic location (set by the provider from their device GPS). 0/0 means
    // "not set" — the map/distance features simply skip such salons.
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // Same JavaBeans issue — must force "isVerified" so Firestore doesn't strip "is".
    @get:PropertyName("isVerified") @set:PropertyName("isVerified")
    var isVerified: Boolean = false
)

/** Bookable staff (active only). Empty for a solo salon. */
fun SalonDocument.activeStaff(): List<StaffMember> = staff.filter { it.active }

/** True once the provider has pinned the salon's location. */
fun SalonDocument.hasLocation(): Boolean = latitude != 0.0 || longitude != 0.0

enum class SalonBadge { NONE, SILVER, GOLD, VERIFIED }

fun SalonDocument.badge(): SalonBadge = when {
    isVerified                            -> SalonBadge.VERIFIED
    rating >= 4.5 && confirmedCount >= 25 -> SalonBadge.GOLD
    rating >= 4.0 && confirmedCount >= 10 -> SalonBadge.SILVER
    else                                  -> SalonBadge.NONE
}

data class AppointmentDocument(
    val id: String = "",                    // Firestore document ID
    // The short reference a customer can read down a phone line ("SB-4C7GHJ").
    // Minted server-side at booking; blank on bookings that predate it.
    val bookingCode: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val serviceName: String = "",
    // The staff member this booking is for. Empty = "any available" / solo salon.
    val staffId: String = "",
    val staffName: String = "",
    val appointmentDate: Long = 0L,         // epoch millis (date + time)
    val status: String = "PENDING",         // "AWAITING_PAYMENT" | "PENDING" | "CONFIRMED" | "COMPLETED" | "CANCELLED"
    val createdAt: Long = 0L,
    val notes: String = "",              // optional customer request/note
    // Denormalized from the payment doc at booking time so the provider's
    // requests list can show a "Cash" badge without an extra read per row.
    val paymentMethod: String = "ONLINE",   // "ONLINE" | "CASH"
    // Customer's reputation, snapshotted at booking time so the provider can see
    // who they're accepting without reading the customer's (rules-protected) user
    // doc. Set to false once the provider has left feedback (see reportCustomer).
    val customerRatingSum: Long = 0L,
    val customerRatingCount: Int = 0,
    val noShowCount: Int = 0,
    @get:PropertyName("customerReported") @set:PropertyName("customerReported")
    var customerReported: Boolean = false
)

/** Snapshotted average customer rating for this booking (0.0 if never rated). */
fun AppointmentDocument.customerRating(): Double =
    if (customerRatingCount > 0) customerRatingSum.toDouble() / customerRatingCount else 0.0

/**
 * A support request from one user (customer or provider) to the platform admin.
 * There is one ticket per user (doc id = the user's app uid), reopened/refreshed
 * each time they contact support about a booking. The actual conversation lives
 * in chat_messages under conversationId "support_{userId}"; this doc is the
 * admin's inbox row — who, about what, and whether it still needs attention.
 */
data class SupportTicket(
    val id: String = "",                    // = userId (one open ticket per user)
    val userId: String = "",
    val userName: String = "",
    val userRole: String = "",              // "CUSTOMER" | "PROVIDER"
    val relatedInfo: String = "",           // e.g. "Haircut · Glam Salon"
    val status: String = "OPEN",            // "OPEN" | "CLOSED"
    val updatedAt: Long = 0L,
    @get:PropertyName("unreadForAdmin") @set:PropertyName("unreadForAdmin")
    var unreadForAdmin: Boolean = false
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
    val repliedAt: Long = 0L,
    val imageUrls: List<String> = emptyList() // Optional review photos (Storage URLs, max 3)
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

/**
 * One post in the social discovery feed — a before/after or sample-work photo a
 * salon shares. Followers (favoriters) are notified server-side when it's created
 * (pushPostToFollowers). Stored in its own `salon_posts` collection.
 */
data class SalonPostDocument(
    val id: String = "",
    val salonId: String = "",
    val providerId: String = "",
    val salonName: String = "",
    val imageUrl: String = "",
    val storagePath: String = "",
    val caption: String = "",
    val createdAt: Long = 0L,
    // Maintained by the countPostLike / countPostComment triggers, never by a
    // client — salon_posts allows no client update at all.
    val likeCount: Int = 0,
    val commentCount: Int = 0
)

/**
 * One comment under a Discover post.
 *
 * [authorName] is denormalized so a thread renders without a user lookup per
 * row; the security rules check it against the author's own user document on
 * write, so it cannot be signed with somebody else's name.
 */
data class PostCommentDocument(
    val id: String = "",
    val postId: String = "",
    val salonId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)

/**
 * A salon's 24-hour story — "two chairs free this afternoon".
 *
 * Deliberately its own collection rather than a short-lived OfferDocument:
 * offers are gated behind identity verification for customers, and an empty
 * chair needs to reach exactly the people who have not verified yet. Stories
 * carry no discount and no money, so they need no gate.
 *
 * [expiresAt] is written by the author, not inferred at read time, so a story's
 * lifetime survives clock differences between devices.
 */
data class StoryDocument(
    val id: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val text: String = "",                  // the announcement itself
    val imageUrl: String = "",              // optional
    val storagePath: String = "",           // for deletion
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L                // createdAt + 24h
)

data class BroadcastDocument(
    val id: String = "",                    // Firestore document ID
    val message: String = "",
    val sentBy: String = "admin",
    val createdAt: Long = 0L
)

/**
 * A provider-posted promotion for their own salon (collection `salon_offers`).
 * INFORMATIONAL only in v1: [discountPercent]/[discountAmount] render as a badge
 * but never change the checkout price (the server recomputes from pricePerService).
 * `active` is a plain field (not an `isX` getter) so no @PropertyName is needed.
 */
data class OfferDocument(
    val id: String = "",                    // Firestore document ID
    val salonId: String = "",
    val providerId: String = "",
    val salonName: String = "",
    val title: String = "",                 // e.g. "۲۰٪ تخفیف ناخن این هفته"
    val description: String = "",
    val service: String = "",               // optional: one of salon.services
    val discountPercent: Int = 0,           // badge only (0 = none)
    val discountAmount: Int = 0,            // badge only, AFN (0 = none)
    val expiresAt: Long = 0L,               // 0 = never expires
    val active: Boolean = true,
    val createdAt: Long = 0L
) {
    /** True when the offer is switched on and not past its expiry. */
    fun isLive(now: Long = System.currentTimeMillis()): Boolean =
        active && (expiresAt == 0L || expiresAt > now)
}

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
 * status: "PENDING" | "PAID" | "FAILED" | "PENDING_CASH" | "CANCELLED" | "REFUND_PENDING"
 * method: "ONLINE" (paid via HesabPay) | "CASH" (paid in person at the salon —
 *   the platform never receives the money, so its commission is instead
 *   debited from the provider's payout balance; see createPaymentSession).
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
    val method: String = "ONLINE",
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
 * A discount code. Document ID is the uppercased code string. Written only by
 * the upsertPromoCode / setPromoActive Cloud Functions; the customer never reads
 * these (they validate via previewPromo) — only the admin management screen does.
 * A code carries EITHER a percentage OR a fixed AFN discount (percentage wins if
 * both are set). maxUses/expiresAt of 0 mean unlimited / never-expires.
 */
data class PromoDocument(
    val code: String = "",
    val discountPercent: Int = 0,
    val discountAmount: Long = 0L,
    val maxUses: Int = 0,
    val usedCount: Int = 0,
    val expiresAt: Long = 0L,
    // JavaBeans strips "is" from Boolean getters — force the stored field name.
    @get:PropertyName("active") @set:PropertyName("active")
    var active: Boolean = true,
    val createdAt: Long = 0L
)

/**
 * Running ledger of what the platform owes each provider. Maintained by the
 * hesabPayWebhook / createPaymentSession Cloud Functions (clients can't write
 * it). owedAmount increases by providerNet on each online payment and
 * decreases by commissionAmount on each cash booking (the platform's cut on a
 * cash sale can only be recovered by deducting it from a future online-payout,
 * since no money changed hands online) — so it CAN go negative, meaning the
 * provider currently owes the platform rather than the other way around.
 */
data class ProviderBalance(
    val providerId: String = "",
    val owedAmount: Long = 0L,
    val updatedAt: Long = 0L,
    // Joined in client-side for display — not stored on the doc.
    val providerName: String = "",
    val hesabAccountNumber: String = ""
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

/**
 * A salon's running booking tally, maintained by the deriveSalonStats trigger.
 *
 * Exists so the provider's Income tab can show lifetime totals without reading
 * every appointment the salon has ever taken in order to count them. One
 * document per salon, so reading it costs the same whether the salon has ten
 * bookings or ten thousand.
 *
 * Counts are Long because Firestore's increment is an int64; the UI narrows
 * them where it displays them.
 */
data class SalonStatsDocument(
    val salonId: String = "",
    val total: Long = 0L,
    /** Bookings per status: PENDING, CONFIRMED, COMPLETED, CANCELLED, … */
    val byStatus: Map<String, Long> = emptyMap(),
    val byService: Map<String, Long> = emptyMap(),
    /** Only CONFIRMED and COMPLETED — what the revenue estimate is built from. */
    val confirmedByService: Map<String, Long> = emptyMap(),
    val updatedAt: Long = 0L,
)
