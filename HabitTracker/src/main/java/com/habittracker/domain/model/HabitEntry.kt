package com.habittracker.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class HabitEntry(
    val habitId: String,
    val userId: String,
    val date: LocalDate,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val value: Int = 1, // сколько раз выполнил (для привычек с targetValue > 1)
    val note: String? = "",
    val createdAt: Long,
    val updatedAt: Long
)

// Модель для UI с дополнительной информацией
data class HabitEntryWithUser(
    val entry: HabitEntry,
    val user: User
)

// Статистика выполнения привычки
data class HabitStats(
    val habitId: String,
    val totalDays: Int,
    val completedDays: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val completionRate: Double, // changed from Float to Double
    val lastCompleted: LocalDate?
)

// Extension functions
fun HabitEntry.isCompletedToday(): Boolean {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return date == today && completed
}

fun HabitEntry.getCompletionText(targetValue: Int = 1): String {
    return if (completed) {
        if (targetValue == 1) "Выполнено"
        else "Выполнено $value из $targetValue"
    } else {
        "Не выполнено"
    }
}

fun HabitEntry.getCompletionProgress(targetValue: Int = 1): Float {
    return if (targetValue == 1) {
        if (completed) 1f else 0f
    } else {
        (value.toFloat() / targetValue).coerceIn(0f, 1f)
    }
}

fun HabitEntry.canEdit(): Boolean {
    val now = kotlinx.datetime.Clock.System.now()
    val entryDateTime = date.atStartOfDayIn(TimeZone.currentSystemDefault())
    val daysSinceEntry = (now - entryDateTime).inWholeDays

    // Можно редактировать записи только за последние 7 дней
    return daysSinceEntry <= 7
}

// Статистические extension functions
fun List<HabitEntry>.calculateStats(habitId: String): HabitStats {
    val entries = this.filter { it.habitId == habitId }.sortedBy { it.date }
    val completedEntries = entries.filter { it.completed }

    val currentStreak = calculateCurrentStreak(entries)
    val longestStreak = calculateLongestStreak(entries)
    val completionRate = if (entries.isNotEmpty()) {
        completedEntries.size.toDouble() / entries.size // changed from Float to Double
    } else 0.0

    return HabitStats(
        habitId = habitId,
        totalDays = entries.size,
        completedDays = completedEntries.size,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        completionRate = completionRate,
        lastCompleted = completedEntries.lastOrNull()?.date
    )
}

private fun calculateCurrentStreak(entries: List<HabitEntry>): Int {
    if (entries.isEmpty()) return 0

    val sortedEntries = entries.sortedByDescending { it.date }
    var streak = 0

    for (entry in sortedEntries) {
        if (entry.completed) {
            streak++
        } else {
            break
        }
    }

    return streak
}

private fun calculateLongestStreak(entries: List<HabitEntry>): Int {
    if (entries.isEmpty()) return 0

    val sortedEntries = entries.sortedBy { it.date }
    var longestStreak = 0
    var currentStreak = 0

    for (entry in sortedEntries) {
        if (entry.completed) {
            currentStreak++
            longestStreak = maxOf(longestStreak, currentStreak)
        } else {
            currentStreak = 0
        }
    }

    return longestStreak
}
