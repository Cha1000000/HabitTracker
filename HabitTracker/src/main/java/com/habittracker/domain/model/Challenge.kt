package com.habittracker.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Entity(tableName = "challenges")
data class Challenge(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val title: String,
    val description: String = "",
    val createdBy: String, // userId создателя
    val participants: List<String>, // список userId участников (приглашённых)
    val acceptedParticipants: List<String> = emptyList(), // список userId участников, принявших приглашение
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rules: ChallengeRules,
    val reward: String = "", // описание награды
    val status: ChallengeStatus = ChallengeStatus.PENDING,
    val winnerId: String? = null, // userId победителя
    val createdAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val updatedAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
)

@Serializable
data class ChallengeRules(
    val type: ChallengeType = ChallengeType.COMPLETION_RATE,
    val targetValue: Int = 100, // целевое значение (проценты, дни и т.д.)
    val allowMissedDays: Int = 0, // сколько дней можно пропустить
    val requireConsecutive: Boolean = false // требуется ли последовательность
)

@Serializable
enum class ChallengeType {
    COMPLETION_RATE,    // по проценту выполнения
    STREAK,            // по длине серии
    TOTAL_DAYS,        // по общему количеству дней
    FIRST_TO_TARGET    // первый достигший цели
}

enum class ChallengeStatus {
    PENDING,    // ожидает начала
    ACTIVE,     // активный
    COMPLETED,  // завершен
    EXPIRED,    // истек (без победителя)
    CANCELLED   // отменен
}

// Модель для UI с дополнительной информацией
data class ChallengeWithDetails(
    val challenge: Challenge,
    val habit: Habit,
    val participantUsers: List<User>,
    val participantStats: Map<String, ChallengeParticipantStats>
)

data class ChallengeParticipantStats(
    val userId: String,
    val completedDays: Int,
    val totalDays: Int,
    val currentStreak: Int,
    val completionRate: Float,
    val position: Int // позиция в рейтинге
)

// Extension functions для работы с челленджами
fun Challenge.isActive(): Boolean = status == ChallengeStatus.ACTIVE

fun Challenge.isPending(): Boolean = status == ChallengeStatus.PENDING

fun Challenge.isCompleted(): Boolean = status == ChallengeStatus.COMPLETED || status == ChallengeStatus.EXPIRED

fun Challenge.getDurationInDays(): Int {
    return (endDate.toEpochDays() - startDate.toEpochDays()).toInt() + 1
}

fun Challenge.getDaysRemaining(): Int {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return if (today <= endDate) {
        (endDate.toEpochDays() - today.toEpochDays()).toInt()
    } else 0
}

fun Challenge.getDaysElapsed(): Int {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when {
        today < startDate -> 0
        today > endDate -> getDurationInDays()
        else -> (today.toEpochDays() - startDate.toEpochDays()).toInt() + 1
    }
}

fun Challenge.getProgressPercentage(): Float {
    val elapsed = getDaysElapsed()
    val total = getDurationInDays()
    return if (total > 0) elapsed.toFloat() / total else 0f
}

fun Challenge.canUserJoin(userId: String): Boolean {
    return status == ChallengeStatus.PENDING && 
           !participants.contains(userId) && 
           createdBy != userId
}

fun Challenge.canUserLeave(userId: String): Boolean {
    return status == ChallengeStatus.PENDING && 
           participants.contains(userId)
}

fun Challenge.canUserEdit(userId: String): Boolean = createdBy == userId

fun Challenge.isUserParticipant(userId: String): Boolean =
    createdBy == userId || participants.contains(userId)

fun Challenge.getStatusText(): String = when (status) {
    ChallengeStatus.PENDING -> "Ожидает начала"
    ChallengeStatus.ACTIVE -> "Активный"
    ChallengeStatus.COMPLETED -> "Завершен"
    ChallengeStatus.EXPIRED -> "Истек"
    ChallengeStatus.CANCELLED -> "Отменен"
}

fun Challenge.getRulesText(): String = when (rules.type) {
    ChallengeType.COMPLETION_RATE -> "Достичь ${rules.targetValue}% выполнения"
    ChallengeType.STREAK -> "Серия из ${rules.targetValue} дней"
    ChallengeType.TOTAL_DAYS -> "Выполнить ${rules.targetValue} дней"
    ChallengeType.FIRST_TO_TARGET -> "Первый достигший цели"
}

fun Challenge.shouldStartToday(): Boolean {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return status == ChallengeStatus.PENDING && startDate == today
}

fun Challenge.shouldEndToday(): Boolean {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return status == ChallengeStatus.ACTIVE && endDate == today
}

fun Challenge.getTimeUntilStart(): String {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysUntil = (startDate.toEpochDays() - today.toEpochDays()).toInt()
    
    return when {
        daysUntil < 0 -> "Уже начался"
        daysUntil == 0 -> "Начинается сегодня"
        daysUntil == 1 -> "Начинается завтра"
        daysUntil < 7 -> "Начинается через $daysUntil дней"
        else -> "Начинается ${startDate}"
    }
}
