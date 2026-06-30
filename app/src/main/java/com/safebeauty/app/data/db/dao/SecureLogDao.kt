package com.safebeauty.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safebeauty.app.data.db.entities.SecureLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SecureLog): Long

    @Query("SELECT * FROM secure_logs WHERE isErased = 0 ORDER BY timestamp DESC")
    fun observeActiveLogs(): Flow<List<SecureLog>>

    /** Soft-delete records older than [cutoffTimestamp]. */
    @Query("UPDATE secure_logs SET isErased = 1 WHERE timestamp < :cutoffTimestamp AND isErased = 0")
    suspend fun softDeleteOlderThan(cutoffTimestamp: Long): Int

    /** Hard-purge previously soft-deleted rows to reclaim disk space. */
    @Query("DELETE FROM secure_logs WHERE isErased = 1")
    suspend fun purgeErased(): Int

    @Query("DELETE FROM secure_logs")
    suspend fun deleteAll(): Int
}
