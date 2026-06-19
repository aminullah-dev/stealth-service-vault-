package com.security.stealthapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.security.stealthapp.data.firebase.SalonDocument

/**
 * Flat cache of Firestore SalonDocuments — no foreign-key constraints so rows
 * can be upserted without first inserting a matching User row.
 */
@Entity(tableName = "salon_cache")
data class SalonCacheEntity(
    @PrimaryKey val id: String,
    val providerId: String   = "",
    val salonName: String    = "",
    val district: String     = "",
    val services: String     = "",   // pipe-separated: "Hair|Makeup|Nails"
    val isAvailable: Boolean = false,
    val rating: Double       = 0.0,
    val cachedAt: Long       = System.currentTimeMillis()
) {
    fun toDocument(): SalonDocument = SalonDocument(
        id          = id,
        providerId  = providerId,
        salonName   = salonName,
        district    = district,
        services    = if (services.isBlank()) emptyList() else services.split("|"),
        isAvailable = isAvailable,
        rating      = rating
    )
}

fun SalonDocument.toEntity(): SalonCacheEntity = SalonCacheEntity(
    id          = id,
    providerId  = providerId,
    salonName   = salonName,
    district    = district,
    services    = services.joinToString("|"),
    isAvailable = isAvailable,
    rating      = rating,
    cachedAt    = System.currentTimeMillis()
)
