package com.security.stealthapp.data.db.converters

import androidx.room.TypeConverter
import com.security.stealthapp.data.db.entities.AppointmentStatus
import com.security.stealthapp.data.db.entities.UserRole

class Converters {

    // ── List<String> ↔ pipe-delimited String ─────────────────────────────────
    // Pipe delimiter avoids conflicts with commas in service names.

    @TypeConverter
    fun listToString(list: List<String>): String = list.joinToString("||")

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split("||")

    // ── UserRole ──────────────────────────────────────────────────────────────

    @TypeConverter
    fun fromUserRole(role: UserRole): String = role.name

    @TypeConverter
    fun toUserRole(name: String): UserRole = UserRole.valueOf(name)

    // ── AppointmentStatus ─────────────────────────────────────────────────────

    @TypeConverter
    fun fromAppointmentStatus(status: AppointmentStatus): String = status.name

    @TypeConverter
    fun toAppointmentStatus(name: String): AppointmentStatus =
        AppointmentStatus.valueOf(name)
}
