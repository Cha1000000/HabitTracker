package com.habittracker.domain.model

import com.google.firebase.firestore.PropertyName
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class Habit(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "🎯", // emoji иконка
    val color: String = "#2196F3", // hex цвет для UI
    val type: HabitType = HabitType.PERSONAL,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val reminderTime: LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val reminderDays: Set<DayOfWeek> = emptySet(),
    val targetValue: Int = 1, // сколько раз в день нужно выполнить
    val unit: String = "раз", // единица измерения (раз, минут, км и т.д.)
    val createdBy: String = "", // userId создателя
    val participants: List<String> = emptyList(), // список userId участников
    val maxParticipants: Int = 10, // максимальное количество участников (лимит 10)
    val inviteCode: String? = null, // код для быстрого присоединения к групповой привычке
    val isPublic: Boolean = false, // можно ли найти привычку по коду
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    val createdAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val updatedAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val updatedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val deletedAtMillis: Long? = null,
    val startDate: LocalDate? = null, // дата начала привычки (null = с момента создания)
    val endDate: LocalDate? = null, // дата окончания привычки (null = бессрочно)
    val followUpReminderEnabled: Boolean = true, // включено ли повторное напоминание
    val followUpReminderDuration: LocalTime = LocalTime(2, 0), // через сколько времени напомнить (по умолчанию 2 часа)
    val timeOfDay: HabitTimeOfDay = HabitTimeOfDay.ANY_TIME, // время выполнения привычки
    val goalType: HabitGoalType = HabitGoalType.SIMPLE, // тип цели: простая или измеримая
    val timeGroupId: String? = null // ID группы привычек с одинаковым названием, но разным временем выполнения
)

enum class HabitGoalType {
    SIMPLE,     // Простое выполнение (да/нет)
    MEASURABLE  // Измеримое выполнение (количество)
}

enum class HabitTimeOfDay {
    MORNING,   // Утро (05:00 - 11:59)
    AFTERNOON, // День (12:00 - 17:59)
    EVENING,   // Вечер (18:00 - End of day)
    ANY_TIME   // Любое время
}

enum class HabitType {
    PERSONAL, // личная привычка
    GROUP     // групповая привычка
}

enum class HabitFrequency {
    DAILY,        // каждый день
    WEEKLY,       // раз в неделю
    TWICE_A_WEEK, // два раза в неделю
    WEEKDAYS,     // только по будням
    WEEKENDS,     // только по выходным
    CUSTOM        // кастомные дни недели
}

// Extension functions для работы с привычками
fun Habit.isGroupHabit(): Boolean = type == HabitType.GROUP

fun Habit.isUserParticipant(userId: String): Boolean = participants.contains(userId)

/**
 * Проверяет, активна ли привычка в указанный день недели
 * с учётом частоты и выбранных дней
 */
fun Habit.isActiveOnDayOfWeek(dayOfWeek: DayOfWeek): Boolean {
    return when (frequency) {
        HabitFrequency.DAILY -> true
        
        HabitFrequency.WEEKDAYS -> dayOfWeek in setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
        
        HabitFrequency.WEEKENDS -> dayOfWeek in setOf(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )
        
        HabitFrequency.WEEKLY,
        HabitFrequency.TWICE_A_WEEK,
        HabitFrequency.CUSTOM -> {
            // Если дни не указаны, считаем привычку активной каждый день (обратная совместимость)
            if (reminderDays.isEmpty()) true
            else dayOfWeek in reminderDays
        }
    }
}

fun Habit.canAddParticipants(): Boolean = 
    isGroupHabit() && participants.size < maxParticipants

// Extension функции для работы с датами привычек
fun Habit.isActiveOnDate(date: LocalDate, habitCreatedDate: LocalDate? = null): Boolean {
    // Если есть дата начала, используем её
    if (startDate != null) {
        // Проверяем, что текущая дата не раньше даты начала
        if (date < startDate) {
            return false
        }
    } else if (habitCreatedDate != null) {
        // Если даты начала нет, используем дату создания
        if (date < habitCreatedDate) {
            return false
        }
    }
    // Если нет ни даты начала, ни даты создания - привычка активна всегда (legacy behavior)
    
    // Проверяем дату окончания
    if (endDate != null && date > endDate) {
        return false
    }
    
    return isActive
}

