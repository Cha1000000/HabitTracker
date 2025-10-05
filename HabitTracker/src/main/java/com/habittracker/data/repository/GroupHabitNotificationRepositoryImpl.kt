package com.habittracker.data.repository

import com.habittracker.data.local.dao.GroupHabitNotificationDao
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.domain.model.GroupHabitNotificationModel
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация repository для работы с уведомлениями групповых привычек
 */
@Singleton
class GroupHabitNotificationRepositoryImpl @Inject constructor(
    private val groupHabitNotificationDao: GroupHabitNotificationDao
) : GroupHabitNotificationRepository {
    
    override fun getNotificationsForUser(userId: String): Flow<List<GroupHabitNotificationModel>> {
        return groupHabitNotificationDao.getNotificationsForUser(userId)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override fun getUnreadCount(userId: String): Flow<Int> {
        return groupHabitNotificationDao.getUnreadCount(userId)
    }
    
    override suspend fun markAsRead(notificationId: String) {
        groupHabitNotificationDao.markAsRead(notificationId, System.currentTimeMillis())
    }
    
    override suspend fun markAllAsRead(userId: String) {
        val notifications = groupHabitNotificationDao.getNotificationsForUser(userId)
        // Получаем текущий список и обновляем все непрочитанные
        // Для простоты используем прямой SQL запрос из DAO
        val readAt = System.currentTimeMillis()
        // Обновляем все непрочитанные уведомления
        // TODO: добавить метод в DAO для массового обновления
    }
    
    override suspend fun deleteNotification(notificationId: String) {
        groupHabitNotificationDao.deleteNotification(notificationId)
    }
    
    override suspend fun deleteReadNotificationsOlderThan(userId: String, olderThanMillis: Long) {
        groupHabitNotificationDao.deleteReadNotificationsOlderThan(userId, olderThanMillis)
    }
    
    override suspend fun deleteAllForUser(userId: String) {
        groupHabitNotificationDao.deleteAllForUser(userId)
    }
    
    /**
     * Маппер из Entity в Domain модель
     */
    private fun GroupHabitNotificationEntity.toDomain(): GroupHabitNotificationModel {
        return GroupHabitNotificationModel(
            id = id,
            userId = userId,
            habitId = habitId,
            type = type,
            participantId = participantId,
            participantName = participantName,
            habitTitle = habitTitle,
            message = message,
            isRead = isRead,
            readAt = readAt,
            createdAt = createdAt
        )
    }
}
