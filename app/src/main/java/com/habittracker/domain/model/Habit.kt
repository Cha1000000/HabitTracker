package com.habittracker.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    val icon: String = "🎯", // emoji иконка
    val color: String = "#2196F3", // hex цвет для UI
    val type: HabitType = HabitType.PERSONAL,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val reminderTime: LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val targetValue: Int = 1, // сколько раз в день нужно выполнить
    val unit: String = "раз", // единица измерения (раз, минут, км и т.д.)
    val createdBy: String, // userId создателя
    val participants: List<String> = emptyList(), // список userId участников
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class HabitType {
    PERSONAL, // личная привычка
    GROUP     // групповая привычка
}

enum class HabitFrequency {
    DAILY,      // каждый день
    WEEKLY,     // раз в неделю
    WEEKDAYS,   // только по будням
    WEEKENDS,   // только по выходным
    CUSTOM      // кастомные дни недели
}

// Extension functions для работы с привычками
fun Habit.isGroupHabit(): Boolean = type == HabitType.GROUP

fun Habit.getParticipantsCount(): Int = if (isGroupHabit()) participants.size else 1

fun Habit.canUserEdit(userId: String): Boolean = createdBy == userId

fun Habit.isUserParticipant(userId: String): Boolean =
    createdBy == userId || participants.contains(userId)

fun Habit.getFrequencyText(): String = when (frequency) {
    HabitFrequency.DAILY -> "Каждый день"
    HabitFrequency.WEEKLY -> "Раз в неделю"
    HabitFrequency.WEEKDAYS -> "По будням"
    HabitFrequency.WEEKENDS -> "По выходным"
    HabitFrequency.CUSTOM -> "Выборочно"
}

fun Habit.getReminderText(): String {
    return if (reminderEnabled && reminderTime != null) {
        "Напоминание в ${reminderTime}"
    } else {
        "Без напоминаний"
    }
}

fun Habit.getTargetText(): String {
    return if (targetValue == 1) {
        "Выполнить"
    } else {
        "$targetValue $unit"
    }
}
