package com.security.stealthapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.security.stealthapp.data.db.entities.Appointment
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: Appointment): Long

    @Update
    suspend fun update(appointment: Appointment): Int

    @Query("SELECT * FROM appointments WHERE customerId = :customerId ORDER BY appointmentDate DESC")
    fun observeForCustomer(customerId: String): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE salonId = :salonId ORDER BY appointmentDate DESC")
    fun observeForSalon(salonId: String): Flow<List<Appointment>>

    /** Live feed of only PENDING appointments for the provider's Accept / Decline queue. */
    @Query("SELECT * FROM appointments WHERE salonId = :salonId AND status = 'PENDING' ORDER BY appointmentDate ASC")
    fun observePendingForSalon(salonId: String): Flow<List<Appointment>>

    @Query("UPDATE appointments SET status = :status WHERE id = :appointmentId")
    suspend fun updateStatus(appointmentId: String, status: String): Int

    @Query("DELETE FROM appointments WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM appointments")
    suspend fun deleteAll(): Int
}
