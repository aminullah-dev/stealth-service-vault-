package com.safebeauty.app.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.db.dao.SalonCacheDao
import com.safebeauty.app.data.db.entities.toEntity
import com.safebeauty.app.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All live queries use AT MOST a single equality filter and then do any
 * remaining filtering/sorting in memory. This deliberately avoids Firestore
 * composite indexes — combining a where-filter with an orderBy on a different
 * field requires a pre-built composite index, and a missing index surfaces as
 * a FAILED_PRECONDITION error inside the snapshot listener. The collections in
 * this app are small, so in-memory sorting is negligible and removes a whole
 * class of runtime crashes.
 *
 * Listeners also emit an empty result on error (instead of closing the flow),
 * so a transient permission/network error can never propagate up through
 * stateIn and crash the app.
 */
@Singleton
class FirestoreRepository @Inject constructor(
    private val salonCacheDao: SalonCacheDao
) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    private val usersCol        = db.collection("users")
    private val salonsCol       = db.collection("salons")
    private val appointmentsCol = db.collection("appointments")
    private val chatCol         = db.collection("chat_messages")
    private val reviewsCol      = db.collection("reviews")
    private val broadcastsCol   = db.collection("broadcasts")
    private val galleryCol      = db.collection("salon_gallery")
    private val postsCol        = db.collection("salon_posts")
    private val offersCol       = db.collection("salon_offers")
    private val waitlistCol      = db.collection("waitlist")
    private val notificationsCol = db.collection("notifications")
    private val platformConfigCol  = db.collection("platform_config")
    private val providerBalancesCol = db.collection("provider_balances")
    private val payoutsCol           = db.collection("payouts")
    private val refundRequestsCol    = db.collection("refund_requests")
    private val promoCodesCol        = db.collection("promo_codes")
    private val supportTicketsCol    = db.collection("support_tickets")
    private val favoritesCol         = db.collection("favorites")

    // ── Users ─────────────────────────────────────────────────────────────────

    // NOTE: PIN authentication and the pre-auth phone lookup moved to Cloud
    // Functions (authenticateWithPin / lookupAccountByPhone). The client no
    // longer bulk-reads the users collection, so `users` reads are locked to
    // owner/admin in firestore.rules. getUserById below only ever reads the
    // caller's OWN document.

    suspend fun createUser(user: UserDocument) {
        usersCol.document(user.uid).set(user).await()
    }

    /** True if any account already uses [phone] — the login identifier must be unique. */
    suspend fun phoneExists(phone: String): Boolean =
        runCatching {
            !usersCol.whereEqualTo("phone", phone).limit(1).get().await().isEmpty
        }.getOrDefault(false)

    suspend fun getUserById(uid: String): UserDocument? {
        return usersCol.document(uid).get().await()
            .toObject(UserDocument::class.java)?.copy(uid = uid)
    }

    suspend fun setUserStatus(uid: String, status: String) {
        usersCol.document(uid).update("status", status).await()
    }

    suspend fun suspendUser(uid: String)   = setUserStatus(uid, "SUSPENDED")
    suspend fun unsuspendUser(uid: String) = setUserStatus(uid, "APPROVED")

    /** Rejects a pending provider application with a reason shown on their AccountStatusScreen. */
    suspend fun rejectProvider(uid: String, reason: String) {
        usersCol.document(uid)
            .update(mapOf("status" to "REJECTED", "rejectionReason" to reason))
            .await()
    }

    suspend fun updateUserName(uid: String, name: String) {
        usersCol.document(uid).update("name", name).await()
    }

    // NOTE: PIN hash updates go through the updatePinHash Cloud Function — a
    // direct client write can be rules-denied after the Firebase Auth password
    // has already changed (Forgot-PIN signs in fresh, before uid_map exists),
    // which would desync the two credentials and lock the account out.

    /** Legacy Base64 path — kept so existing photos still display after migration. */
    suspend fun updateUserPhoto(uid: String, base64: String) {
        usersCol.document(uid).update("profilePhotoBase64", base64).await()
    }

    /** New Storage path — stores the HTTPS download URL returned by [StorageRepository]. */
    suspend fun updateUserPhotoUrl(uid: String, url: String) {
        usersCol.document(uid).update("profilePhotoUrl", url).await()
    }

    /** Provider's HesabPay account number, so the admin knows where to send payouts. */
    suspend fun updateHesabAccountNumber(uid: String, hesabAccountNumber: String) {
        usersCol.document(uid).update("hesabAccountNumber", hesabAccountNumber).await()
    }

    // Loyalty points are awarded server-side (confirmAppointment) and are now
    // frozen against client writes in firestore.rules — so the old client-side
    // incrementLoyaltyPoints() writer was removed (it was unused and would now
    // be denied). The client only ever reads the points below.

    fun observeUserLoyaltyPoints(uid: String): Flow<Int> = callbackFlow {
        val listener = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { trySend(0); return@addSnapshotListener }
            trySend(snap?.getLong("loyaltyPoints")?.toInt() ?: 0)
        }
        awaitClose { listener.remove() }
    }

    /** Live view of a single user's own document (used to gate on kycStatus). */
    fun observeUser(uid: String): Flow<UserDocument?> = callbackFlow {
        val listener = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { trySend(null); return@addSnapshotListener }
            trySend(snap?.toObject(UserDocument::class.java)?.copy(uid = snap.id))
        }
        awaitClose { listener.remove() }
    }

    /** Users awaiting KYC review, oldest first — for the admin verification tab. */
    fun observeKycPending(): Flow<List<UserDocument>> = callbackFlow {
        val listener = usersCol
            .whereEqualTo("kycStatus", "PENDING")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(UserDocument::class.java)?.copy(uid = it.id) }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        runCatching { usersCol.document(uid).update("fcmToken", token).await() }
    }

    fun observePendingProviders(): Flow<List<UserDocument>> = callbackFlow {
        val listener = usersCol
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(UserDocument::class.java)?.copy(uid = it.id) }
                    ?.filter { it.role == "PROVIDER" }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Salons ────────────────────────────────────────────────────────────────

    /**
     * Live stream of available salons.
     * On success: writes to local cache for offline use.
     * On error: falls back to the local cache so the UI stays populated.
     */
    fun observeAvailableSalons(): Flow<List<SalonDocument>> = callbackFlow {
        val scope = this
        val listener = salonsCol
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    scope.launch {
                        val cached = salonCacheDao.observeAvailable().firstOrNull() ?: emptyList()
                        trySend(cached.map { it.toDocument() })
                    }
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(SalonDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.rating }
                    ?: emptyList()
                scope.launch { salonCacheDao.upsertAll(list.map { it.toEntity() }) }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeSalonByProvider(providerId: String): Flow<SalonDocument?> = callbackFlow {
        val listener = salonsCol
            .whereEqualTo("providerId", providerId)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(null); return@addSnapshotListener }
                val doc = snap?.documents?.firstOrNull()
                trySend(doc?.toObject(SalonDocument::class.java)?.copy(id = doc.id))
            }
        awaitClose { listener.remove() }
    }

    fun observeAllSalons(): Flow<List<SalonDocument>> = callbackFlow {
        val listener = salonsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(SalonDocument::class.java)?.copy(id = it.id) }
                ?.sortedBy { it.salonName }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun createSalon(salon: SalonDocument): String {
        val ref = salonsCol.add(salon).await()
        return ref.id
    }

    suspend fun updateSalon(salon: SalonDocument) {
        salonsCol.document(salon.id).set(salon).await()
    }

    suspend fun setAvailability(salonId: String, isAvailable: Boolean) {
        salonsCol.document(salonId).update("isAvailable", isAvailable).await()
    }

    // NOTE: confirming an appointment (status flip + confirmedCount + loyalty
    // points + notification) is one atomic transaction in the confirmAppointment
    // Cloud Function — not client-side writes.

    suspend fun setSalonVerified(salonId: String, verified: Boolean) {
        salonsCol.document(salonId).update("isVerified", verified).await()
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    fun observeForCustomer(customerId: String): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.appointmentDate }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observePendingForSalon(salonId: String): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("salonId", salonId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
                    ?.filter { it.status == "PENDING" }
                    ?.sortedBy { it.appointmentDate }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeAllForSalon(salonId: String): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("salonId", salonId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.appointmentDate }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // NOTE: appointments are created exclusively by the createPaymentSession
    // Cloud Function (pay-first); the rules deny client creation.

    // NOTE: rescheduling goes through the rescheduleAppointment Cloud Function —
    // appointment updates are rules-denied for clients.

    suspend fun sweepOldAppointments(windowMs: Long) {
        val cutoff = System.currentTimeMillis() - windowMs
        val cancelled = appointmentsCol
            .whereEqualTo("status", "CANCELLED")
            .get().await()
        cancelled.documents
            .filter { (it.getLong("createdAt") ?: 0L) < cutoff }
            .forEach { it.reference.delete().await() }
    }

    /**
     * One taken time-slot for a salon, tagged with the staff member it belongs
     * to ([staffId] is empty for a solo salon / "any available" booking).
     */
    data class BookedSlot(val time: Long, val staffId: String)

    /**
     * Taken time-slots for a salon on the day containing [dateMs]. Served by
     * the getBookedSlots Cloud Function: a customer cannot (and must not) read
     * other customers' appointment docs directly — the rules deny that query,
     * so the old direct read silently returned empty and every slot looked
     * free, allowing double-booking. Each slot carries its staffId so a
     * multi-staff salon can serve several customers in the same time slot.
     */
    suspend fun getBookedSlotsForSalon(salonId: String, dateMs: Long): List<BookedSlot> {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59); cal.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val result = functions
            .getHttpsCallable("getBookedSlots")
            .call(hashMapOf("salonId" to salonId, "dayStart" to startOfDay, "dayEnd" to endOfDay))
            .await()

        @Suppress("UNCHECKED_CAST")
        val map = result.getData() as? Map<String, Any?> ?: return emptyList()
        // Prefer the staff-aware "booked" shape; fall back to the legacy "slots"
        // (plain times, no staff) if the backend hasn't been updated yet.
        val booked = map["booked"] as? List<*>
        if (booked != null) {
            return booked.mapNotNull { entry ->
                val m = entry as? Map<*, *> ?: return@mapNotNull null
                val time = (m["time"] as? Number)?.toLong() ?: return@mapNotNull null
                BookedSlot(time, m["staffId"] as? String ?: "")
            }
        }
        return (map["slots"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong()?.let { t -> BookedSlot(t, "") } }
            ?: emptyList()
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    fun observeConversation(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = chatCol
            .whereEqualTo("conversationId", conversationId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }
                    ?.sortedBy { it.timestamp }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendChatMessage(message: ChatMessage) {
        chatCol.add(message).await()
    }

    // ── Reviews ─────────────────────────────────────────────────────────────────

    fun observeReviewsForSalon(salonId: String): Flow<List<ReviewDocument>> = callbackFlow {
        val listener = reviewsCol
            .whereEqualTo("salonId", salonId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(ReviewDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun replyToReview(reviewId: String, reply: String) {
        reviewsCol.document(reviewId).update(
            mapOf("providerReply" to reply, "repliedAt" to System.currentTimeMillis())
        ).await()
    }

    /** Reserves a review document ID up front so photos can be uploaded under
     *  reviews/{id}/ before the review doc itself is written. */
    fun newReviewId(): String = reviewsCol.document().id

    /**
     * Adds a review. Writing the review doc is the only client write now — the
     * salon's average rating is recomputed server-side (awardReviewPoints trigger)
     * so a provider can't forge it; `rating` is frozen against client writes in
     * firestore.rules. When [review.id] is set (photo reviews reserve it via
     * [newReviewId]) the doc is written at that ID; otherwise Firestore
     * auto-generates one.
     */
    suspend fun addReview(review: ReviewDocument) {
        if (review.id.isNotBlank()) {
            reviewsCol.document(review.id).set(review).await()
        } else {
            reviewsCol.add(review).await()
        }
    }

    // ── Salon gallery (portfolio photos) ─────────────────────────────────────

    fun observeGalleryForSalon(salonId: String): Flow<List<GalleryImageDocument>> = callbackFlow {
        val listener = galleryCol
            .whereEqualTo("salonId", salonId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(GalleryImageDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Reserves a new Firestore document ID so the Storage path can be pre-computed. */
    fun newGalleryDocId(): String = galleryCol.document().id

    // ── Social discovery feed (salon_posts) ─────────────────────────────────────

    /** The global feed of the most recent salon posts (newest first, capped). */
    fun observeFeed(): Flow<List<SalonPostDocument>> = callbackFlow {
        val listener = postsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(SalonPostDocument::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.createdAt }
                ?.take(100)
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addGalleryImage(image: GalleryImageDocument) {
        val id = image.id.ifBlank { galleryCol.document().id }
        galleryCol.document(id).set(image.copy(id = id)).await()
    }

    /** Returns the storagePath field for [imageId], or blank if the doc doesn't exist. */
    suspend fun getGalleryImageStoragePath(imageId: String): String =
        runCatching {
            galleryCol.document(imageId).get().await()
                .getString("storagePath").orEmpty()
        }.getOrDefault("")

    suspend fun deleteGalleryImage(imageId: String) {
        galleryCol.document(imageId).delete().await()
    }

    // ── salon offers (provider-posted promotions; informational) ────────────────

    /** All offers a salon has posted (for the provider's own management list). */
    fun observeOffersForSalon(salonId: String): Flow<List<OfferDocument>> = callbackFlow {
        val listener = offersCol
            .whereEqualTo("salonId", salonId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(OfferDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Every live offer across all salons (active + not expired), for customer display. */
    fun observeActiveOffers(): Flow<List<OfferDocument>> = callbackFlow {
        val listener = offersCol
            .whereEqualTo("active", true)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val now = System.currentTimeMillis()
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(OfferDocument::class.java)?.copy(id = it.id) }
                    ?.filter { it.isLive(now) }   // drop expired client-side (no index needed)
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Create or edit an offer (rules enforce the provider owns the salon). */
    suspend fun upsertOffer(offer: OfferDocument) {
        val id = offer.id.ifBlank { offersCol.document().id }
        offersCol.document(id).set(offer.copy(id = id)).await()
    }

    suspend fun setOfferActive(offerId: String, active: Boolean) {
        offersCol.document(offerId).update("active", active).await()
    }

    suspend fun deleteOffer(offerId: String) {
        offersCol.document(offerId).delete().await()
    }

    suspend fun deleteUser(uid: String) {
        usersCol.document(uid).delete().await()
    }

    suspend fun deleteSalonByProvider(providerId: String) {
        val docs = salonsCol.whereEqualTo("providerId", providerId).get().await()
        docs.documents.forEach { salonDoc ->
            // Remove any portfolio photos belonging to this salon, then the salon.
            runCatching {
                galleryCol.whereEqualTo("salonId", salonDoc.id).get().await()
                    .documents.forEach { it.reference.delete().await() }
            }
            salonDoc.reference.delete().await()
        }
    }

    // ── Admin — all users ────────────────────────────────────────────────────

    fun observeAllUsers(): Flow<List<UserDocument>> = callbackFlow {
        val listener = usersCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(UserDocument::class.java)?.copy(uid = it.id) }
                ?.sortedBy { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    // ── Broadcasts ───────────────────────────────────────────────────────────

    fun observeBroadcasts(): Flow<List<BroadcastDocument>> = callbackFlow {
        val listener = broadcastsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(BroadcastDocument::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun sendBroadcast(doc: BroadcastDocument) {
        broadcastsCol.add(doc).await()
    }

    // ── Export ───────────────────────────────────────────────────────────────

    suspend fun getAppointmentsForUser(userId: String): List<AppointmentDocument> =
        appointmentsCol.whereEqualTo("customerId", userId).get().await()
            .documents.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
            .sortedByDescending { it.appointmentDate }

    // ── Waitlist ──────────────────────────────────────────────────────────────────

    suspend fun addToWaitlist(entry: WaitlistEntry): String {
        val ref = waitlistCol.add(entry).await()
        return ref.id
    }

    suspend fun removeFromWaitlist(entryId: String) {
        waitlistCol.document(entryId).delete().await()
    }

    suspend fun dismissWaitlistEntry(entryId: String) {
        waitlistCol.document(entryId).update("status", "EXPIRED").await()
    }

    fun observeMyWaitlist(customerId: String): Flow<List<WaitlistEntry>> = callbackFlow {
        val listener = waitlistCol
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(WaitlistEntry::class.java)?.copy(id = it.id) }
                    ?.filter { it.status == "WAITING" || it.status == "SLOT_AVAILABLE" }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * When a slot opens for [salonId] on [dateMs], promote the earliest-waiting
     * customer to SLOT_AVAILABLE. Uses a single equality filter + in-memory date
     * check to avoid composite index requirements.
     */
    suspend fun notifyFirstWaiting(salonId: String, dateMs: Long) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val entries = waitlistCol.whereEqualTo("salonId", salonId).get().await()
        val first = entries.documents
            .mapNotNull { it.toObject(WaitlistEntry::class.java)?.copy(id = it.id) }
            .filter { it.requestedDate == startOfDay && it.status == "WAITING" }
            .minByOrNull { it.createdAt }
        if (first != null) {
            waitlistCol.document(first.id).update("status", "SLOT_AVAILABLE").await()
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun observeNotifications(uid: String): Flow<List<NotificationDocument>> = callbackFlow {
        val listener = notificationsCol
            .whereEqualTo("recipientId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    CrashReporter.recordNonFatal(err, "firestore:observeNotifications")
                    trySend(emptyList()); return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(NotificationDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // NOTE: notification documents are created exclusively by the Cloud
    // Functions (Admin SDK) — every doc becomes a real FCM push, so an open
    // client create path would let any signed-in user push arbitrary text to
    // any user's phone. The Firestore rules enforce this (create: if false).

    suspend fun markNotificationRead(notificationId: String) {
        runCatching { notificationsCol.document(notificationId).update("isRead", true).await() }
            .onFailure { CrashReporter.recordNonFatal(it, "firestore:markNotificationRead") }
    }

    suspend fun markAllNotificationsRead(uid: String) {
        runCatching {
            val batch = db.batch()
            val docs  = notificationsCol
                .whereEqualTo("recipientId", uid)
                .whereEqualTo("isRead", false)
                .get().await()
            docs.documents.forEach { batch.update(it.reference, "isRead", true) }
            if (docs.documents.isNotEmpty()) batch.commit().await()
        }.onFailure { CrashReporter.recordNonFatal(it, "firestore:markAllNotificationsRead") }
    }

    suspend fun deleteNotification(notificationId: String) {
        runCatching { notificationsCol.document(notificationId).delete().await() }
            .onFailure { CrashReporter.recordNonFatal(it, "firestore:deleteNotification") }
    }

    // ── Platform config (commission) ────────────────────────────────────────────

    /** Live commission percent from platform_config/general (defaults to 10%). */
    fun observeCommissionPercent(): Flow<Double> = callbackFlow {
        val listener = platformConfigCol.document("general")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(10.0); return@addSnapshotListener }
                trySend(snap?.getDouble("commissionPercent") ?: 10.0)
            }
        awaitClose { listener.remove() }
    }

    /** Admin-only write (enforced by security rules). */
    suspend fun setCommissionPercent(percent: Double) {
        platformConfigCol.document("general")
            .set(mapOf("commissionPercent" to percent), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /** Live list of promo codes (admin-only read, enforced by rules), newest first. */
    fun observePromoCodes(): Flow<List<PromoDocument>> = callbackFlow {
        val listener = promoCodesCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(PromoDocument::class.java) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Admin-only: flagged (misconduct) customer reports still awaiting review. */
    fun observeFlaggedReports(): Flow<List<CustomerReportDocument>> = callbackFlow {
        val listener = db.collection("customer_reports")
            .whereEqualTo("flagged", true)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(CustomerReportDocument::class.java)?.copy(id = it.id) }
                    ?.filter { it.status == "OPEN" }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Support tickets ─────────────────────────────────────────────────────────

    /**
     * Opens (or re-opens) this user's support ticket about a booking. One doc per
     * user keyed by [userId]; contacting support again just refreshes it and
     * flags it unread for the admin. The conversation itself is a normal chat
     * under "support_{userId}".
     */
    suspend fun upsertSupportTicket(
        userId: String,
        userName: String,
        userRole: String,
        relatedInfo: String
    ) {
        supportTicketsCol.document(userId).set(
            SupportTicket(
                id             = userId,
                userId         = userId,
                userName       = userName,
                userRole       = userRole,
                relatedInfo    = relatedInfo,
                status         = "OPEN",
                updatedAt      = System.currentTimeMillis(),
                unreadForAdmin = true
            )
        ).await()
    }

    /** Admin-only: live list of open support tickets, newest first. */
    fun observeOpenSupportTickets(): Flow<List<SupportTicket>> = callbackFlow {
        val listener = supportTicketsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(SupportTicket::class.java)?.copy(id = it.id) }
                ?.filter { it.status == "OPEN" }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Admin-only: clears the unread flag (the admin has opened the thread). */
    suspend fun markSupportTicketRead(userId: String) {
        runCatching { supportTicketsCol.document(userId).update("unreadForAdmin", false).await() }
    }

    /** Admin-only: closes a resolved ticket so it leaves the inbox. */
    suspend fun closeSupportTicket(userId: String) {
        runCatching { supportTicketsCol.document(userId).update("status", "CLOSED").await() }
    }

    // ── Provider balances (payout ledger) ────────────────────────────────────────

    /** Live list of what the platform owes each provider, highest first. */
    fun observeProviderBalances(): Flow<List<ProviderBalance>> = callbackFlow {
        val listener = providerBalancesCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(ProviderBalance::class.java) }
                // Keep anything non-zero: positive = platform owes the provider (a
                // payout), negative = the provider owes the platform (cash-booking
                // commission debt). Only 0 (fully settled) is dropped, so a provider
                // in debt no longer silently disappears from the admin finance list.
                ?.filter { it.owedAmount != 0L }
                ?.sortedByDescending { it.owedAmount }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Live owed balance for one provider — what they see on their own Income tab. */
    fun observeProviderBalance(providerId: String): Flow<Long> = callbackFlow {
        val listener = providerBalancesCol.document(providerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(0L); return@addSnapshotListener }
                trySend(snap?.getLong("owedAmount") ?: 0L)
            }
        awaitClose { listener.remove() }
    }

    /** Live payout history for one provider (most recent first). */
    fun observePayoutsForProvider(providerId: String): Flow<List<PayoutDocument>> = callbackFlow {
        val listener = payoutsCol.whereEqualTo("providerId", providerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(PayoutDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Live payout history (most recent first). Admin reads all rows. */
    fun observePayouts(): Flow<List<PayoutDocument>> = callbackFlow {
        val listener = payoutsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(PayoutDocument::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Live refund requests belonging to one customer (rules allow own rows). */
    fun observeRefundsForCustomer(customerId: String): Flow<List<RefundRequestDocument>> = callbackFlow {
        val listener = refundRequestsCol
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(RefundRequestDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Live refund requests (most recent first). Admin reads all rows. */
    fun observeRefundRequests(): Flow<List<RefundRequestDocument>> = callbackFlow {
        val listener = refundRequestsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(RefundRequestDocument::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    // ── Favorites (Firestore mirror) ────────────────────────────────────────────

    /**
     * Mirrors a favorite to Firestore so the server can notify favoriters when
     * their salon posts an offer. The on-device Room list stays the UI source of
     * truth; this is a best-effort write (doc id "{customerId}_{salonId}").
     */
    suspend fun setFavorite(customerId: String, salonId: String, isFavorite: Boolean) {
        if (customerId.isBlank() || salonId.isBlank()) return
        val ref = favoritesCol.document("${customerId}_$salonId")
        if (isFavorite) {
            ref.set(mapOf(
                "customerId" to customerId,
                "salonId"    to salonId,
                "createdAt"  to System.currentTimeMillis()
            )).await()
        } else {
            ref.delete().await()
        }
    }

    // ── Seeder check ──────────────────────────────────────────────────────────

    suspend fun isUsersEmpty(): Boolean =
        usersCol.limit(1).get().await().isEmpty

    suspend fun isAdminMissing(): Boolean =
        usersCol.whereEqualTo("role", "ADMIN").limit(1).get().await().isEmpty
}
