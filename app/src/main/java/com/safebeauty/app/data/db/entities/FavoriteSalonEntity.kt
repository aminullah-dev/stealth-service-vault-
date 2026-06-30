package com.safebeauty.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Device-local list of salons the customer has favorited. Kept on-device (never
 * synced to Firestore) so a saved-providers list can't be tied back to a user
 * account if the backend is ever compromised.
 */
@Entity(tableName = "favorite_salons")
data class FavoriteSalonEntity(
    @PrimaryKey val salonId: String,
    val savedAt: Long = System.currentTimeMillis()
)
