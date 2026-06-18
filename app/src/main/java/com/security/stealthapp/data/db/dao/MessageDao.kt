package com.security.stealthapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.security.stealthapp.data.db.entities.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages WHERE isErased = 0 ORDER BY timestamp DESC")
    fun observeActiveMessages(): Flow<List<Message>>

    @Query(
        """
        SELECT * FROM messages
        WHERE (senderId = :userId OR recipientId = :userId) AND isErased = 0
        ORDER BY timestamp ASC
        """
    )
    fun observeConversation(userId: String): Flow<List<Message>>

    @Query("UPDATE messages SET isErased = 1 WHERE timestamp < :cutoffTimestamp AND isErased = 0")
    suspend fun softDeleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM messages WHERE isErased = 1")
    suspend fun purgeErased(): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll(): Int
}
