package com.security.stealthapp.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.security.stealthapp.data.db.dao.SalonCacheDao
import com.security.stealthapp.data.db.entities.toEntity
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

    private val usersCol        = db.collection("users")
    private val salonsCol       = db.collection("salons")
    private val appointmentsCol = db.collection("appointments")
    private val chatCol         = db.collection("chat_messages")
    private val reviewsCol      = db.collection("reviews")
    private val broadcastsCol   = db.collection("broadcasts")
    private val galleryCol      = db.collection("salon_gallery")
    private val waitlistCol     = db.collection("waitlist")

    // ── Users ─────────────────────────────────────────────────────────────────

    suspend fun getAllUsersForAuth(): List<UserDocument> {
        return usersCol.get().await().documents.mapNotNull { doc ->
            doc.toObject(UserDocument::class.java)?.copy(uid = doc.id)
        }
    }

    suspend fun createUser(user: UserDocument) {
        usersCol.document(user.uid).set(user).await()
    }

    suspend fun getUserById(uid: String): UserDocument? {
        return usersCol.document(uid).get().await()
            .toObject(UserDocument::class.java)?.copy(uid = uid)
    }

    suspend fun setUserStatus(uid: String, status: String) {
        usersCol.document(uid).update("status", status).await()
    }

    suspend fun suspendUser(uid: String)   = setUserStatus(uid, "SUSPENDED")
    suspend fun unsuspendUser(uid: String) = setUserStatus(uid, "APPROVED")

    suspend fun updateUserName(uid: String, name: String) {
        usersCol.document(uid).update("name", name).await()
    }

    suspend fun updateUserPin(uid: String, pinHash: String, salt: String) {
        usersCol.document(uid).update(mapOf("pinHash" to pinHash, "salt" to salt)).await()
    }

    suspend fun setDecoyPin(uid: String, hash: String, salt: String) {
        usersCol.document(uid).update(mapOf("decoyPinHash" to hash, "decoySalt" to salt)).await()
    }

    suspend fun clearDecoyPin(uid: String) {
        usersCol.document(uid).update(mapOf("decoyPinHash" to "", "decoySalt" to "")).await()
    }

    suspend fun updateUserPhoto(uid: String, base64: String) {
        usersCol.document(uid).update("profilePhotoBase64", base64).await()
    }

    suspend fun incrementLoyaltyPoints(customerId: String) {
        runCatching {
            usersCol.document(customerId).update("loyaltyPoints", FieldValue.increment(10)).await()
        }
    }

    fun observeUserLoyaltyPoints(uid: String): Flow<Int> = callbackFlow {
        val listener = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { trySend(0); return@addSnapshotListener }
            trySend(snap?.getLong("loyaltyPoints")?.toInt() ?: 0)
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

    suspend fun incrementConfirmedCount(salonId: String) {
        runCatching {
            salonsCol.document(salonId).update("confirmedCount", FieldValue.increment(1)).await()
        }
    }

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

    suspend fun createAppointment(appt: AppointmentDocument): String {
        val ref = appointmentsCol.add(appt).await()
        return ref.id
    }

    suspend fun updateAppointmentStatus(appointmentId: String, status: String) {
        appointmentsCol.document(appointmentId).update("status", status).await()
    }

    /**
     * Move an appointment to a new time. The booking returns to PENDING so the
     * provider re-confirms the new slot.
     */
    suspend fun rescheduleAppointment(appointmentId: String, newDateMs: Long) {
        appointmentsCol.document(appointmentId)
            .update(mapOf("appointmentDate" to newDateMs, "status" to "PENDING"))
            .await()
    }

    suspend fun sweepOldAppointments(windowMs: Long) {
        val cutoff = System.currentTimeMillis() - windowMs
        val cancelled = appointmentsCol
            .whereEqualTo("status", "CANCELLED")
            .get().await()
        cancelled.documents
            .filter { (it.getLong("createdAt") ?: 0L) < cutoff }
            .forEach { it.reference.delete().await() }
    }

    suspend fun getBookedSlotsForSalon(salonId: String, dateMs: Long): List<Long> {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59)
        val endOfDay = cal.timeInMillis
        return appointmentsCol.whereEqualTo("salonId", salonId).get().await()
            .documents.mapNotNull { it.toObject(AppointmentDocument::class.java) }
            .filter { it.status != "CANCELLED" && it.appointmentDate in startOfDay..endOfDay }
            .map { it.appointmentDate }
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

    /**
     * Adds a review, then recomputes the salon's average rating from all of its
     * reviews and writes it back so salon cards stay in sync.
     */
    suspend fun addReview(review: ReviewDocument) {
        reviewsCol.add(review).await()
        val all = reviewsCol.whereEqualTo("salonId", review.salonId).get().await()
            .documents.mapNotNull { it.toObject(ReviewDocument::class.java) }
        if (all.isNotEmpty()) {
            val avg = all.map { it.rating }.average()
            runCatching { salonsCol.document(review.salonId).update("rating", avg).await() }
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

    suspend fun addGalleryImage(image: GalleryImageDocument) {
        galleryCol.add(image).await()
    }

    suspend fun deleteGalleryImage(imageId: String) {
        galleryCol.document(imageId).delete().await()
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

    // ── Seeder check ──────────────────────────────────────────────────────────

    suspend fun isUsersEmpty(): Boolean =
        usersCol.limit(1).get().await().isEmpty

    suspend fun isAdminMissing(): Boolean =
        usersCol.whereEqualTo("role", "ADMIN").limit(1).get().await().isEmpty
}
