package com.habittracker.data.local.dao

import androidx.room.*
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с уведомлениями групповых привычек
 */
@Dao
interface GroupHabitNotificationDao {
    
    @Query("SELECT * FROM group_habit_notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getNotificationsForUser(userId: String): Flow<List<GroupHabitNotificationEntity>>
    
    @Query("SELECT * FROM group_habit_notifications WHERE id = :notificationId")
    suspend fun getNotificationById(notificationId: String): GroupHabitNotificationEntity?
    
    @Query("SELECT COUNT(*) FROM group_habit_notifications WHERE userId = :userId AND isRead = 0")
    fun getUnreadCount(userId: String): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: GroupHabitNotificationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<GroupHabitNotificationEntity>)
    
    @Update
    suspend fun updateNotification(notification: GroupHabitNotificationEntity)
    
    @Query("UPDATE group_habit_notifications SET isRead = 1, readAt = :readAt WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String, readAt: Long)
    
    @Query("DELETE FROM group_habit_notifications WHERE id = :notificationId")
    suspend fun deleteNotification(notificationId: String)
    
    @Query("SELECT * FROM group_habit_notifications WHERE userId = :userId AND isRead = 1 AND readAt < :olderThan")
    suspend fun getReadNotificationsOlderThan(userId: String, olderThan: Long): List<GroupHabitNotificationEntity>

    @Query("SELECT * FROM group_habit_notifications WHERE userId = :userId")
    suspend fun getAllNotificationsForUserSync(userId: String): List<GroupHabitNotificationEntity>

    @Query("DELETE FROM group_habit_notifications WHERE userId = :userId AND isRead = 1 AND readAt < :olderThan")
    suspend fun deleteReadNotificationsOlderThan(userId: String, olderThan: Long)
    
    @Query("DELETE FROM group_habit_notifications WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
