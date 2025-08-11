package com.habittracker.domain.usecase.notification

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Use Case для отправки push-уведомлений участникам групповых привычек
 */
class SendGroupHabitPushNotificationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val habitRepository: HabitRepository
) {

    /**
     * Отправляет уведомление о выполнении привычки участником
     */
    suspend operator fun invoke(
        habitId: String,
        completedByUserId: String
    ): Result<Unit> {
        return try {
            val habit = habitRepository.getHabitById(habitId)
            val completedByUser = socialRepository.getUserById(completedByUserId)

            if (habit != null && completedByUser != null && habit.isGroupHabit()) {
                val participants = getHabitParticipants(habit, completedByUserId)

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
                                    "habit_title" to (habit.title ?: ""),
                                    "participant_name" to (completedByUser.name ?: ""),
                                    "screen" to "group_habit_detail"
                                )
                            )
                        }
                    }.awaitAll()
                }
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
        habitId: String,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val habit = habitRepository.getHabitById(habitId)
            val participants = getHabitParticipants(habit, excludeUserId)

            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "group_achievement",
                            title = "🏆 Групповое достижение!",
                            body = "Все участники выполнили \"${habit?.title}\" сегодня!",
                            data = mapOf(
                                "habit_id" to habitId,
                                "habit_title" to (habit?.title ?: ""),
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
        habitId: String,
        streakUserId: String,
        streak: Int,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val habit = habitRepository.getHabitById(habitId)
            val streakUser = socialRepository.getUserById(streakUserId)
            val participants = getHabitParticipants(habit, excludeUserId)

            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "participant_streak",
                            title = "🔥 Отличная серия!",
                            body = "${streakUser?.name} поддерживает серию $streak дней в \"${habit?.title}\"!",
                            data = mapOf(
                                "habit_id" to habitId,
                                "habit_title" to (habit?.title ?: ""),
                                "participant_name" to (streakUser?.name ?: ""),
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
        habitId: String,
        newParticipantId: String,
        excludeUserId: String? = null
    ): Result<Unit> {
        return try {
            val habit = habitRepository.getHabitById(habitId)
            val newParticipant = socialRepository.getUserById(newParticipantId)
            val participants = getHabitParticipants(habit, excludeUserId)

            coroutineScope {
                participants.map { participant ->
                    async {
                        sendPushNotification(
                            toUserId = participant.id,
                            type = "new_participant",
                            title = "👥 Новый участник",
                            body = "${newParticipant?.name} присоединился к \"${habit?.title}\"!",
                            data = mapOf(
                                "habit_id" to habitId,
                                "habit_title" to (habit?.title ?: ""),
                                "participant_name" to (newParticipant?.name ?: ""),
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
        habitId: String,
        fromUserId: String,
        toUserId: String,
        invitationId: String
    ): Result<Unit> {
        return try {
            val habit = habitRepository.getHabitById(habitId)
            val fromUser = socialRepository.getUserById(fromUserId)

            sendPushNotification(
                toUserId = toUserId,
                type = "habit_invitation",
                title = "📩 Приглашение в группу",
                body = "${fromUser?.name} приглашает вас в \"${habit?.title}\"",
                data = mapOf(
                    "habit_id" to habitId,
                    "habit_title" to (habit?.title ?: ""),
                    "from_user_name" to (fromUser?.name ?: ""),
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
     * Получает список участников привычки, исключая одного пользователя
     */
    private suspend fun getHabitParticipants(habit: Habit?, excludeUserId: String?): List<User> {
        if (habit == null) return emptyList()
        val participantIds = habit.participants.filter { it != excludeUserId }

        return coroutineScope {
            participantIds.map {
                async { socialRepository.getUserById(it) }
            }.awaitAll().filterNotNull()
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
        data: Map<String, String>
    ): Result<Unit> {
        return try {
            val user = socialRepository.getUserById(toUserId)
            val fcmToken = user?.fcmToken

            if (fcmToken != null && fcmToken.isNotBlank()) {
                socialRepository.sendPushNotification(
                    fcmToken = fcmToken,
                    title = title,
                    body = body,
                    data = data + mapOf("notification_type" to type)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
