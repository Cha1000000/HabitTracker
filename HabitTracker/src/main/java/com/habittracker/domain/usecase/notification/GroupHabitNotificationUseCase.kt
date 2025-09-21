package com.habittracker.domain.usecase.notification

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import java.util.UUID
import javax.inject.Inject

/**
 * Use Case для управления уведомлениями групповых привычек
 */
class GroupHabitNotificationUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val socialRepository: SocialRepository
) {
    
    /**
     * Получает все уведомления для пользователя
     */
    fun getGroupHabitNotifications(userId: String): Flow<List<GroupHabitNotification>> = flow {
        try {
            val notifications = mutableListOf<GroupHabitNotification>()
            
            // Получаем групповые привычки пользователя
            habitRepository.getGroupHabits(userId).collect { groupHabits ->
                for (habit in groupHabits) {
                    // Генерируем уведомления для каждой групповой привычки
                    notifications.addAll(generateNotificationsForHabit(habit, userId))
                }
                
                emit(notifications.sortedByDescending { it.timestamp })
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
    
    /**
     * Генерирует уведомления для конкретной привычки
     */
    private suspend fun generateNotificationsForHabit(
        habit: Habit,
        currentUserId: String
    ): List<GroupHabitNotification> {
        val notifications = mutableListOf<GroupHabitNotification>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        try {
            // Получаем записи выполнения за последние 7 дней для каждого участника
            val startDate = today.minus(7, DateTimeUnit.DAY)
            val allEntries = mutableListOf<HabitEntry>()
            
            for (participantId in habit.participants) {
                val participantEntries = habitEntryRepository.getEntriesForUserAndPeriod(
                    habitId = habit.id,
                    userId = participantId,
                    startDate = startDate,
                    endDate = today
                )
                allEntries.addAll(participantEntries)
            }
            
            // Группируем записи по участникам
            val entriesByUser = allEntries.groupBy { it.userId }
            
            // Генерируем уведомления о выполнении участниками
            for (participantId in habit.participants) {
                if (participantId == currentUserId) continue // Не уведомляем о собственных действиях
                
                val participantEntries = entriesByUser[participantId] ?: emptyList()
                val participant = getParticipantInfo(participantId)
                
                // Уведомление о выполнении сегодня
                val todayEntry = participantEntries.find { entry -> entry.date == today && entry.completed }
                if (todayEntry != null) {
                    notifications.add(
                        GroupHabitNotification(
                            id = UUID.randomUUID().toString(),
                            type = NotificationType.PARTICIPANT_COMPLETED,
                            habitId = habit.id,
                            habitTitle = habit.title,
                            participantName = participant?.name ?: "Участник",
                            message = "${participant?.name ?: "Участник"} выполнил(а) привычку \"${habit.title}\"",
                            timestamp = Clock.System.now(),
                            isRead = false
                        )
                    )
                }
                
                // Уведомление о серии выполнений
                val streak = calculateStreak(participantEntries, today)
                if (streak >= 7 && streak % 7 == 0) {
                    notifications.add(
                        GroupHabitNotification(
                            id = UUID.randomUUID().toString(),
                            type = NotificationType.PARTICIPANT_STREAK,
                            habitId = habit.id,
                            habitTitle = habit.title,
                            participantName = participant?.name ?: "Участник",
                            message = "${participant?.name ?: "Участник"} поддерживает серию $streak дней в \"${habit.title}\"!",
                            timestamp = Clock.System.now(),
                            isRead = false
                        )
                    )
                }
            }
            
            // Уведомление о групповом достижении
            if (checkGroupAchievement(habit, entriesByUser, today)) {
                notifications.add(
                    GroupHabitNotification(
                        id = UUID.randomUUID().toString(),
                        type = NotificationType.GROUP_ACHIEVEMENT,
                        habitId = habit.id,
                        habitTitle = habit.title,
                        message = "Все участники выполнили \"${habit.title}\" сегодня! ",
                        timestamp = Clock.System.now(),
                        isRead = false
                    )
                )
            }
            
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем работу
        }
        
        return notifications
    }
    
    /**
     * Получает информацию об участнике
     */
    private suspend fun getParticipantInfo(userId: String): User? {
        return try {
            socialRepository.getUserById(userId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Вычисляет текущую серию выполнений
     */
    private fun calculateStreak(entries: List<HabitEntry>, today: LocalDate): Int {
        val sortedEntries = entries
            .filter { it.completed }
            .sortedByDescending { it.date }
        
        var streak = 0
        var currentDate = today
        
        for (entry in sortedEntries) {
            if (entry.date == currentDate) {
                streak++
                currentDate = currentDate.minus(1, DateTimeUnit.DAY)
            } else {
                break
            }
        }
        
        return streak
    }
    
    /**
     * Проверяет групповое достижение (все участники выполнили сегодня)
     */
    private fun checkGroupAchievement(
        habit: Habit,
        entriesByUser: Map<String, List<HabitEntry>>,
        today: LocalDate
    ): Boolean {
        if (habit.participants.size <= 1) return false
        
        return habit.participants.all { participantId ->
            val userEntries = entriesByUser[participantId] ?: emptyList()
            userEntries.any { it.date == today && it.completed }
        }
    }
    
    /**
     * Отмечает уведомление как прочитанное
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            // TODO: Реализовать сохранение состояния уведомлений в базе данных
            // Пока возвращаем успех
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Отмечает все уведомления пользователя как прочитанные
     */
    suspend fun markAllNotificationsAsRead(userId: String): Result<Unit> {
        return try {
            // TODO: Реализовать массовую отметку уведомлений
            // Пока возвращаем успех
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Модель уведомления групповой привычки
 */
data class GroupHabitNotification(
    val id: String,
    val type: NotificationType,
    val habitId: String,
    val habitTitle: String,
    val participantName: String? = null,
    val message: String,
    val timestamp: Instant,
    val isRead: Boolean = false
)

/**
 * Типы уведомлений
 */
enum class NotificationType {
    PARTICIPANT_COMPLETED,    // Участник выполнил привычку
    PARTICIPANT_STREAK,       // Участник поддерживает серию
    GROUP_ACHIEVEMENT,        // Групповое достижение
    NEW_INVITATION,          // Новое приглашение
    INVITATION_ACCEPTED,     // Приглашение принято
    PARTICIPANT_JOINED,      // Новый участник присоединился
    HABIT_REMINDER          // Напоминание о привычке
}
