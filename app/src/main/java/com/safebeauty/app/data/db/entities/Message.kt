package com.safebeauty.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["timestamp"]), Index(value = ["senderId"]), Index(value = ["recipientId"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val senderId: String,
    val recipientId: String,
    val content: String,
    val isRead: Boolean = false,
    val isErased: Boolean = false
)
