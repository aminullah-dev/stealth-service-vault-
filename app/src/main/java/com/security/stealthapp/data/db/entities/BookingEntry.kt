package com.security.stealthapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_entries",
    indices = [Index(value = ["timestamp"]), Index(value = ["serviceCategory"])]
)
data class BookingEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val serviceCategory: String,
    val providerName: String,
    val neighborhood: String,
    val scheduledTime: Long,
    val status: String = BookingStatus.PENDING.name,
    val isErased: Boolean = false
)

enum class BookingStatus { PENDING, CONFIRMED, COMPLETED, CANCELLED }
