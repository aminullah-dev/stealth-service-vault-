package com.safebeauty.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safebeauty.app.data.db.entities.BookingEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(booking: BookingEntry): Long

    @Update
    suspend fun update(booking: BookingEntry): Int

    @Query("SELECT * FROM booking_entries WHERE isErased = 0 ORDER BY timestamp DESC")
    fun observeActiveBookings(): Flow<List<BookingEntry>>

    @Query("SELECT * FROM booking_entries WHERE serviceCategory = :category AND isErased = 0 ORDER BY timestamp DESC")
    fun observeByCategory(category: String): Flow<List<BookingEntry>>

    @Query("UPDATE booking_entries SET isErased = 1 WHERE timestamp < :cutoffTimestamp AND isErased = 0")
    suspend fun softDeleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM booking_entries WHERE isErased = 1")
    suspend fun purgeErased(): Int

    @Query("DELETE FROM booking_entries")
    suspend fun deleteAll(): Int
}
