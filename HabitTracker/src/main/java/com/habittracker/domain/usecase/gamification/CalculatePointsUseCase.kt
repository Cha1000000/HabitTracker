package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import javax.inject.Inject

/**
 * Use case для расчета очков за различные действия в приложении
 */
class CalculatePointsUseCase @Inject constructor() {

    companion object {
        // Базовые очки за действия
        const val POINTS_HABIT_COMPLETION = 10
        const val POINTS_STREAK_BONUS_PER_DAY = 2
        const val POINTS_PERFECT_DAY = 50 // все привычки выполнены
        const val POINTS_PERFECT_WEEK = 200
        const val POINTS_PERFECT_MONTH = 1000
        const val POINTS_HABIT_CREATION = 5
        const val POINTS_FRIEND_ADDED = 25
        const val POINTS_CHALLENGE_WON = 100
        const val POINTS_CHALLENGE_PARTICIPATED = 20
        const val POINTS_GROUP_HABIT_COMPLETION = 15 // бонус за групповые привычки
        
        // Множители для разных типов привычек
        const val MULTIPLIER_WEEKLY = 1.5f
        const val MULTIPLIER_MONTHLY = 2.0f
        const val MULTIPLIER_DIFFICULT = 1.3f // для привычек с высоким targetValue
        
        // Бонусы за время выполнения
        const val EARLY_BIRD_BONUS = 5 // выполнение до 8:00
        const val CONSISTENCY_BONUS = 3 // выполнение в одно и то же время
    }

    /**
     * Рассчитать очки за выполнение привычки
     */
    fun calculateHabitCompletionPoints(
        habit: Habit,
        entry: HabitEntry,
        currentStreak: Int,
        isGroupHabit: Boolean = false
    ): Int {
        var points = POINTS_HABIT_COMPLETION
        
        // Базовые очки с учетом частоты
        points = when (habit.frequency) {
            HabitFrequency.DAILY -> points
            HabitFrequency.WEEKLY -> (points * MULTIPLIER_WEEKLY).toInt()
            HabitFrequency.TWICE_A_WEEK -> (points * MULTIPLIER_WEEKLY).toInt()
            HabitFrequency.WEEKDAYS -> points
            HabitFrequency.WEEKENDS -> points
            HabitFrequency.CUSTOM -> points
        }
        
        // Бонус за сложность (высокий targetValue)
        if (habit.targetValue > 1) {
            points = (points * MULTIPLIER_DIFFICULT).toInt()
        }
        
        // Бонус за серию
        val streakBonus = currentStreak * POINTS_STREAK_BONUS_PER_DAY
        points += streakBonus
        
        // Бонус за групповую привычку
        if (isGroupHabit) {
            points += POINTS_GROUP_HABIT_COMPLETION - POINTS_HABIT_COMPLETION
        }
        
        // Бонус за раннее выполнение (до 8:00)
        entry.completedAt?.let { completedDateTime ->
            if (completedDateTime.time.hour < 8) {
                points += EARLY_BIRD_BONUS
            }
        }
        
        return points
    }

    /**
     * Рассчитать текущую серию для привычки
     */
    fun calculateCurrentStreak(
        habitId: String,
        entries: List<HabitEntry>,
        today: LocalDate
    ): Int {
        val habitEntries = entries.filter { it.habitId == habitId }
            .sortedByDescending { it.date }

        var streak = 0
        var currentDate = today

        for (entry in habitEntries) {
            if (entry.date == currentDate && entry.completed) {
                streak++
                currentDate = currentDate.minus(1, DateTimeUnit.DAY)
            } else if (entry.date < currentDate) {
                break
            }
        }

        return streak
    }

    /**
     * Рассчитать очки за идеальный день (все привычки выполнены)
     */
    fun calculatePerfectDayPoints(
        totalHabits: Int,
        completedHabits: Int
    ): Int {
        return if (totalHabits > 0 && completedHabits == totalHabits) {
            POINTS_PERFECT_DAY
        } else 0
    }

