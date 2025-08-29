package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habittracker.data.local.entity.PendingNotificationEntity

@Dao
interface PendingNotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingNotificationEntity): Long

    @Query("DELETE FROM pending_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_notifications WHERE receivedAt + (ttlSeconds * 1000) < :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query("SELECT * FROM pending_notifications WHERE recipientUserId = :userId ORDER BY receivedAt ASC")
    suspend fun getAllForUser(userId: String): List<PendingNotificationEntity>

    @Query("DELETE FROM pending_notifications WHERE recipientUserId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM pending_notifications WHERE dedupKey = :key")
    suspend fun deleteByDedupKey(key: String)
}
