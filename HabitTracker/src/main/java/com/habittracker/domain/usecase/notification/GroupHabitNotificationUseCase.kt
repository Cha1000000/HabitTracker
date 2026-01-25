package com.habittracker.domain.usecase.notification

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.User
import com.habittracker.R
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.NotificationRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import javax.inject.Inject
import timber.log.Timber

/**
 * Use Case для управления уведомлениями групповых привычек
 */
class GroupHabitNotificationUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val socialRepository: SocialRepository,
    private val notificationRepository: NotificationRepository,
    private val stringProvider: StringProvider
) {
    
    /**
     * Получает все уведомления для пользователя
     */
    fun getGroupHabitNotifications(userId: String): Flow<List<GroupHabitNotification>> = flow {
        try {
            Timber.tag(TAG).d("getGroupHabitNotifications called for userId=$userId")
            val notifications = mutableListOf<GroupHabitNotification>()
            
            // Получаем сохраненные статусы прочтения
            val readStatuses = notificationRepository.getReadStatusesForUser(userId).first()
            Timber.tag(TAG).d("Loaded ${readStatuses.size} read statuses")
            
            // Получаем групповые привычки пользователя
            habitRepository.getGroupHabits(userId).collect { groupHabits ->
                Timber.tag(TAG).d("Found ${groupHabits.size} group habits for user")
                for (habit in groupHabits) {
                    Timber.tag(TAG).d("Processing habit: ${habit.title} (${habit.id})")
                    // Генерируем уведомления для каждой групповой привычки
                    val habitNotifications = generateNotificationsForHabit(habit, userId)
                    Timber.tag(TAG).d("Generated ${habitNotifications.size} notifications for habit ${habit.title}")
                    
                    // Применяем сохраненные статусы прочтения
                    val notificationsWithStatus = habitNotifications.map { notification ->
                        val isRead = readStatuses[notification.id] ?: false
                        notification.copy(isRead = isRead)
                    }
                    
                    notifications.addAll(notificationsWithStatus)
                }
                
                Timber.tag(TAG).d("Total notifications to emit: ${notifications.size}")
                emit(notifications.sortedByDescending { it.timestamp })
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading notifications")
            emit(emptyList())
        }
    }
    
    companion object {
        private const val TAG = "GroupHabitNotifUseCase"
    }
    
    /**
     * Генерирует уведомления для конкретной привычки и сохраняет их статусы
     */
    private suspend fun generateNotificationsForHabit(
        habit: Habit,
        currentUserId: String
    ): List<GroupHabitNotification> {
        val notifications = mutableListOf<GroupHabitNotification>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        try {
            // Получаем полный список участников (создатель + participants)
            val allParticipantIds = (listOf(habit.createdBy) + habit.participants).distinct()
            Timber.tag(TAG).d("Habit ${habit.title}: ${allParticipantIds.size} participants (creator + ${habit.participants.size} others)")
            
            // Получаем ВСЕ записи выполнения для привычки (для всех участников)
            val startDate = today.minus(7, DateTimeUnit.DAY)
            val allHabitEntries = habitEntryRepository.getEntriesForHabit(habit.id).first()
            
            // Фильтруем записи за последние 7 дней
            val allEntries = allHabitEntries.filter { entry ->
                entry.date >= startDate && entry.date <= today
            }
            
            Timber.tag(TAG).d("Loaded ${allEntries.size} total entries for habit ${habit.title} (from ${allHabitEntries.size} total)")
            
            // Группируем записи по участникам
            val entriesByUser = allEntries.groupBy { it.userId }
            
            // Генерируем уведомления о выполнении участниками
            for (participantId in allParticipantIds) {
                if (participantId == currentUserId) continue // Не уведомляем о собственных действиях
                
                val participantEntries = entriesByUser[participantId] ?: emptyList()
                val participant = getParticipantInfo(participantId)
                val participantName = participant?.name ?: stringProvider.getString(R.string.default_participant_name)
                
                // Уведомление о выполнении сегодня
                val todayEntry = participantEntries.find { entry -> entry.date == today && entry.completed }
                Timber.tag(TAG).d("Participant $participantId: todayEntry=${todayEntry != null}")
                if (todayEntry != null) {
                    // Генерируем стабильный ID на основе данных уведомления
                    val notificationId = generateNotificationId(
                        type = "PARTICIPANT_COMPLETED",
                        habitId = habit.id,
                        participantId = participantId,
                        date = today.toString()
                    )
                    
                    val notification = GroupHabitNotification(
                        id = notificationId,
                        type = NotificationType.PARTICIPANT_COMPLETED,
                        habitId = habit.id,
                        habitTitle = habit.title,
                        participantName = participantName,
                        message = stringProvider.getString(R.string.notification_participant_completed_message, participantName, habit.title),
                        timestamp = todayEntry.completedAt?.toInstant(TimeZone.currentSystemDefault()) ?: Clock.System.now(),
                        isRead = false
                    )
                    notifications.add(notification)
                    
                    // Сохраняем статус нового уведомления
                    saveNotificationStatus(notification, currentUserId)
                }
                
                // Уведомление о серии выполнений
                val streak = calculateStreak(participantEntries, today)
                if (streak >= 7 && streak % 7 == 0) {
                    val notificationId = generateNotificationId(
                        type = "PARTICIPANT_STREAK",
                        habitId = habit.id,
                        participantId = participantId,
                        date = today.toString(),
                        extra = streak.toString()
                    )
                    
                    val notification = GroupHabitNotification(
                        id = notificationId,
                        type = NotificationType.PARTICIPANT_STREAK,
                        habitId = habit.id,
                        habitTitle = habit.title,
                        participantName = participantName,
                        message = stringProvider.getString(R.string.notification_participant_streak_message, participantName, streak, habit.title),
                        timestamp = Clock.System.now(),
                        isRead = false
                    )
                    notifications.add(notification)
                    
                    // Сохраняем статус нового уведомления
                    saveNotificationStatus(notification, currentUserId)
                }
            }
            
            // Уведомление о групповом достижении
            if (checkGroupAchievement(habit, entriesByUser, today)) {
                val notificationId = generateNotificationId(
                    type = "GROUP_ACHIEVEMENT",
                    habitId = habit.id,
                    participantId = "group",
                    date = today.toString()
                )
                
                val notification = GroupHabitNotification(
                    id = notificationId,
                    type = NotificationType.GROUP_ACHIEVEMENT,
                    habitId = habit.id,
                    habitTitle = habit.title,
                    message = stringProvider.getString(R.string.notification_group_achievement_message, habit.title),
                    timestamp = Clock.System.now(),
                    isRead = false
                )
                notifications.add(notification)
                
                // Сохраняем статус нового уведомления
                saveNotificationStatus(notification, currentUserId)
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error generating notifications for habit ${habit.title}")
        }
        
        return notifications
    }
    
    /**
     * Получает информацию об участнике
     */
    private suspend fun getParticipantInfo(userId: String): User? {
        return try {
            socialRepository.getUserById(userId)
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Вычисляет текущую серию выполнений
     */
    private fun calculateStreak(entries: List<HabitEntry>, today: LocalDate): Int {
        val sortedEntries = entries
            .sortedByDescending { it.date }
        
        var streak = 0
        var currentDate = today
        
        for (entry in sortedEntries) {
            if (entry.date == currentDate) {
                if (entry.completed) {
                    streak++
                    currentDate = currentDate.minus(1, DateTimeUnit.DAY)
                } else if (entry.isRestDay) {
                    currentDate = currentDate.minus(1, DateTimeUnit.DAY)
                } else {
                    break
                }
            } else if (entry.date < currentDate) {
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
        // Получаем полный список участников (создатель + participants)
        val allParticipantIds = (listOf(habit.createdBy) + habit.participants).distinct()
        
        if (allParticipantIds.size <= 1) return false
        
        return allParticipantIds.all { participantId ->
            val userEntries = entriesByUser[participantId] ?: emptyList()
            userEntries.any { it.date == today && it.completed }
        }
    }
    
    /**
     * Генерирует стабильный ID для уведомления на основе его данных
     */
    private fun generateNotificationId(
        type: String,
        habitId: String,
        participantId: String,
        date: String,
        extra: String = ""
    ): String {
        val components = listOf(type, habitId, participantId, date, extra)
        return components.joinToString("_")
    }
    
    /**
     * Сохраняет статус нового уведомления в базу данных
     */
    private suspend fun saveNotificationStatus(
        notification: GroupHabitNotification,
        userId: String
    ) {
        try {
            notificationRepository.saveReadStatus(
                notificationId = notification.id,
                userId = userId,
                habitId = notification.habitId,
                isRead = notification.isRead,
                readAt = null
            )
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем генерацию уведомлений
            Timber.tag(TAG).e(e, "Error saving notification for user $userId")
        }
    }
    
    /**
     * Отмечает уведомление как прочитанное
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            notificationRepository.markAsRead(notificationId, Clock.System.now())
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
            notificationRepository.markAllAsReadForUser(userId, Clock.System.now())
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
    PARTICIPANT_JOINED,      // Новый участник присоединился (alias для FRIEND_JOINED)
    FRIEND_JOINED,           // Друг присоединился к привычке
    HABIT_REMINDER,          // Напоминание о привычке
    ACHIEVEMENT_UNLOCKED     // Достижение разблокировано
}
