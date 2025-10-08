package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.GroupHabitNotificationDao
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.domain.model.GroupHabitNotificationModel
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация repository для работы с уведомлениями групповых привычек
 * Синхронизирует уведомления через Firestore для отображения на всех устройствах
 */
@Singleton
class GroupHabitNotificationRepositoryImpl @Inject constructor(
    private val groupHabitNotificationDao: GroupHabitNotificationDao,
    private val firestore: FirebaseFirestore
) : GroupHabitNotificationRepository {
    
    companion object {
        private const val TAG = "GroupHabitNotifRepo"
        private const val COLLECTION_NOTIFICATIONS = "group_habit_notifications"
    }
    
    // Хранилище активных listeners для предотвращения дублирования
    private val activeListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    
    override suspend fun saveNotification(notification: GroupHabitNotificationEntity) {
        try {
            // Сохраняем ТОЛЬКО в Firestore
            // Firestore listener автоматически синхронизирует в локальную БД
            val notificationData = hashMapOf(
                "id" to notification.id,
                "userId" to notification.userId,
                "habitId" to notification.habitId,
                "type" to notification.type,
                "participantId" to notification.participantId,
                "participantName" to notification.participantName,
                "habitTitle" to notification.habitTitle,
                "message" to notification.message,
                "isRead" to notification.isRead,
                "readAt" to notification.readAt,
                "createdAt" to notification.createdAt
            )
            
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notification.id)
                .set(notificationData)
                .await()
            
            Log.d(TAG, "Notification saved to Firestore: ${notification.id} for user ${notification.userId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notification to Firestore, saving locally as fallback", e)
            // Если Firestore недоступен, сохраняем локально как fallback
            groupHabitNotificationDao.insertNotification(notification)
        }
    }
    
    override fun getNotificationsForUser(userId: String): Flow<List<GroupHabitNotificationModel>> {
        // Запускаем Firestore listener для синхронизации (только если ещё не запущен)
        if (!activeListeners.containsKey(userId)) {
            syncNotificationsFromFirestore(userId)
        }
        
        // Возвращаем Flow из локальной БД
        return groupHabitNotificationDao.getNotificationsForUser(userId)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * Синхронизирует уведомления из Firestore в локальную БД
     */
    private fun syncNotificationsFromFirestore(userId: String) {
        val listener = firestore.collection(COLLECTION_NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to notifications", error)
                    return@addSnapshotListener
                }
                
                snapshot?.documents?.forEach { doc ->
                    try {
                        val notification = GroupHabitNotificationEntity(
                            id = doc.getString("id") ?: return@forEach,
                            userId = doc.getString("userId") ?: return@forEach,
                            habitId = doc.getString("habitId") ?: "",
                            type = doc.getString("type") ?: "",
                            participantId = doc.getString("participantId") ?: "",
                            participantName = doc.getString("participantName") ?: "",
                            habitTitle = doc.getString("habitTitle") ?: "",
                            message = doc.getString("message") ?: "",
                            isRead = doc.getBoolean("isRead") ?: false,
                            readAt = doc.getLong("readAt"),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                        
                        // Сохраняем в локальную БД асинхронно
                        CoroutineScope(Dispatchers.IO).launch {
                            groupHabitNotificationDao.insertNotification(notification)
                            Log.d(TAG, "Notification synced from Firestore: ${notification.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing notification from Firestore", e)
                    }
                }
            }
        
        // Сохраняем listener для предотвращения дублирования
        activeListeners[userId] = listener
        Log.d(TAG, "Firestore listener registered for user: $userId")
    }
    
    override fun getUnreadCount(userId: String): Flow<Int> {
        return groupHabitNotificationDao.getUnreadCount(userId)
    }
    
    override suspend fun markAsRead(notificationId: String) {
        try {
            // Обновляем в Firestore
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update("isRead", true, "readAt", System.currentTimeMillis())
                .await()
            Log.d(TAG, "Notification marked as read in Firestore: $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark notification as read in Firestore", e)
        }
        
        // Обновляем локально
        groupHabitNotificationDao.markAsRead(notificationId, System.currentTimeMillis())
    }
    
    override suspend fun deleteNotification(notificationId: String) {
        try {
            // Удаляем из Firestore
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .delete()
                .await()
            Log.d(TAG, "Notification deleted from Firestore: $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete notification from Firestore", e)
        }
        
        // Удаляем локально
        groupHabitNotificationDao.deleteNotification(notificationId)
    }
    
    override suspend fun deleteReadNotificationsOlderThan(userId: String, olderThanMillis: Long) {
        try {
            // Удаляем из Firestore
            val snapshot = firestore.collection(COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", true)
                .whereLessThan("readAt", olderThanMillis)
                .get()
                .await()
            
            snapshot.documents.forEach { doc ->
                doc.reference.delete()
            }
            Log.d(TAG, "Deleted ${snapshot.size()} old notifications from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old notifications from Firestore", e)
        }
        
        // Удаляем локально
        groupHabitNotificationDao.deleteReadNotificationsOlderThan(userId, olderThanMillis)
    }
    
    override suspend fun deleteAllForUser(userId: String) {
        try {
            // Удаляем из Firestore
            val snapshot = firestore.collection(COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            snapshot.documents.forEach { doc ->
                doc.reference.delete()
            }
            Log.d(TAG, "Deleted all notifications from Firestore for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all notifications from Firestore", e)
        }
        
        // Удаляем локально
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
