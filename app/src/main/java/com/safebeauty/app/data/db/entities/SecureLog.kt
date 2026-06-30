package com.safebeauty.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "secure_logs",
    indices = [Index(value = ["timestamp"]), Index(value = ["isErased"])]
)
data class SecureLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String,
    val isErased: Boolean = false
)
