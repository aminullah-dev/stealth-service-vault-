package com.security.stealthapp.data.firebase

import com.google.firebase.firestore.PropertyName

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
    val fcmToken: String = "",
    val createdAt: Long = 0L
)

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

data class SalonDocument(
    val id: String = "",                    // Firestore document ID (set after read)
    val providerId: String = "",
    val providerName: String = "",
    val salonName: String = "",
    val district: String = "",
    val services: List<String> = emptyList(),
    // @PropertyName forces Firestore to use "isAvailable" as the field name.
    // Without it, the JavaBeans convention for Boolean getters strips the "is"
    // prefix, storing the field as "available" instead and breaking all queries.
    @get:PropertyName("isAvailable") @set:PropertyName("isAvailable")
    var isAvailable: Boolean = false,
    val rating: Double = 0.0,
    val workingHours: List<WorkingHours> = emptyList(),
    val slotDurationMinutes: Int = 60
)

data class AppointmentDocument(
    val id: String = "",                    // Firestore document ID
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val serviceName: String = "",
    val appointmentDate: Long = 0L,         // epoch millis (date + time)
    val status: String = "PENDING",         // "PENDING" | "CONFIRMED" | "CANCELLED"
    val createdAt: Long = 0L
)

data class ReviewDocument(
    val id: String = "",                    // Firestore document ID
    val salonId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val rating: Int = 0,                    // 1–5 stars
    val comment: String = "",
    val createdAt: Long = 0L
)

data class BroadcastDocument(
    val id: String = "",                    // Firestore document ID
    val message: String = "",
    val sentBy: String = "admin",
    val createdAt: Long = 0L
)
