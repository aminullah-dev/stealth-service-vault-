package com.security.stealthapp.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor() {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ── Collections ───────────────────────────────────────────────────────────

    private val usersCol      = db.collection("users")
    private val salonsCol     = db.collection("salons")
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
            .whereEqualTo("role", "PROVIDER")
            .whereEqualTo("status", "PENDING")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(UserDocument::class.java)?.copy(uid = it.id)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    // ── Salons ────────────────────────────────────────────────────────────────

    /** Live stream of available salons — drives the CustomerDashboard. */
    fun observeAvailableSalons(): Flow<List<SalonDocument>> = callbackFlow {
        val listener = salonsCol
            .whereEqualTo("isAvailable", true)
            .orderBy("rating", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(SalonDocument::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    /** Live stream for a single salon — drives the ProviderDashboard. */
    fun observeSalonByProvider(providerId: String): Flow<SalonDocument?> = callbackFlow {
        val listener = salonsCol
            .whereEqualTo("providerId", providerId)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
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
            .orderBy("appointmentDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(AppointmentDocument::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    /** Live feed of PENDING appointments for a salon — drives the provider queue. */
    fun observePendingForSalon(salonId: String): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("salonId", salonId)
            .whereEqualTo("status", "PENDING")
            .orderBy("appointmentDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(AppointmentDocument::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                )
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

    /** Sweeps appointments older than [windowMs] — called by DataErasureWorker. */
    suspend fun sweepOldAppointments(windowMs: Long) {
        val cutoff = System.currentTimeMillis() - windowMs
        val old = appointmentsCol
            .whereLessThan("createdAt", cutoff)
            .whereEqualTo("status", "CANCELLED")
            .get().await()
        old.documents.forEach { it.reference.delete().await() }
    }

    // ── Seeder check ──────────────────────────────────────────────────────────

    /** Returns true if the users collection is empty (first launch). */
    suspend fun isUsersEmpty(): Boolean =
        usersCol.limit(1).get().await().isEmpty
}
