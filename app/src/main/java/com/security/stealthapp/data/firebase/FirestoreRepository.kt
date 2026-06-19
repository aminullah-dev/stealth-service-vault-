package com.security.stealthapp.data.firebase

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

    suspend fun createAppointment(appt: AppointmentDocument): String {
        val ref = appointmentsCol.add(appt).await()
        return ref.id
    }

    suspend fun updateAppointmentStatus(appointmentId: String, status: String) {
        appointmentsCol.document(appointmentId).update("status", status).await()
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

    // ── Seeder check ──────────────────────────────────────────────────────────

    suspend fun isUsersEmpty(): Boolean =
        usersCol.limit(1).get().await().isEmpty
}
