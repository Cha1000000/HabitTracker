package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habittracker.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE state = :state ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getByState(state: String = "PENDING", limit: Int = 50): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    @Query("UPDATE sync_outbox SET retryCount = retryCount + 1, lastError = :error, state = :state, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markRetry(id: Long, error: String?, state: String, updatedAt: Long)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId AND state IN ('PENDING', 'IN_PROGRESS')")
    suspend fun hasPendingOperation(entityType: String, entityId: String): Int

    @Query("SELECT COUNT(*) > 0 FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId AND state IN ('PENDING', 'IN_PROGRESS')")
    suspend fun hasPendingForEntity(entityType: String, entityId: String): Boolean
}
