package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.Achievement
import com.habittracker.domain.model.AchievementMetric
import com.habittracker.domain.repository.AchievementRepository
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.notification.NotificationType
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.presentation.ui.components.AchievementEventManager
import com.habittracker.presentation.ui.components.AchievementToastData
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

/**
 * Use case для проверки и автоматической разблокировки достижений.
 * Вызывается после действий пользователя, которые могут привести к получению достижения.
 */
class CheckAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val getUserStatsUseCase: GetUserStatsUseCase,
    private val userRepository: UserRepository,
    private val socialRepository: SocialRepository,
    private val groupHabitNotificationRepository: GroupHabitNotificationRepository,
    private val achievementEventManager: AchievementEventManager,
    private val analyticsManager: AnalyticsManager
) {

    /**
     * Проверяет все достижения пользователя и разблокирует выполненные.
     * @param userId ID пользователя
     * @return Список новых разблокированных достижений
     */
    suspend operator fun invoke(userId: String): List<Achievement> {
        return try {
            val userStats = getUserStatsUseCase.getUserStats(userId) ?: return emptyList()
            val allAchievements = achievementRepository.getAllAchievements().firstOrNull() ?: emptyList()
            val unlockedAchievements = achievementRepository.getUnlockedAchievements(userId).firstOrNull() ?: emptyList()
            val unlockedAchievementIds = unlockedAchievements.map { it.achievementId }.toSet()

            val newUnlockedAchievements = mutableListOf<Achievement>()

            allAchievements.forEach { achievement ->
                if (!unlockedAchievementIds.contains(achievement.id)) {
                    val currentValue = getMetricValue(userStats, achievement.condition.metric)

                    // Обновляем прогресс для незавершенных достижений
                    if (currentValue > 0) {
                        achievementRepository.updateAchievementProgress(userId, achievement.id, currentValue)
                    }

                    if (currentValue >= achievement.condition.targetValue) {
                        unlockAchievement(userId, achievement)
                        newUnlockedAchievements.add(achievement)
                    }
                }
            }
            
            newUnlockedAchievements
        } catch (e: Exception) {
            Timber.tag("CheckAchievements").e(e, "Error checking achievements")
            emptyList()
        }
    }

    private suspend fun unlockAchievement(userId: String, achievement: Achievement) {
        // Разблокируем достижение
        achievementRepository.unlockAchievement(userId, achievement.id)
        
        // Track achievement unlocked event
        analyticsManager.logEvent(
            AnalyticsEvents.ACHIEVEMENT_UNLOCKED,
            mapOf(AnalyticsEvents.Params.ACHIEVEMENT_ID to achievement.id)
        )
        
        // Начисляем очки за достижение
        val user = userRepository.getUserById(userId)
        if (user != null) {
            val updatedUser = user.copy(points = user.points + achievement.pointsReward)
            userRepository.updateUser(updatedUser)
            
            // Отправляем уведомление
            sendAchievementNotification(user, achievement)
        }
    }
    
    private suspend fun sendAchievementNotification(user: com.habittracker.domain.model.User, achievement: Achievement) {
        try {
            val title = "🏆 Достижение разблокировано!"
            val message = "Вы получили достижение: ${achievement.icon} ${achievement.title}"
            
            Timber.tag("CheckAchievements").d("Achievement unlocked: ${user.name} - ${achievement.title}")
            
            // 1. Отправляем событие для показа анимированного toast
            try {
                achievementEventManager.emitAchievementUnlocked(
                    AchievementToastData(
                        id = achievement.id,
                        title = achievement.title,
                        description = achievement.description,
                        icon = achievement.icon,
                        pointsReward = achievement.pointsReward,
                        titleResId = achievement.titleResId,
                        descriptionResId = achievement.descriptionResId
                    )
                )
            } catch (e: Exception) {
                Timber.tag("CheckAchievements").w(e, "Failed to emit toast event")
            }
            
            // 2. Сохраняем in-app уведомление
            val notification = GroupHabitNotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                habitId = achievement.id, // Используем ID достижения для навигации
                type = NotificationType.ACHIEVEMENT_UNLOCKED.name,
                participantId = user.id,
                participantName = user.name,
                habitTitle = achievement.title,
                message = message,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                isRead = false,
                titleResId = achievement.titleResId,
                descriptionResId = achievement.descriptionResId
            )
            groupHabitNotificationRepository.saveNotification(notification)

            // 3. Отправляем push-уведомление, если есть FCM токен
            user.fcmToken?.takeIf { it.isNotBlank() }?.let { token ->
                val data = mapOf(
                    "type" to "achievement_unlocked",
                    "achievementId" to achievement.id,
                    "userId" to user.id,
                    "screen" to "profile" // или achievements
                )
                
                socialRepository.sendPushNotification(token, title, message, data)
            }
        } catch (e: Exception) {
            Timber.tag("CheckAchievements").w(e, "Failed to send achievement notification")
        }
    }

    // Дублируем логику получения метрик из GetAchievementsUseCase, или лучше вынести это в общий хелпер?
    // Пока продублируем, так как GetUserStatsUseCase возвращает UserStats, и мы мапим метрики на поля Stats.
    private fun getMetricValue(stats: com.habittracker.domain.model.gamification.UserStats, metric: AchievementMetric): Int {
        return when (metric) {
            AchievementMetric.TOTAL_HABITS_CREATED -> stats.habitStats.totalHabits
            AchievementMetric.TOTAL_HABITS_COMPLETED -> stats.habitStats.totalCompletions
            AchievementMetric.CURRENT_STREAK -> stats.streakStats.currentStreak
            AchievementMetric.LONGEST_STREAK -> stats.streakStats.longestStreak
            AchievementMetric.TOTAL_POINTS -> stats.user.points
            AchievementMetric.FRIENDS_COUNT -> stats.socialStats.friendsCount
            AchievementMetric.CHALLENGES_WON -> stats.challengeStats.wonChallenges
            AchievementMetric.CHALLENGES_PARTICIPATED -> stats.challengeStats.totalChallenges
            AchievementMetric.PERFECT_WEEKS -> stats.habitStats.perfectDays / 7
            AchievementMetric.PERFECT_MONTHS -> stats.habitStats.perfectDays / 30
            AchievementMetric.CONSECUTIVE_DAYS -> stats.streakStats.currentStreak
            AchievementMetric.HABIT_VARIETY -> stats.habitStats.activeHabits
            AchievementMetric.EARLY_BIRD -> stats.timeStats.morningCompletions
            AchievementMetric.NIGHT_OWL -> stats.timeStats.eveningCompletions
            AchievementMetric.WEEKEND_WARRIOR -> stats.timeStats.weekendCompletionsLast30
            AchievementMetric.SOCIAL_BUTTERFLY -> stats.socialStats.friendsCount + stats.socialStats.groupHabitsCount
            AchievementMetric.MENTOR -> stats.socialStats.groupHabitsCount
            AchievementMetric.CONSISTENCY -> stats.timeStats.consistencyScore.toInt()
        }
    }
}
