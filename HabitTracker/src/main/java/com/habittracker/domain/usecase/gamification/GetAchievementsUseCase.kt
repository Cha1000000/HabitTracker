@file:OptIn(ExperimentalCoroutinesApi::class)

package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.AchievementCategory
import com.habittracker.domain.model.AchievementMetric
import com.habittracker.domain.model.AchievementWithProgress
import com.habittracker.domain.repository.AchievementRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class GetAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val getUserStatsUseCase: GetUserStatsUseCase,
    private val userRepository: UserRepository
) {

    fun execute(userId: String, category: AchievementCategory? = null): Flow<List<AchievementWithProgress>> {
        val achievementsFlow = if (category != null) {
            achievementRepository.getAchievementsByCategory(category.name)
        } else {
            achievementRepository.getAllAchievements()
        }
        val userAchievementsFlow = achievementRepository.getUserAchievements(userId)

        return combine(achievementsFlow, userAchievementsFlow) { achievements, userAchievements ->
            achievements to userAchievements
        }.flatMapLatest { (achievements, userAchievements) ->
            flow {
                val userStats = getUserStatsUseCase.getUserStats(userId)
                if (userStats == null) {
                    emit(emptyList())
                    return@flow
                }
                val userAchievementMap = userAchievements.associateBy { it.achievementId }
                val result = achievements.map { achievement ->
                    val userAchievement = userAchievementMap[achievement.id]
                    val currentValue = getMetricValue(userStats, achievement.condition.metric)
                    val progress = if (achievement.condition.targetValue > 0) {
                        (currentValue.toFloat() / achievement.condition.targetValue).coerceIn(0f, 1f)
                    } else 0f

                    AchievementWithProgress(
                        achievement = achievement,
                        userAchievement = userAchievement,
                        progress = progress,
                        isUnlocked = userAchievement?.isUnlocked ?: false
                    )
                }
                emit(result)
            }
        }
    }

    fun getAchievementsWithProgress(userId: String): Flow<List<AchievementWithProgress>> {
        val allAchievementsFlow = achievementRepository.getAllAchievements()
        val userAchievementsFlow = achievementRepository.getUserAchievements(userId)

        return combine(allAchievementsFlow, userAchievementsFlow) { achievements, userAchievements ->
            achievements to userAchievements
        }.flatMapLatest { (achievements, userAchievements) ->
            flow {
                val userStats = getUserStatsUseCase.getUserStats(userId)
                if (userStats == null) {
                    emit(emptyList())
                    return@flow
                }
                val userAchievementMap = userAchievements.associateBy { it.achievementId }
                val result = achievements.map { achievement ->
                    val userAchievement = userAchievementMap[achievement.id]
                    val currentValue = getMetricValue(userStats, achievement.condition.metric)
                    val progress = if (achievement.condition.targetValue > 0) {
                        (currentValue.toFloat() / achievement.condition.targetValue).coerceIn(0f, 1f)
                    } else 0f

                    AchievementWithProgress(
                        achievement = achievement,
                        userAchievement = userAchievement,
                        progress = progress,
                        isUnlocked = userAchievement?.isUnlocked ?: false
                    )
                }
                emit(result)
            }
        }
    }

    suspend fun getAchievementsByCategory(userId: String, category: AchievementCategory): List<AchievementWithProgress> {
        val achievements = achievementRepository.getAchievementsByCategory(category.name).firstOrNull() ?: emptyList()
        val userAchievements = achievementRepository.getUserAchievements(userId).firstOrNull() ?: emptyList()
        val userStats = getUserStatsUseCase.getUserStats(userId) ?: return emptyList()

        val userAchievementMap = userAchievements.associateBy { it.achievementId }

        return achievements.map { achievement ->
            val userAchievement = userAchievementMap[achievement.id]
            val currentValue = getMetricValue(userStats, achievement.condition.metric)
            val progress = if (achievement.condition.targetValue > 0) {
                (currentValue.toFloat() / achievement.condition.targetValue).coerceIn(0f, 1f)
            } else 0f

            AchievementWithProgress(
                achievement = achievement,
                userAchievement = userAchievement,
                progress = progress,
                isUnlocked = userAchievement?.isUnlocked ?: false
            )
        }
    }

    fun getUnlockedAchievements(userId: String): Flow<List<AchievementWithProgress>> {
        val allAchievementsFlow = achievementRepository.getAllAchievements()
        val unlockedFlow = achievementRepository.getUnlockedAchievements(userId)

        return combine(allAchievementsFlow, unlockedFlow) { allAchievements, unlocked ->
            val achievementsById = allAchievements.associateBy { it.id }
            unlocked.mapNotNull { userAchievement ->
                val achievement = achievementsById[userAchievement.achievementId] ?: return@mapNotNull null
                AchievementWithProgress(
                    achievement = achievement,
                    userAchievement = userAchievement,
                    progress = 1.0f,
                    isUnlocked = true
                )
            }
        }
    }

    fun getLockedAchievementsWithProgress(userId: String): Flow<List<AchievementWithProgress>> {
        val allAchievementsFlow = achievementRepository.getAllAchievements()
        val userAchievementsFlow = achievementRepository.getUserAchievements(userId)

        return combine(allAchievementsFlow, userAchievementsFlow) { allAchievements, userAchievements ->
            allAchievements to userAchievements
        }.flatMapLatest { (allAchievements, userAchievements) ->
            flow {
                val userStats = getUserStatsUseCase.getUserStats(userId)
                if (userStats == null) {
                    emit(emptyList())
                    return@flow
                }
                val userAchievementIds = userAchievements.map { it.achievementId }.toSet()

                val result = allAchievements
                    .filter { !userAchievementIds.contains(it.id) && !it.isSecret }
                    .map { achievement ->
                        val currentValue = getMetricValue(userStats, achievement.condition.metric)
                        val progress = if (achievement.condition.targetValue > 0) {
                            (currentValue.toFloat() / achievement.condition.targetValue).coerceIn(0f, 1f)
                        } else 0f
                        AchievementWithProgress(
                            achievement = achievement,
                            userAchievement = null,
                            progress = progress,
                            isUnlocked = false
                        )
                    }
                emit(result)
            }
        }
    }

    suspend fun checkAndUnlockAchievements(userId: String) {
        val userStats = getUserStatsUseCase.getUserStats(userId) ?: return
        val allAchievements = achievementRepository.getAllAchievements().firstOrNull() ?: emptyList()
        val unlockedAchievements = achievementRepository.getUnlockedAchievements(userId).firstOrNull() ?: emptyList()
        val unlockedAchievementIds = unlockedAchievements.map { it.achievementId }.toSet()

        allAchievements.forEach { achievement ->
            if (!unlockedAchievementIds.contains(achievement.id)) {
                val currentValue = getMetricValue(userStats, achievement.condition.metric)

                if (currentValue >= achievement.condition.targetValue) {
                    val now = Clock.System.now()
                    achievementRepository.unlockAchievement(userId, achievement.id)
                    // Также можно обновить очки пользователя
                    val user = userRepository.getUserById(userId)
                    user?.let {
                        val updatedUser = it.copy(points = it.points + achievement.pointsReward)
                        userRepository.updateUser(updatedUser)
                    }
                }
            }
        }
    }

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
