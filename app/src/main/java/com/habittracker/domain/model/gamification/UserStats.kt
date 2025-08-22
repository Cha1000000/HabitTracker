package com.habittracker.domain.model.gamification

import com.habittracker.domain.model.User
import com.habittracker.domain.model.UserLevelInfo

/**
 * Полная статистика пользователя.
 */
data class UserStats(
    val user: User,
    val levelInfo: UserLevelInfo,
    val habitStats: HabitStats,
    val challengeStats: ChallengeStats,
    val streakStats: StreakStats,
    val timeStats: TimeStats,
    val socialStats: SocialStats
)

/**
 * Статистика по привычкам.
 */
data class HabitStats(
    val totalHabits: Int,
    val activeHabits: Int,
    val completedToday: Int,
    val totalCompletions: Int,
    val completionsThisWeek: Int,
    val completionsThisMonth: Int,
    val completionsThisYear: Int,
    val averageCompletionRate: Float,
    val mostCompletedHabit: String?,
    val perfectDays: Int // Дни, когда все привычки были выполнены
)

/**
 * Статистика по челленджам.
 */
data class ChallengeStats(
    val totalChallenges: Int,
    val activeChallenges: Int,
    val completedChallenges: Int,
    val wonChallenges: Int,
    val winRate: Float,
    val createdChallenges: Int
)

/**
 * Статистика по сериям (streaks).
 */
data class StreakStats(
    val currentStreak: Int, // Самая длинная текущая серия среди всех привычек
    val longestStreak: Int, // Самая длинная серия за все время
    val averageStreak: Int,
    val activeStreaks: Int, // Количество привычек с активной серией
    val streaksByHabit: Map<String, Int> // Серия для каждой привычки
)

/**
 * Временная статистика.
 */
data class TimeStats(
    val morningCompletions: Int,
    val afternoonCompletions: Int,
    val eveningCompletions: Int,
    val averageCompletionTime: String?, // Среднее время выполнения
    val mostProductiveHour: Int?,
    val consistencyScore: Float, // Оценка постоянства (0-1)
    val weekendCompletionsLast30: Int // Выполнения в выходные за последние 30 дней
)

/**
 * Социальная статистика.
 */
data class SocialStats(
    val friendsCount: Int,
    val groupHabitsCount: Int,
    val challengesParticipated: Int,
    val challengesWon: Int
)
