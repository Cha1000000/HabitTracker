package com.habittracker.domain.repository

import com.habittracker.domain.model.GroupHabitNotificationModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository для работы с уведомлениями групповых привычек
 */
interface GroupHabitNotificationRepository {
    
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
     * Отмечает все уведомления как прочитанные
     */
    suspend fun markAllAsRead(userId: String)
    
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
