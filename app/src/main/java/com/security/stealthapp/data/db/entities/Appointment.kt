package com.security.stealthapp.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class AppointmentStatus { PENDING, CONFIRMED, CANCELLED }

@Entity(
    tableName   = "appointments",
    foreignKeys = [
        ForeignKey(
            entity        = User::class,
            parentColumns = ["id"],
            childColumns  = ["customerId"],
            onDelete      = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity        = Salon::class,
            parentColumns = ["id"],
            childColumns  = ["salonId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("customerId"),
        Index("salonId"),
        Index("status"),
        Index("timestamp")
    ]
)
data class Appointment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val salonId: String,
    val serviceName: String,
    val appointmentDate: Long,
    val status: AppointmentStatus = AppointmentStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)
