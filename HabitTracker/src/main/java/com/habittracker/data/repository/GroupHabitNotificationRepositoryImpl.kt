package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.GroupHabitNotificationDao
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.domain.model.GroupHabitNotificationModel
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация repository для работы с уведомлениями групповых привычек
 * Синхронизирует уведомления через Firestore для отображения на всех устройствах
 */
@Singleton
class GroupHabitNotificationRepositoryImpl @Inject constructor(
    private val groupHabitNotificationDao: GroupHabitNotificationDao,
    private val firestore: FirebaseFirestore,
    private val syncManager: com.habittracker.data.sync.SyncManager
) : GroupHabitNotificationRepository {
    
    companion object {
        private const val TAG = "GroupHabitNotifRepo"
        private const val COLLECTION_NOTIFICATIONS = "group_habit_notifications"
    }
    
    // Хранилище активных listeners для предотвращения дублирования
    private val activeListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override suspend fun saveNotification(notification: GroupHabitNotificationEntity) {
        try {
            // 1. Save locally first (Room-first)
            groupHabitNotificationDao.insertNotification(notification)
            Log.d(TAG, "Notification saved locally: ${notification.id}")

            // 2. Enqueue Sync (Outbox)
            val domainModel = notification.toDomain()
            val payload = com.habittracker.domain.util.SyncPayloads.groupHabitNotificationToJson(domainModel)
            
            syncManager.enqueueUpsert(
                entityType = "group_habit_notification",
                entityId = notification.id,
                payloadJson = payload,
                updatedAt = notification.createdAt // or now
            )
            Log.d(TAG, "Notification enqueued for sync: ${notification.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notification", e)
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
                
                val docs = snapshot?.documents ?: emptyList()
                if (docs.isEmpty()) return@addSnapshotListener

                syncScope.launch {
                    docs.forEach { doc ->
                        try {
                            val remote = GroupHabitNotificationEntity(
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

                            val hasPending = try {
                                syncManager.hasPendingOperations("group_habit_notification", remote.id)
                            } catch (e: Exception) {
                                Log.w(TAG, "hasPendingOperations failed for id=${remote.id}: ${e.message}")
                                false
                            }
                            if (hasPending) return@forEach

                            val local = try {
                                groupHabitNotificationDao.getNotificationById(remote.id)
                            } catch (e: Exception) {
                                Log.w(TAG, "Local getNotificationById failed for id=${remote.id}: ${e.message}")
                                null
                            }

                            val merged = if (local != null) {
                                val localReadAt = local.readAt ?: 0L
                                val remoteReadAt = remote.readAt ?: 0L
                                when {
                                    local.isRead && (!remote.isRead || remoteReadAt < localReadAt) -> local
                                    else -> remote
                                }
                            } else remote

                            groupHabitNotificationDao.insertNotification(merged)
                            Log.d(TAG, "Notification synced from Firestore: ${remote.id}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing notification from Firestore", e)
                        }
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
        // 1. Update locally
        val now = System.currentTimeMillis()
        groupHabitNotificationDao.markAsRead(notificationId, now)
        
        // 2. Get updated notification to sync
        val notification = groupHabitNotificationDao.getNotificationById(notificationId)
        if (notification != null) {
            val payload = com.habittracker.domain.util.SyncPayloads.groupHabitNotificationToJson(notification.toDomain())
            syncManager.enqueueUpsert(
                entityType = "group_habit_notification",
                entityId = notificationId,
                payloadJson = payload,
                updatedAt = now
            )
            Log.d(TAG, "Notification marked as read enqueued: $notificationId")
        }
    }
    
    override suspend fun deleteNotification(notificationId: String) {
        // 1. Delete locally
        groupHabitNotificationDao.deleteNotification(notificationId)
        
        // 2. Enqueue delete
        syncManager.enqueueDelete(
            entityType = "group_habit_notification",
            entityId = notificationId,
            updatedAt = System.currentTimeMillis()
        )
        Log.d(TAG, "Notification delete enqueued: $notificationId")
    }
    
    override suspend fun deleteReadNotificationsOlderThan(userId: String, olderThanMillis: Long) {
        // 1. Find notifications to delete locally
        val notificationsToDelete = groupHabitNotificationDao.getReadNotificationsOlderThan(userId, olderThanMillis)
        
        if (notificationsToDelete.isNotEmpty()) {
            // Delete locally
            groupHabitNotificationDao.deleteReadNotificationsOlderThan(userId, olderThanMillis)
            
            // Enqueue deletes
            val now = System.currentTimeMillis()
            notificationsToDelete.forEach { notification ->
                syncManager.enqueueDelete(
                    entityType = "group_habit_notification",
                    entityId = notification.id,
                    updatedAt = now
                )
            }
            Log.d(TAG, "Enqueued delete for ${notificationsToDelete.size} old notifications")
        }
    }
    
    override suspend fun deleteAllForUser(userId: String) {
        try {
            // 1. Fetch all notifications for user locally to get IDs for sync
            val list = groupHabitNotificationDao.getAllNotificationsForUserSync(userId)
            
            if (list.isNotEmpty()) {
                // 2. Delete locally
                groupHabitNotificationDao.deleteAllForUser(userId)
                
                // 3. Enqueue deletes
                val now = System.currentTimeMillis()
                list.forEach { notification ->
                    syncManager.enqueueDelete(
                        entityType = "group_habit_notification",
                        entityId = notification.id,
                        updatedAt = now
                    )
                }
                Log.d(TAG, "Enqueued delete all for user $userId (${list.size} items)")
            }
        } catch (e: Exception) {
             Log.e(TAG, "Failed to deleteAllForUser", e)
             // Fallback local delete just in case fetch failed
             groupHabitNotificationDao.deleteAllForUser(userId)
        }
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
            createdAt = createdAt,
            titleResId = titleResId,
            descriptionResId = descriptionResId
        )
    }
}
