package com.habittracker.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Уровень сложности привычки для геймификации
 */
enum class HabitDifficulty(val points: Int, val multiplier: Double) {
    EASY(10, 1.0),      // Легкая привычка: 10 базовых очков
    MEDIUM(20, 1.5),    // Средняя привычка: 20 базовых очков + 50% бонус
    HARD(30, 2.0)       // Сложная привычка: 30 базовых очков + 100% бонус
}

/**
 * Категории привычек для группировки и аналитики
 */
enum class HabitCategory(val emoji: String, val colorHex: String) {
    HEALTH("🏥", "#4CAF50"),        // Здоровье - зеленый
    SPORT("🏃", "#FF9800"),         // Спорт - оранжевый  
    LEARNING("📚", "#2196F3"),      // Обучение - синий
    WORK("💼", "#9C27B0"),          // Работа - фиолетовый
    SOCIAL("👥", "#E91E63"),        // Социальное - розовый
    CREATIVITY("🎨", "#FF5722"),    // Творчество - красно-оранжевый
    MINDFULNESS("🧘", "#00BCD4"),   // Осознанность - голубой
    FINANCE("💰", "#FFC107"),       // Финансы - желтый
    HOME("🏠", "#795548"),          // Дом - коричневый
    OTHER("⭐", "#607D8B")          // Другое - серый
}

/**
 * Роли участников в групповой привычке
 */
enum class ParticipantRole {
    CREATOR,    // Создатель привычки (полные права)
    ADMIN,      // Администратор (может управлять участниками)
    MEMBER      // Обычный участник
}

/**
 * Участник групповой привычки с дополнительной информацией
 */
data class HabitParticipant(
    val userId: String,
    val role: ParticipantRole,
    val joinedAt: Instant,
    val invitedBy: String? = null, // кто пригласил
    val isActive: Boolean = true    // активен ли участник
)

/**
 * Уровни приватности для привычек
 */
enum class PrivacyLevel {
    PUBLIC,             // Видна всем, можно найти по поиску
    FRIENDS_ONLY,       // Видна только друзьям
    PARTICIPANTS_ONLY,  // Видна только участникам
    PRIVATE             // Видна только создателю
}

/**
 * Настройки уведомлений для групповой привычки
 */
data class GroupNotificationSettings(
    val notifyOnCompletion: Boolean = true,        // уведомлять о выполнении участниками
    val notifyOnMilestones: Boolean = true,        // уведомлять о достижениях
    val notifyOnNewParticipants: Boolean = true,   // уведомлять о новых участниках
    val notifyOnReminders: Boolean = true,         // отправлять напоминания группе
    val quietHoursStart: kotlinx.datetime.LocalTime? = null,  // начало тихих часов
    val quietHoursEnd: kotlinx.datetime.LocalTime? = null     // конец тихих часов
)

/**
 * Расширенная статистика привычки для аналитики
 */
data class EnhancedHabitStats(
    val totalCompletions: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val completionRate: Double = 0.0,           // процент выполнения за все время
    val weeklyCompletionRate: Double = 0.0,     // процент выполнения за неделю
    val monthlyCompletionRate: Double = 0.0,    // процент выполнения за месяц
    val totalPoints: Int = 0,                   // общие заработанные очки
    val averageCompletionTime: kotlinx.datetime.LocalTime? = null, // среднее время выполнения
    val lastCompletedAt: Instant? = null,
    val createdDaysAgo: Int = 0
)

/**
 * Статистика группы для групповых привычек
 */
data class GroupHabitStats(
    val totalParticipants: Int,
    val activeParticipants: Int,
    val groupCompletionRate: Double,            // средний процент выполнения группы
    val topPerformer: String? = null,           // лучший участник по статистике
    val groupStreak: Int = 0,                   // дни подряд, когда хотя бы кто-то выполнял
    val perfectDays: Int = 0,                   // дни, когда все участники выполнили
    val totalGroupPoints: Int = 0,              // общие очки группы
    val averageIndividualStreak: Double = 0.0   // средняя серия участников
)

/**
 * Расширенная модель привычки с дополнительными полями
 */
