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
        if (!repo.isUsersEmpty()) return
        // Only the admin account is seeded. All providers and customers register
        // themselves through the registration flow. The admin approves providers.
        // Default admin PIN: 246813 — change this before production deployment.
        seed("Admin", "246813", "ADMIN", "APPROVED", null)
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
