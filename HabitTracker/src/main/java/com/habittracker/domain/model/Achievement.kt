package com.habittracker.domain.model

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Модель достижения
 */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "🏆", // emoji иконка
    val category: AchievementCategory,
    val type: AchievementType,
    val condition: AchievementCondition,
    val pointsReward: Int, // очки за получение достижения
    val isSecret: Boolean = false, // скрытое достижение
    val isActive: Boolean = true,
    val createdAt: Instant,
    @StringRes val titleResId: Int? = null,
    @StringRes val descriptionResId: Int? = null
)

/**
 * Категории достижений
 */
enum class AchievementCategory {
    HABITS,        // связанные с привычками
    STREAKS,       // связанные с сериями
    SOCIAL,        // социальные достижения
    CHALLENGES,    // связанные с челленджами
    MILESTONES,    // ранги уровня
    SPECIAL        // особые достижения
}

/**
 * Типы достижений
 */
enum class AchievementType {
    SINGLE,        // одноразовое достижение
    PROGRESSIVE,   // прогрессивное (можно получить несколько раз)
    TIERED         // многоуровневое (бронза, серебро, золото)
}

/**
 * Условие для получения достижения
 */
data class AchievementCondition(
    val metric: AchievementMetric,
    val targetValue: Int,
    val timeframe: AchievementTimeframe = AchievementTimeframe.ALL_TIME,
    val additionalParams: Map<String, String> = emptyMap()
)

/**
 * Метрики для достижений
 */
enum class AchievementMetric {
    TOTAL_HABITS_CREATED,      // общее количество созданных привычек
    TOTAL_HABITS_COMPLETED,    // общее количество выполненных привычек
    CURRENT_STREAK,            // текущая серия
    LONGEST_STREAK,            // самая длинная серия
    TOTAL_POINTS,              // общие очки
    FRIENDS_COUNT,             // количество друзей
    CHALLENGES_WON,            // выигранные челленджи
    CHALLENGES_PARTICIPATED,   // участие в челленджах
    PERFECT_WEEKS,             // идеальные недели (все привычки выполнены)
    PERFECT_MONTHS,            // идеальные месяцы
    CONSECUTIVE_DAYS,          // дни подряд с активностью
    HABIT_VARIETY,             // разнообразие привычек (разные категории)
    EARLY_BIRD,                // выполнение до определенного времени
    NIGHT_OWL,                 // выполнение после определенного времени
    WEEKEND_WARRIOR,           // активность в выходные
    SOCIAL_BUTTERFLY,          // социальная активность
    MENTOR,                    // помощь другим пользователям
    CONSISTENCY               // постоянство в выполнении
}

/**
 * Временные рамки для достижений
 */
enum class AchievementTimeframe {
    ALL_TIME,      // за все время
    DAILY,         // за день
    WEEKLY,        // за неделю
    MONTHLY,       // за месяц
    YEARLY         // за год
}

/**
 * Пользовательское достижение (связь пользователя с достижением)
 */
@Entity(
    tableName = "user_achievements",
    primaryKeys = ["userId", "achievementId"]
)
data class UserAchievement(
    val userId: String,
    val achievementId: String,
    val progress: Int = 0, // текущий прогресс для прогрессивных достижений
    val isUnlocked: Boolean = false, // разблокировано ли достижение
    val unlockedAt: Instant? = null, // когда было разблокировано
    val createdAt: Instant, // когда было создано (начало отслеживания)
    val updatedAt: Instant = createdAt,
    val tier: Int = 1, // для многоуровневых достижений (1 = бронза, 2 = серебро, 3 = золото)
    val isNotified: Boolean = false // показано ли уведомление пользователю
)

/**
 * Модель для UI с дополнительной информацией
 */
data class AchievementWithProgress(
    val achievement: Achievement,
    val userAchievement: UserAchievement?,
    val progress: Float, // прогресс от 0.0 до 1.0
    val isUnlocked: Boolean,
    val nextTierRequirement: Int? = null // для многоуровневых достижений
)

/**
 * Статистика достижений пользователя
 */
data class UserAchievementStats(
    val totalAchievements: Int,
    val unlockedAchievements: Int,
    val totalPointsFromAchievements: Int,
    val achievementsByCategory: Map<AchievementCategory, Int>,
    val recentAchievements: List<AchievementWithProgress>,
    val nextAchievements: List<AchievementWithProgress> // ближайшие к получению
)

// Extension functions для работы с достижениями
fun Achievement.getProgressDescription(currentValue: Int): String {
    val progress = (currentValue.toFloat() / condition.targetValue * 100).toInt()
    return "$currentValue / ${condition.targetValue} ($progress%)"
}

fun Achievement.isCompleted(currentValue: Int): Boolean = 
    currentValue >= condition.targetValue

fun UserAchievement.getProgressPercentage(targetValue: Int): Float =
    (progress.toFloat() / targetValue).coerceIn(0f, 1f)

fun AchievementWithProgress.getDisplayTitle(): String = when {
    achievement.type == AchievementType.TIERED && userAchievement != null -> {
        val tierName = when (userAchievement.tier) {
            1 -> "Бронза"
            2 -> "Серебро" 
            3 -> "Золото"
            else -> "Уровень ${userAchievement.tier}"
        }
        "${achievement.title} ($tierName)"
    }
    else -> achievement.title
}
