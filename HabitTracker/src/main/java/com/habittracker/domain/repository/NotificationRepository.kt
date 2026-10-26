package com.habittracker.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Репозиторий для управления статусами уведомлений
 */
interface NotificationRepository {
    
    /**
     * Сохраняет статус прочтения уведомления
     */
    suspend fun saveReadStatus(
        notificationId: String,
        userId: String,
        habitId: String,
        isRead: Boolean,
        readAt: Instant? = null
    )
    
    /**
     * Получает статус прочтения уведомления
     */
    suspend fun getReadStatus(notificationId: String): Boolean
    
    /**
     * Получает все статусы уведомлений для пользователя
     */
    fun getReadStatusesForUser(userId: String): Flow<Map<String, Boolean>>
    
    /**
     * Отмечает уведомление как прочитанное
     */
    suspend fun markAsRead(notificationId: String, readAt: Instant)
    
    /**
     * Отмечает все уведомления пользователя как прочитанные
     */
    suspend fun markAllAsReadForUser(userId: String, readAt: Instant)
    
    /**
     * Получает количество непрочитанных уведомлений для пользователя
     */
    fun getUnreadCountForUser(userId: String): Flow<Int>
    
    /**
     * Удаляет статус уведомления
     */
    suspend fun deleteReadStatus(notificationId: String)
    
    /**
     * Удаляет все статусы уведомлений для пользователя
     */
    suspend fun deleteAllForUser(userId: String)
}
