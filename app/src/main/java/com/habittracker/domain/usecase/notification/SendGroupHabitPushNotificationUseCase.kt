package com.habittracker.domain.usecase.notification

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Use Case для отправки push-уведомлений участникам групповых привычек
 */
class SendGroupHabitPushNotificationUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {

    /**
     * Отправляет уведомление о выполнении привычки участником
     */
    suspend fun sendParticipantCompletedNotification(
        habit: Habit,
        completedByUser: User,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val participants = getHabitParticipants(habit, excludeUserId)
            
            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "group_habit_completed",
                            title = "🎯 ${habit.title}",
                            body = "${completedByUser.name} выполнил привычку!",
                            data = mapOf(
                                "habit_id" to habit.id,
                                "habit_title" to habit.title,
                                "participant_name" to completedByUser.name,
                                "screen" to "group_habit_detail"
                            )
                        )
                    }
                }.awaitAll()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление о групповом достижении
     */
    suspend fun sendGroupAchievementNotification(
        habit: Habit,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val participants = getHabitParticipants(habit, excludeUserId)
            
            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "group_achievement",
                            title = "🏆 Групповое достижение!",
                            body = "Все участники выполнили \"${habit.title}\" сегодня!",
                            data = mapOf(
                                "habit_id" to habit.id,
                                "habit_title" to habit.title,
                                "screen" to "group_habit_detail"
                            )
                        )
                    }
                }.awaitAll()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление о серии участника
     */
    suspend fun sendParticipantStreakNotification(
        habit: Habit,
        streakUser: User,
        streak: Int,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val participants = getHabitParticipants(habit, excludeUserId)
            
            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "participant_streak",
                            title = "🔥 Отличная серия!",
                            body = "${streakUser.name} поддерживает серию $streak дней в \"${habit.title}\"!",
                            data = mapOf(
                                "habit_id" to habit.id,
                                "habit_title" to habit.title,
                                "participant_name" to streakUser.name,
                                "streak" to streak.toString(),
                                "screen" to "group_habit_detail"
                            )
                        )
                    }
                }.awaitAll()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление о новом участнике в группе
     */
    suspend fun sendNewParticipantNotification(
        habit: Habit,
        newParticipant: User,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val participants = getHabitParticipants(habit, excludeUserId)
            
            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "new_participant",
                            title = "👥 Новый участник",
                            body = "${newParticipant.name} присоединился к \"${habit.title}\"!",
                            data = mapOf(
                                "habit_id" to habit.id,
                                "habit_title" to habit.title,
                                "participant_name" to newParticipant.name,
                                "screen" to "group_habit_detail"
                            )
                        )
                    }
                }.awaitAll()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление о приглашении в привычку
     */
    suspend fun sendHabitInvitationNotification(
        habit: Habit,
        fromUser: User,
        toUserId: String,
        invitationId: String
    ): Result<Unit> {
        return try {
            sendPushNotification(
                toUserId = toUserId,
                type = "habit_invitation",
                title = "📩 Приглашение в группу",
                body = "${fromUser.name} приглашает вас в \"${habit.title}\"",
                data = mapOf(
                    "habit_id" to habit.id,
                    "habit_title" to habit.title,
                    "from_user_name" to fromUser.name,
                    "invitation_id" to invitationId,
                    "screen" to "notifications"
                )
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получает список участников привычки (исключая указанного пользователя)
     */
    private suspend fun getHabitParticipants(
        habit: Habit,
        excludeUserId: String?
    ): List<User> {
        val allParticipantIds = listOf(habit.createdBy) + habit.participants
        val participantIds = if (excludeUserId != null) {
            allParticipantIds.filter { it != excludeUserId }
        } else {
            allParticipantIds
        }
        
        return participantIds.mapNotNull { userId ->
            try {
                socialRepository.getUserById(userId)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Отправляет push-уведомление конкретному пользователю
     */
    private suspend fun sendPushNotification(
        toUserId: String,
        type: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        try {
            val user = socialRepository.getUserById(toUserId)
            if (user?.fcmToken != null) {
                socialRepository.sendPushNotification(
                    fcmToken = user.fcmToken,
                    title = title,
                    body = body,
                    data = data + mapOf("type" to type)
                )
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем выполнение для других участников
            // TODO: Добавить логирование
        }
    }
}
