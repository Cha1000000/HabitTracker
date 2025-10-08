package com.habittracker.domain.repository

import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.domain.model.GroupHabitNotificationModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository для работы с уведомлениями групповых привычек
 */
interface GroupHabitNotificationRepository {
    
    /**
     * Сохраняет уведомление
     */
    suspend fun saveNotification(notification: GroupHabitNotificationEntity)
    
    /**
     * Получает все уведомления пользователя
     */
    fun getNotificationsForUser(userId: String): Flow<List<GroupHabitNotificationModel>>
    
    /**
     * Получает количество непрочитанных уведомлений
     */
    fun getUnreadCount(userId: String): Flow<Int>
    
    /**
     * Отмечает уведомление как прочитанное
     */
    suspend fun markAsRead(notificationId: String)
    
    /**
     * Удаляет уведомление
     */
    suspend fun deleteNotification(notificationId: String)
    
    /**
     * Удаляет прочитанные уведомления старше указанного времени
     */
    suspend fun deleteReadNotificationsOlderThan(userId: String, olderThanMillis: Long)
    
    /**
     * Удаляет все уведомления пользователя
     */
    suspend fun deleteAllForUser(userId: String)
}
