package com.security.stealthapp.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A service provider's salon profile. [services] is stored as a pipe-delimited
 * string via [com.security.stealthapp.data.db.converters.Converters].
 */
@Entity(
    tableName    = "salons",
    foreignKeys  = [
        ForeignKey(
            entity       = User::class,
            parentColumns = ["id"],
            childColumns  = ["providerId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices      = [Index("providerId"), Index("district"), Index("isAvailable")]
)
data class Salon(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val salonName: String,
    val district: String,
    val services: List<String>,
    val isAvailable: Boolean = true,
    val rating: Float = 0f
)
