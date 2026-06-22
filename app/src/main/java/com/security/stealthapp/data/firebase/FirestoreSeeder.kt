package com.security.stealthapp.data.firebase

import com.security.stealthapp.security.PinHasher
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds demo accounts into Firestore the first time the app launches.
 * Checks [FirestoreRepository.isUsersEmpty] before writing anything.
 */
@Singleton
class FirestoreSeeder @Inject constructor(
    private val repo: FirestoreRepository,
    private val auth: FirebaseAuthManager,
    private val pinHasher: PinHasher
) {

    suspend fun seedIfEmpty() {
        // Ensure an admin account always exists. We seed when there is no ADMIN
        // user yet — not only when the whole collection is empty — so that an
        // admin is restored even if customers/providers registered first (e.g.
        // after the database was wiped).
        //
        // This runs at app startup before anyone has authenticated, so the
        // isAdminMissing() read is rejected by the security rules
        // (request.auth == null) once the database is no longer in test mode.
        // That's expected — the admin already exists by then. Swallow any
        // failure so a denied read can never crash the app on launch.
        runCatching {
            if (!repo.isAdminMissing()) return
            // Default admin PIN: 135790 — change via Firebase Console after first login.
            seed("Admin", "135790", "ADMIN", "APPROVED", null)
        }
    }

    private data class SalonSeed(
        val name: String, val district: String, val services: List<String>, val rating: Double
    )

    private suspend fun seed(
        name: String, pin: String, role: String, status: String, salon: SalonSeed?
    ) {
        val uid           = UUID.randomUUID().toString()
        val salt          = pinHasher.generateSalt()
        val pinHash       = pinHasher.hash(pin, salt)
        val authPassword  = pinHasher.deriveAuthPassword(pin, salt)
        val firebaseEmail = "${uid.replace("-", "")}@sb.app"

        // Create Firebase Auth account
        auth.createAccount(firebaseEmail, authPassword)
            .onFailure { return } // skip if already exists (re-seed edge case)

        // Create Firestore user document
        repo.createUser(
            UserDocument(
                uid           = uid,
                name          = name,
                phone         = "",
                role          = role,
                pinHash       = pinHash,
                salt          = salt,
                status        = status,
                firebaseEmail = firebaseEmail,
                createdAt     = System.currentTimeMillis()
            )
        )

        // Create salon document for providers
        if (salon != null) {
            repo.createSalon(
                SalonDocument(
                    providerId   = uid,
                    providerName = name,
                    salonName    = salon.name,
                    district     = salon.district,
                    services     = salon.services,
                    isAvailable  = true,
                    rating       = salon.rating
                )
            )
        }
    }
}
