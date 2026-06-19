package com.security.stealthapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.security.stealthapp.data.db.entities.FavoriteSalonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteSalonDao {

    @Query("SELECT salonId FROM favorite_salons ORDER BY savedAt DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteSalonEntity)

    @Query("DELETE FROM favorite_salons WHERE salonId = :salonId")
    suspend fun remove(salonId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_salons WHERE salonId = :salonId)")
    suspend fun isFavorite(salonId: String): Boolean
}
