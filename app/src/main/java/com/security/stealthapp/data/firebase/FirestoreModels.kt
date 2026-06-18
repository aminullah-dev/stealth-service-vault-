package com.security.stealthapp.data.firebase

/**
 * Firestore document representations.
 * All fields have defaults so Firestore can deserialize via the no-arg constructor.
 */

data class UserDocument(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "CUSTOMER",          // "CUSTOMER" | "PROVIDER" | "ADMIN"
    val pinHash: String = "",               // PBKDF2(pin, salt) — for local verification
    val salt: String = "",
    val status: String = "APPROVED",        // "PENDING" | "APPROVED" | "REJECTED"
    val firebaseEmail: String = "",         // synthetic internal email for Firebase Auth
    val createdAt: Long = 0L
)

data class SalonDocument(
    val id: String = "",                    // Firestore document ID (set after read)
    val providerId: String = "",
    val providerName: String = "",
    val salonName: String = "",
    val district: String = "",
    val services: List<String> = emptyList(),
    val isAvailable: Boolean = false,
    val rating: Double = 0.0
)

data class AppointmentDocument(
    val id: String = "",                    // Firestore document ID
    val customerId: String = "",
    val customerName: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val serviceName: String = "",
    val appointmentDate: Long = 0L,         // epoch millis (date + time)
    val status: String = "PENDING",         // "PENDING" | "CONFIRMED" | "CANCELLED"
    val createdAt: Long = 0L
)
