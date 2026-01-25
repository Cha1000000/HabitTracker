package com.habittracker.domain.usecase.notification

import com.habittracker.R
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use Case для отправки push-уведомлений участникам групповых привычек
 */
class SendGroupHabitPushNotificationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val habitRepository: HabitRepository,
    private val firestoreRepository: com.habittracker.domain.repository.FirestoreRepository,
    private val notificationRepository: GroupHabitNotificationRepository,
    private val stringProvider: StringProvider
) {
    
    companion object {
        private const val TAG = "SendGroupHabitPush"
    }

    /**
     * Отправляет уведомление о выполнении привычки участником
     */
    suspend operator fun invoke(
        habitId: String,
        completedByUserId: String
    ): Result<Unit> {
        return try {
            Timber.tag(TAG).d("invoke: habitId=$habitId, completedByUserId=$completedByUserId")
            
            // ВСЕГДА загружаем привычку из Firestore для получения актуального списка участников
            // Локальная БД может быть не синхронизирована
            Timber.tag(TAG).d("Loading habit from Firestore to get fresh participant list...")
            var habit = firestoreRepository.getHabitById(habitId)
            Timber.tag(TAG).d("Firestore result: ${if (habit != null) "found ${habit.title}" else "not found"}")
            
            // Если не нашли в Firestore, пробуем локальную БД (fallback)
            if (habit == null) {
                Timber.tag(TAG).d("Habit not found in Firestore, trying local DB...")
                habit = habitRepository.getHabitById(habitId)
            }
            
            val completedByUser = socialRepository.getUserById(completedByUserId)
            
            Timber.tag(TAG).d("Habit loaded: ${habit?.title}, type=${habit?.type}, isGroup=${habit?.isGroupHabit()}, participants=${habit?.participants?.size}")
            Timber.tag(TAG).d("User loaded: ${completedByUser?.name}")

            if (habit != null && completedByUser != null && habit.isGroupHabit()) {
                Timber.tag(TAG).d("Habit details: createdBy=${habit.createdBy}, participants=${habit.participants}, completedBy=$completedByUserId")
                val participants = getHabitParticipants(habit, completedByUserId)
                Timber.tag(TAG).d("Sending push to ${participants.size} participants: ${participants.map { "${it.name}(${it.id})" }}")

                val message = stringProvider.getString(R.string.push_habit_completed_body, completedByUser.name)
                val currentTime = System.currentTimeMillis()

                coroutineScope {
                    participants.map { participant ->
                        async {
                            Timber.tag(TAG).d("Sending push to participant: ${participant.name} (${participant.id})")
                            sendPushNotification(
                                toUserId = participant.id,
                                type = "group_habit_completed",
                                title = stringProvider.getString(R.string.push_habit_completed_title, habit.title),
                                body = message,
                                data = mapOf(
                                    "habit_id" to habit.id,
                                    "habit_title" to habit.title,
                                    "participant_name" to completedByUser.name,
                                    "screen" to "group_habit_detail"
                                )
                            )
                            
                            // Сохраняем уведомление в локальную БД для отображения в UI
                            try {
                                val notificationEntity = GroupHabitNotificationEntity(
                                    id = UUID.randomUUID().toString(),
                                    userId = participant.id,
                                    habitId = habit.id,
                                    type = "PARTICIPANT_COMPLETED",
                                    participantId = completedByUserId,
                                    participantName = completedByUser.name,
                                    habitTitle = habit.title,
                                    message = message,
                                    isRead = false,
                                    readAt = null,
                                    createdAt = currentTime
                                )
                                notificationRepository.saveNotification(notificationEntity)
                                Timber.tag(TAG).d("Notification saved to DB for user: ${participant.id}")
                            } catch (e: Exception) {
                                Timber.tag(TAG).w("Failed to save notification to DB: ${e.message}")
                            }
                        }
                    }.awaitAll()
                }
            } else {
                Timber.tag(TAG).w("Push not sent: habit=${habit != null}, user=${completedByUser != null}, isGroup=${habit?.isGroupHabit()}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error sending push notification")
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление о выходе участника из привычки
     */
    suspend fun sendParticipantLeftNotification(
        habitId: String,
        leftUserId: String,
        remainingParticipants: List<String>
    ): Result<Unit> {
        return try {
            val habit = firestoreRepository.getHabitById(habitId) 
                ?: habitRepository.getHabitById(habitId)
            val leftUser = socialRepository.getUserById(leftUserId)

            if (habit != null && leftUser != null && remainingParticipants.isNotEmpty()) {
                val message = stringProvider.getString(R.string.push_participant_left_body, leftUser.name, habit.title)
                val currentTime = System.currentTimeMillis()
                
                coroutineScope {
                    remainingParticipants.map { participantId ->
                        async {
                            // Отправляем push-уведомление
                            sendPushNotification(
                                toUserId = participantId,
                                type = "participant_left",
                                title = stringProvider.getString(R.string.push_participant_left_title),
                                body = message,
                                data = mapOf(
                                    "habit_id" to habitId,
                                    "habit_title" to habit.title,
                                    "participant_name" to leftUser.name,
                                    "screen" to "group_habit_detail"
                                )
                            )
                            
                            // Сохраняем уведомление в локальную БД для отображения в UI
                            try {
                                val notificationEntity = GroupHabitNotificationEntity(
                                    id = UUID.randomUUID().toString(),
                                    userId = participantId,
                                    habitId = habitId,
                                    type = "PARTICIPANT_LEFT",
                                    participantId = leftUserId,
                                    participantName = leftUser.name,
                                    habitTitle = habit.title,
                                    message = message,
                                    isRead = false,
                                    readAt = null,
                                    createdAt = currentTime
                                )
                                notificationRepository.saveNotification(notificationEntity)
                                Timber.tag(TAG).d("Notification saved to DB for user: $participantId")
                            } catch (e: Exception) {
                                Timber.tag(TAG).w("Failed to save notification to DB: ${e.message}")
                            }
                        }
                    }.awaitAll()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send participant left notification")
            Result.failure(e)
        }
    }

    /**
     * Получает список участников привычки (включая создателя), исключая одного пользователя
     */
    private suspend fun getHabitParticipants(habit: Habit?, excludeUserId: String?): List<User> {
        if (habit == null) return emptyList()
        
        // Включаем создателя и всех участников
        val allParticipantIds = (listOf(habit.createdBy) + habit.participants).distinct()
        val participantIds = allParticipantIds.filter { it != excludeUserId }

        return coroutineScope {
            participantIds.map {
                async { socialRepository.getUserById(it) }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Отправляет push-уведомление конкретному пользователю с retry механизмом
     */
    private suspend fun sendPushNotification(
        toUserId: String,
        type: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): Result<Unit> {
        return try {
            Timber.tag(TAG).d("sendPushNotification to user: $toUserId")
            
            // ВСЕГДА загружаем актуальный токен из Firestore для push-уведомлений
            // Это критично, так как токены часто обновляются
            Timber.tag(TAG).d("Fetching fresh token from Firestore for user: $toUserId")
            val user = socialRepository.getUserByIdFromFirestore(toUserId)
            val fcmToken = user?.fcmToken
            
            Timber.tag(TAG).d("User FCM token: ${if (fcmToken.isNullOrBlank()) "EMPTY" else "EXISTS (masked: ${fcmToken.take(8)}...${fcmToken.takeLast(4)})"}") 

            if (fcmToken != null && fcmToken.isNotBlank()) {
                Timber.tag(TAG).d("Sending push via socialRepository: $title")
                socialRepository.sendPushNotification(
                    fcmToken = fcmToken,
                    title = title,
                    body = body,
                    data = data + mapOf(
                        // единый ключ типа уведомления
                        "type" to type,
                        // для клиентской фильтрации уведомлений
                        "recipientUserId" to toUserId
                    )
                )
                Timber.tag(TAG).d("Push sent successfully to $toUserId")
            } else {
                Timber.tag(TAG).w("FCM token is empty for user $toUserId even after Firestore fetch")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send push to $toUserId")
            Result.failure(e)
        }
    }
}
