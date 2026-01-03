package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Модель прогресса участника групповой привычки
 */
data class ParticipantProgress(
    val user: User,
    val completedToday: Boolean,
    val currentStreak: Int,
    val totalCompletions: Int,
    val completionRate: Float // от 0.0 до 1.0
)

/**
 * Модель совместного прогресса групповой привычки
 */
data class GroupHabitProgress(
    val habit: Habit,
    val participants: List<ParticipantProgress>,
    val totalParticipants: Int,
    val completedTodayCount: Int,
    val averageCompletionRate: Float,
    val groupStreak: Int // количество дней подряд, когда все участники выполняли привычку
)

/**
 * Use case для получения совместного прогресса групповой привычки
 */
class GetGroupHabitProgressUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val socialRepository: SocialRepository
) {
    /**
     * Получает совместный прогресс групповой привычки
     * @param habitId ID привычки
     * @param targetDate Дата для которой рассчитывается прогресс (по умолчанию - сегодня)
     * @return Flow с прогрессом группы
     */
    operator fun invoke(habitId: String, targetDate: LocalDate? = null): Flow<GroupHabitProgress?> {
        return combine(
            habitRepository.getHabitByIdFlow(habitId),
            habitEntryRepository.getEntriesForHabit(habitId)
        ) { habit, entries ->
            if (habit == null || !habit.isGroupHabit()) {
                return@combine null
            }

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val dateForProgress = targetDate ?: today
            
            // Для будущих дат прогресс всегда 0 (никто не может отметить привычку в будущем)
            val isFutureDate = dateForProgress > today
            // Убираем дубликаты: createdBy может уже быть в participants
            val allParticipants = (listOf(habit.createdBy) + habit.participants).distinct()

            val participantProgresses = allParticipants.mapNotNull { userId ->
                val user = socialRepository.getUserById(userId)
                if (user != null) {
                    calculateParticipantProgress(user, userId, entries, dateForProgress, isFutureDate)
                } else null
            }

            GroupHabitProgress(
                habit = habit,
                participants = participantProgresses,
                totalParticipants = participantProgresses.size,
                completedTodayCount = participantProgresses.count { it.completedToday },
                averageCompletionRate = if (participantProgresses.isNotEmpty()) {
                    participantProgresses.map { it.completionRate }.average().toFloat()
                } else 0f,
                groupStreak = calculateGroupStreak(participantProgresses, entries, allParticipants, today)
            )
        }
    }

    /**
     * Вычисляет прогресс отдельного участника
     */
    private fun calculateParticipantProgress(
        user: User,
        userId: String,
        entries: List<HabitEntry>,
        targetDate: LocalDate,
        isFutureDate: Boolean
    ): ParticipantProgress {
        // Для будущих дат прогресс всегда 0
        if (isFutureDate) {
            return ParticipantProgress(
                user = user,
                completedToday = false,
                currentStreak = 0,
                totalCompletions = 0,
                completionRate = 0f
            )
        }
        
        val userEntries = entries.filter { it.userId == userId }
        val completedEntries = userEntries.filter { it.completed }

        val completedToday = userEntries.any { it.date == targetDate && it.completed }
        val currentStreak = calculateCurrentStreak(userEntries, targetDate)
        val totalCompletions = completedEntries.size
        
        val completionRate = if (userEntries.isNotEmpty()) {
            completedEntries.size.toFloat() / userEntries.size.toFloat()
        } else 0f

        return ParticipantProgress(
            user = user,
            completedToday = completedToday,
            currentStreak = currentStreak,
            totalCompletions = totalCompletions,
            completionRate = completionRate
        )
    }

    /**
     * Вычисляет текущую серию выполнений для участника
     */
    private fun calculateCurrentStreak(entries: List<HabitEntry>, today: LocalDate): Int {
        val sortedEntries = entries.sortedByDescending { it.date }
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
     * Вычисляет групповую серию (дни подряд, когда все участники выполняли привычку)
     */
    private fun calculateGroupStreak(
        participantProgresses: List<ParticipantProgress>,
        entries: List<HabitEntry>,
        allParticipants: List<String>,
        today: LocalDate
    ): Int {
        if (participantProgresses.isEmpty()) return 0

        var streak = 0
        var currentDate = today

        while (true) {
            val completedOnDate = allParticipants.all { userId ->
                entries.any { entry ->
                    entry.userId == userId && entry.date == currentDate && entry.completed
                }
            }

            if (completedOnDate) {
                streak++
                currentDate = currentDate.minus(1, DateTimeUnit.DAY)
            } else {
                break
            }
        }

        return streak
    }
}
