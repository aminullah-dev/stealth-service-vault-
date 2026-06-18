package com.security.stealthapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class UserRole { CUSTOMER, PROVIDER }

@Entity(
    tableName = "users",
    indices   = [Index(value = ["phoneNumber"], unique = true)]
)
data class User(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val role: UserRole,
    val pinHash: String,
    val salt: String
)
