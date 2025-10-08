package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.NotificationReadStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы со статусами уведомлений
 */
@Dao
interface NotificationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadStatus(status: NotificationReadStatusEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadStatuses(statuses: List<NotificationReadStatusEntity>)
    
    @Update
    suspend fun updateReadStatus(status: NotificationReadStatusEntity)
    
    @Query("SELECT * FROM notification_read_status WHERE notificationId = :notificationId")
    suspend fun getReadStatus(notificationId: String): NotificationReadStatusEntity?
    
    @Query("SELECT * FROM notification_read_status WHERE userId = :userId")
    fun getReadStatusesForUser(userId: String): Flow<List<NotificationReadStatusEntity>>
    
    @Query("SELECT * FROM notification_read_status WHERE userId = :userId AND habitId = :habitId")
    suspend fun getReadStatusesForUserAndHabit(userId: String, habitId: String): List<NotificationReadStatusEntity>
    
    @Query("UPDATE notification_read_status SET isRead = 1, readAt = :readAt WHERE notificationId = :notificationId")
    suspend fun markAsRead(notificationId: String, readAt: Long)
    
    @Query("UPDATE notification_read_status SET isRead = 1, readAt = :readAt WHERE userId = :userId")
    suspend fun markAllAsReadForUser(userId: String, readAt: Long)
    
    @Query("DELETE FROM notification_read_status WHERE notificationId = :notificationId")
    suspend fun deleteReadStatus(notificationId: String)
    
    @Query("DELETE FROM notification_read_status WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    @Query("SELECT COUNT(*) FROM notification_read_status WHERE userId = :userId AND isRead = 0")
    fun getUnreadCountForUser(userId: String): Flow<Int>
}