    /**
     * Рассчитать очки за идеальную неделю
     */
    fun calculatePerfectWeekPoints(
        perfectDaysInWeek: Int
    ): Int {
        return if (perfectDaysInWeek >= 7) {
            POINTS_PERFECT_WEEK
        } else 0
    }

    /**
     * Рассчитать очки за идеальный месяц
     */
    fun calculatePerfectMonthPoints(
        perfectDaysInMonth: Int,
        daysInMonth: Int
    ): Int {
        return if (perfectDaysInMonth >= daysInMonth) {
            POINTS_PERFECT_MONTH
        } else 0
    }

    /**
     * Рассчитать очки за создание привычки
     */
    fun calculateHabitCreationPoints(): Int = POINTS_HABIT_CREATION

    /**
     * Рассчитать очки за добавление друга
     */
    fun calculateFriendAddedPoints(): Int = POINTS_FRIEND_ADDED

    /**
     * Рассчитать очки за участие в челлендже
     */
    fun calculateChallengeParticipationPoints(): Int = POINTS_CHALLENGE_PARTICIPATED

    /**
     * Рассчитать очки за победу в челлендже
     */
    fun calculateChallengeWinPoints(
        challenge: Challenge,
        participantsCount: Int
    ): Int {
        var points = POINTS_CHALLENGE_WON
        
        // Бонус за количество участников
        val participantBonus = (participantsCount - 1) * 10
        points += participantBonus
        
        // Бонус за длительность челленджа
        val challengeDays = (challenge.endDate.toEpochDays() - challenge.startDate.toEpochDays()).toInt()
        val durationBonus = when {
            challengeDays >= 30 -> 50
            challengeDays >= 14 -> 25
            challengeDays >= 7 -> 10
            else -> 0
        }
        points += durationBonus
        
        return points
    }

    /**
     * Рассчитать бонусные очки за постоянство
     * (выполнение привычки в одно и то же время)
     */
    fun calculateConsistencyBonus(
        recentEntries: List<HabitEntry>,
        timeWindowMinutes: Int = 60
    ): Int {
        if (recentEntries.size < 3) return 0
        
        val completedEntries = recentEntries.filter { it.completed && it.completedAt != null }
        if (completedEntries.size < 3) return 0
        
        // Проверяем, выполняются ли привычки примерно в одно время
        val times = completedEntries.mapNotNull { entry ->
            entry.completedAt?.time
        }
        
        if (times.size < 3) return 0
        
        val averageMinutes = times.map { it.hour * 60 + it.minute }.average()
        val isConsistent = times.all { time ->
            val minutes = time.hour * 60 + time.minute
            val diff = kotlin.math.abs(minutes.toDouble() - averageMinutes)
            diff <= timeWindowMinutes.toDouble()
        }
        
        return if (isConsistent) CONSISTENCY_BONUS else 0
    }

    /**
     * Рассчитать общие очки пользователя за день
     */
    fun calculateDailyPoints(
        habitEntries: List<HabitEntry>,
        habits: List<Habit>,
        streaks: Map<String, Int>,
        date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    ): Int {
        var totalPoints = 0
        
        // Очки за выполненные привычки
        habitEntries.filter { it.date == date && it.completed }.forEach { entry ->
            val habit = habits.find { it.id == entry.habitId }
            if (habit != null) {
                val streak = streaks[habit.id] ?: 0
                val isGroup = habit.participants.isNotEmpty()
                totalPoints += calculateHabitCompletionPoints(habit, entry, streak, isGroup)
            }
        }
        
        // Бонус за идеальный день
        val totalHabitsForDay = habits.filter { it.isActive }.size
        val completedHabitsForDay = habitEntries.count { it.date == date && it.completed }
        totalPoints += calculatePerfectDayPoints(totalHabitsForDay, completedHabitsForDay)
        
        return totalPoints
    }
}
