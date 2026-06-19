package com.security.stealthapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.security.stealthapp.data.db.entities.SalonCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalonCacheDao {

    @Query("SELECT * FROM salon_cache WHERE isAvailable = 1 ORDER BY rating DESC")
    fun observeAvailable(): Flow<List<SalonCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(salons: List<SalonCacheEntity>)

    @Query("DELETE FROM salon_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM salon_cache")
    suspend fun count(): Int
}
