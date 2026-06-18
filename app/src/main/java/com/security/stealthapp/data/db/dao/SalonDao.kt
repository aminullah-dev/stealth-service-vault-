package com.security.stealthapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.security.stealthapp.data.db.entities.Salon
import kotlinx.coroutines.flow.Flow

@Dao
interface SalonDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(salon: Salon): Long

    @Update
    suspend fun update(salon: Salon): Int

    /** Live stream of ALL salons — used to derive availability counts. */
    @Query("SELECT * FROM salons ORDER BY rating DESC")
    fun observeAll(): Flow<List<Salon>>

    /** Live stream filtered to available salons — drives the CustomerDashboard list. */
    @Query("SELECT * FROM salons WHERE isAvailable = 1 ORDER BY rating DESC")
    fun observeAvailable(): Flow<List<Salon>>

    /** Single-salon live stream for ProviderDashboard. */
    @Query("SELECT * FROM salons WHERE providerId = :providerId LIMIT 1")
    fun observeByProvider(providerId: String): Flow<Salon?>

    @Query("SELECT * FROM salons WHERE district = :district AND isAvailable = 1 ORDER BY rating DESC")
    fun observeByDistrict(district: String): Flow<List<Salon>>

    /** Atomic availability toggle — one query, avoids read-modify-write race. */
    @Query("UPDATE salons SET isAvailable = :isAvailable WHERE providerId = :providerId")
    suspend fun setAvailability(providerId: String, isAvailable: Boolean): Int

    @Query("DELETE FROM salons")
    suspend fun deleteAll(): Int
}