data class EnhancedHabit(
    // Базовые поля из оригинальной модели Habit
    val id: String,
    val title: String,
    val description: String = "",
    val icon: String = "🎯",
    val color: String = "#2196F3",
    val type: HabitType = HabitType.PERSONAL,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val reminderTime: kotlinx.datetime.LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val targetValue: Int = 1,
    val unit: String = "раз",
    val createdBy: String,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    
    // Новые поля для геймификации
    val difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
    val category: HabitCategory = HabitCategory.OTHER,
    val tags: List<String> = emptyList(),
    val basePoints: Int = difficulty.points,
    
    // Улучшенные социальные функции
    val participants: List<HabitParticipant> = emptyList(),
    val maxParticipants: Int = 10,
    val inviteCode: String? = null,
    val privacyLevel: PrivacyLevel = PrivacyLevel.PRIVATE,
    val groupNotificationSettings: GroupNotificationSettings? = null,
    
    // Статистика (кэшированная для производительности)
    val stats: EnhancedHabitStats = EnhancedHabitStats(),
    val groupStats: GroupHabitStats? = null
) {
    // Совместимость с оригинальной моделью Habit
    fun toHabit(): Habit = Habit(
        id = id,
        title = title,
        description = description,
        icon = icon,
        color = color,
        type = type,
        frequency = frequency,
        reminderTime = reminderTime,
        reminderEnabled = reminderEnabled,
        targetValue = targetValue,
        unit = unit,
        createdBy = createdBy,
        participants = participants.map { it.userId },
        maxParticipants = maxParticipants,
        inviteCode = inviteCode,
        isPublic = privacyLevel == PrivacyLevel.PUBLIC,
        isActive = isActive,
        createdAt = createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = updatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
}

/**
 * Extension функции для работы с расширенной моделью
 */
fun EnhancedHabit.isGroupHabit(): Boolean = type == HabitType.GROUP

fun EnhancedHabit.getActiveParticipantsCount(): Int = 
    if (isGroupHabit()) participants.count { it.isActive } else 1

fun EnhancedHabit.canUserEdit(userId: String): Boolean = 
    createdBy == userId || participants.any { it.userId == userId && it.role == ParticipantRole.ADMIN }

fun EnhancedHabit.isUserParticipant(userId: String): Boolean =
    createdBy == userId || participants.any { it.userId == userId && it.isActive }

fun EnhancedHabit.getUserRole(userId: String): ParticipantRole? = when {
    createdBy == userId -> ParticipantRole.CREATOR
    else -> participants.find { it.userId == userId }?.role
}

fun EnhancedHabit.canAddParticipants(): Boolean = 
    isGroupHabit() && getActiveParticipantsCount() < maxParticipants

fun EnhancedHabit.calculatePoints(streakMultiplier: Double = 1.0): Int {
    val base = basePoints
    val difficultyBonus = (base * (difficulty.multiplier - 1.0)).toInt()
    val streakBonus = (base * (streakMultiplier - 1.0)).toInt()
    return base + difficultyBonus + streakBonus
}

fun EnhancedHabit.getCategoryColor(): String = category.colorHex

fun EnhancedHabit.getCategoryEmoji(): String = category.emoji

/**
 * Фабричные методы для создания привычек
 */
object HabitFactory {
    fun createPersonalHabit(
        title: String,
        createdBy: String,
        difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
        category: HabitCategory = HabitCategory.OTHER
    ): EnhancedHabit = EnhancedHabit(
        id = generateHabitId(),
        title = title,
        createdBy = createdBy,
        type = HabitType.PERSONAL,
        difficulty = difficulty,
        category = category,
        basePoints = difficulty.points,
        createdAt = kotlinx.datetime.Clock.System.now(),
        updatedAt = kotlinx.datetime.Clock.System.now()
    )
    
    fun createGroupHabit(
        title: String,
        createdBy: String,
        maxParticipants: Int = 10,
        difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
        category: HabitCategory = HabitCategory.OTHER,
        privacyLevel: PrivacyLevel = PrivacyLevel.FRIENDS_ONLY
    ): EnhancedHabit = EnhancedHabit(
        id = generateHabitId(),
        title = title,
        createdBy = createdBy,
        type = HabitType.GROUP,
        maxParticipants = maxParticipants,
        difficulty = difficulty,
        category = category,
        basePoints = difficulty.points,
        privacyLevel = privacyLevel,
        groupNotificationSettings = GroupNotificationSettings(),
        inviteCode = HabitValidation.generateInviteCode(),
        createdAt = kotlinx.datetime.Clock.System.now(),
        updatedAt = kotlinx.datetime.Clock.System.now()
    )
    
    private fun generateHabitId(): String = java.util.UUID.randomUUID().toString()
}
