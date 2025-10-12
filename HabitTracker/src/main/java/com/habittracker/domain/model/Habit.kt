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
    val startDate: LocalDate? = null, // дата начала привычки (null = с момента создания)
    val endDate: LocalDate? = null // дата окончания привычки (null = бессрочно)
)

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

fun Habit.getParticipantsCount(): Int = if (isGroupHabit()) participants.size else 1

fun Habit.canUserEdit(userId: String): Boolean = createdBy == userId

fun Habit.isUserParticipant(userId: String): Boolean =
    createdBy == userId || participants.contains(userId)

fun Habit.getFrequencyText(): String = when (frequency) {
    HabitFrequency.DAILY -> "Каждый день"
    HabitFrequency.WEEKLY -> "Раз в неделю"
    HabitFrequency.TWICE_A_WEEK -> "Два раза в неделю"
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

fun Habit.canAddParticipants(): Boolean = 
    isGroupHabit() && participants.size < maxParticipants

fun Habit.hasInviteCode(): Boolean = !inviteCode.isNullOrBlank()

fun Habit.canUserInvite(userId: String): Boolean = 
    isGroupHabit() && createdBy == userId && canAddParticipants()

fun Habit.canUserRemoveParticipant(userId: String, targetUserId: String): Boolean =
    isGroupHabit() && createdBy == userId && targetUserId != createdBy

fun Habit.getRemainingSlots(): Int = 
    if (isGroupHabit()) maxParticipants - participants.size else 0

fun Habit.getTypeText(): String = when (type) {
    HabitType.PERSONAL -> "Личная"
    HabitType.GROUP -> "Групповая"
}

fun Habit.getParticipantsText(): String = when {
    !isGroupHabit() -> "Только вы"
    participants.isEmpty() -> "Только создатель"
    else -> "${participants.size + 1} из $maxParticipants участников"
}

// Extension функции для работы с датами привычек
fun Habit.isActiveOnDate(date: LocalDate, habitCreatedDate: LocalDate? = null): Boolean {
    // Если есть дата начала, используем её
    if (startDate != null) {
        // Проверяем, что текущая дата не раньше даты начала
        if (date < startDate) {
            android.util.Log.d("Habit", "❌ Habit '$title' not active: date=$date < startDate=$startDate")
            return false
        }
    } else if (habitCreatedDate != null) {
        // Если даты начала нет, используем дату создания
        if (date < habitCreatedDate) {
            android.util.Log.d("Habit", "❌ Habit '$title' not active: date=$date < createdDate=$habitCreatedDate")
            return false
        }
    }
    // Если нет ни даты начала, ни даты создания - привычка активна всегда (legacy behavior)
    
    // Проверяем дату окончания
    if (endDate != null && date > endDate) {
        android.util.Log.d("Habit", "❌ Habit '$title' not active: date=$date > endDate=$endDate")
        return false
    }
    
    return isActive
}

fun Habit.hasStarted(today: LocalDate): Boolean {
    val effectiveStartDate = startDate ?: return true
    return today >= effectiveStartDate
}

fun Habit.hasEnded(today: LocalDate): Boolean {
    val effectiveEndDate = endDate ?: return false
    return today > effectiveEndDate
}

fun Habit.isEditableDateRange(hasBeenCompleted: Boolean): Boolean {
    // Дату начала можно редактировать только если привычка ещё не была выполнена
    return !hasBeenCompleted
}
