package com.habittracker.data.repository

import com.habittracker.data.local.dao.NotificationDao
import com.habittracker.data.local.entity.NotificationReadStatusEntity
import com.habittracker.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория для управления статусами уведомлений
 */
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao
) : NotificationRepository {
    
    override suspend fun saveReadStatus(
        notificationId: String,
        userId: String,
        habitId: String,
        isRead: Boolean,
        readAt: Instant?
    ) {
        val entity = NotificationReadStatusEntity(
            notificationId = notificationId,
            userId = userId,
            habitId = habitId,
            isRead = isRead,
            readAt = readAt,
            createdAt = Clock.System.now()
        )
        notificationDao.insertReadStatus(entity)
    }
    
    override suspend fun getReadStatus(notificationId: String): Boolean {
        return notificationDao.getReadStatus(notificationId)?.isRead ?: false
    }
    
    override fun getReadStatusesForUser(userId: String): Flow<Map<String, Boolean>> {
        return notificationDao.getReadStatusesForUser(userId).map { statuses ->
            statuses.associate { it.notificationId to it.isRead }
        }
    }
    
    override suspend fun markAsRead(notificationId: String, readAt: Instant) {
        notificationDao.markAsRead(notificationId, readAt.toEpochMilliseconds())
    }
    
    override suspend fun markAllAsReadForUser(userId: String, readAt: Instant) {
        notificationDao.markAllAsReadForUser(userId, readAt.toEpochMilliseconds())
    }
    
    override fun getUnreadCountForUser(userId: String): Flow<Int> {
        return notificationDao.getUnreadCountForUser(userId)
    }
    
    override suspend fun deleteReadStatus(notificationId: String) {
        notificationDao.deleteReadStatus(notificationId)
    }
    
    override suspend fun deleteAllForUser(userId: String) {
        notificationDao.deleteAllForUser(userId)
    }
}
