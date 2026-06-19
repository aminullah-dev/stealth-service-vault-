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

        seed("Admin",    "0000", "ADMIN",    "APPROVED", null)
        seed("Fatima",   "5678", "CUSTOMER", "APPROVED", null)
        seed("Maryam",   "1234", "PROVIDER", "APPROVED",
            SalonSeed("Maryam Studio",   "District 3 – Khair Khana",  listOf("Hair", "Makeup"), 4.8))
        seed("Zahra",    "2345", "PROVIDER", "APPROVED",
            SalonSeed("Zahra Beauty",    "District 6 – Karte Seh",    listOf("Skincare", "Eyebrows"), 4.6))
        seed("Parisa",   "3456", "PROVIDER", "APPROVED",
            SalonSeed("Parisa Brows",    "District 11 – Qala-e Wahed",listOf("Eyebrows", "Nails"), 4.9))
        seed("Neda",     "4567", "PROVIDER", "APPROVED",
            SalonSeed("Neda Nails Pro",  "District 9 – Dasht-e Barchi",listOf("Nails", "Makeup"), 4.7))
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
