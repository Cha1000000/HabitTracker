package com.habittracker.domain.model

import kotlinx.datetime.LocalDate

/**
 * Статус выполнения привычки за конкретный день
 */
enum class DayCompletionStatus {
    COMPLETED,   // Выполнено
    MISSED,      // Пропущено (день прошёл, но не выполнено)
    REST_DAY,    // Выходной день (пользователь отметил как отдых)
    NOT_ACTIVE,  // День не активен для этой привычки (по расписанию)
    FUTURE,      // Будущий день
    PARTIAL      // Частично выполнено (для измеримых привычек)
}

/**
 * Статистика выполнения привычки за один день
 */
data class DayStatistics(
    val date: LocalDate,
    val status: DayCompletionStatus,
    val value: Int = 0,        // Текущее значение (для измеримых привычек)
    val targetValue: Int = 1,  // Целевое значение
    val note: String? = null,  // Заметка пользователя
    val entry: HabitEntry? = null // Полная запись для доступа к photoUrl и другим полям
)

/**
 * Статистика выполнения привычки за один день для конкретного участника
 */
data class ParticipantDayStatistics(
    val userId: String,
    val userName: String,
    val userAvatarUrl: String?,
    val dayStatistics: DayStatistics
)

/**
 * Агрегированная статистика привычки за период
 */
data class HabitStatisticsSummary(
    val totalDays: Int,           // Всего дней в периоде
    val activeDays: Int,          // Дней, когда привычка была активна
    val completedDays: Int,       // Выполненных дней
    val missedDays: Int,          // Пропущенных дней
    val restDays: Int,            // Выходных дней
    val partialDays: Int,         // Частично выполненных дней
    val completionRate: Float,    // Процент выполнения (0f-1f)
    val currentStreak: Int,       // Текущая серия
    val bestStreak: Int,          // Лучшая серия
    val totalValue: Int = 0,      // Суммарное значение (для измеримых)
    val averageValue: Float = 0f  // Среднее значение (для измеримых)
)

/**
 * Полная статистика привычки за период
 */
data class HabitStatisticsData(
    val habit: Habit,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyStatistics: List<DayStatistics>,
    val summary: HabitStatisticsSummary
)

/**
 * Статистика групповой привычки с данными участников
 */
data class GroupHabitStatisticsData(
    val habit: Habit,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currentUserStatistics: HabitStatisticsData,
    val participantsStatistics: Map<String, HabitStatisticsData>, // userId -> statistics
    val participantsInfo: List<User> // Информация об участниках
)

/**
 * Период для выбора статистики
 */
enum class StatisticsPeriod {
    WEEK,    // Последние 7 дней
    MONTH,   // Последние 30 дней
    CUSTOM   // Произвольный период
}
