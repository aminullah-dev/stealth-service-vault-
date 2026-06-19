package com.security.stealthapp.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
class FirestoreRepository @Inject constructor() {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ── Collections ───────────────────────────────────────────────────────────

    private val usersCol        = db.collection("users")
    private val salonsCol       = db.collection("salons")
    private val appointmentsCol = db.collection("appointments")

    // ── Users ─────────────────────────────────────────────────────────────────

    /** Fetches all user documents once — used only during PIN login. */
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

    /** Approves a provider (admin action). */
    suspend fun setUserStatus(uid: String, status: String) {
        usersCol.document(uid).update("status", status).await()
    }

    /** Live feed of providers pending admin approval. */
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

    /** Live stream of available salons — drives the CustomerDashboard. */
    fun observeAvailableSalons(): Flow<List<SalonDocument>> = callbackFlow {
        val listener = salonsCol
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(SalonDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.rating }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Live stream for a single salon — drives the ProviderDashboard. */
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

    /** Live feed of a customer's own appointments. */
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

    /** Live feed of PENDING appointments for a salon — drives the provider queue. */
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

    /** Sweeps cancelled appointments older than [windowMs] — called by DataErasureWorker. */
    suspend fun sweepOldAppointments(windowMs: Long) {
        val cutoff = System.currentTimeMillis() - windowMs
        val cancelled = appointmentsCol
            .whereEqualTo("status", "CANCELLED")
            .get().await()
        cancelled.documents
            .filter { (it.getLong("createdAt") ?: 0L) < cutoff }
            .forEach { it.reference.delete().await() }
    }

    // ── Seeder check ──────────────────────────────────────────────────────────

    /** Returns true if the users collection is empty (first launch). */
    suspend fun isUsersEmpty(): Boolean =
        usersCol.limit(1).get().await().isEmpty
}
