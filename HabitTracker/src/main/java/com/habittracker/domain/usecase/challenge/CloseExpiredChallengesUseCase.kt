package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.math.min

class CloseExpiredChallengesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val challengeRepository: ChallengeRepository,
    private val socialRepository: SocialRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) {
    suspend operator fun invoke(): Result<Int> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: return Result.success(0)
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

            val challenges = challengeRepository.getChallengesForUser(currentUserId).first()
            val toClose = challenges.filter { c ->
                (c.status == ChallengeStatus.ACTIVE || c.status == ChallengeStatus.PENDING) && c.endDate < today
            }

            var closedCount = 0
            for (challenge in toClose) {
                closeOne(challenge)
                closedCount++
            }

            Result.success(closedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun closeOne(challenge: Challenge) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val participantIds = (challenge.acceptedParticipants + challenge.createdBy).distinct()

        val winner = findWinnerByProgress(challenge, participantIds)
        if (winner == null) {
            val updated = challenge.copy(
                status = ChallengeStatus.EXPIRED,
                winnerId = null,
                updatedAt = today
            )
            challengeRepository.updateChallenge(updated)
            notifyExpired(updated, participantIds)
            return
        }

        val updated = challenge.copy(
            status = ChallengeStatus.COMPLETED,
            winnerId = winner,
            updatedAt = today
        )
        challengeRepository.updateChallenge(updated)
        
        // Award points to the winner
        updateUserPointsUseCase(winner, GamificationEvent.ChallengeWon)
        
        // Check achievements for the winner
        try {
            checkAchievementsUseCase(winner)
        } catch (e: Exception) {
            // Ignore achievement check errors
        }
        
        notifyCompleted(updated, participantIds, winner)
    }

    private suspend fun findWinnerByProgress(challenge: Challenge, participantIds: List<String>): String? {
        if (participantIds.isEmpty()) return null

        val currentUserId = authRepository.getCurrentUserId()
        val totalDays = (challenge.endDate.toEpochDays() - challenge.startDate.toEpochDays()).toInt() + 1

        var bestUserId: String? = null
        var bestRatio = 0f
        var bestValue = 0

        for (userId in participantIds) {
            val completedDays = challengeRepository.getCompletedDaysFromFirestore(
                habitId = challenge.habitId,
                userId = userId,
                startDate = challenge.startDate,
                endDate = challenge.endDate
            )

            val currentStreak = if (userId == currentUserId) {
                challengeRepository.getCurrentStreakForUserInChallenge(
                    habitId = challenge.habitId,
                    userId = userId,
                    startDate = challenge.startDate,
                    endDate = challenge.endDate
                )
            } else {
                challengeRepository.getCurrentStreakFromFirestore(
                    habitId = challenge.habitId,
                    userId = userId,
                    startDate = challenge.startDate,
                    endDate = challenge.endDate
                )
            }

            val (value, target) = when (challenge.rules.type) {
                ChallengeType.COMPLETION_RATE,
                ChallengeType.FIRST_TO_TARGET -> {
                    val completionRate = if (totalDays > 0) (completedDays.toFloat() / totalDays.toFloat()) else 0f
                    val progressPercent = (completionRate * 100f).toInt()
                    progressPercent to challenge.rules.targetValue
                }

                ChallengeType.TOTAL_DAYS -> completedDays to challenge.rules.targetValue
                ChallengeType.STREAK -> currentStreak to challenge.rules.targetValue
            }

            val ratio = if (target > 0) min(value.toFloat() / target.toFloat(), 1f) else 0f
            if (ratio > bestRatio || (ratio == bestRatio && value > bestValue) || (ratio == bestRatio && value == bestValue && (bestUserId == null || userId < bestUserId!!))) {
                bestRatio = ratio
                bestValue = value
                bestUserId = userId
            }
        }

        return if (bestRatio <= 0f) null else bestUserId
    }

    private suspend fun notifyExpired(challenge: Challenge, participantIds: List<String>) {
        try {
            val reason = "Челлендж истёк без победителя"
            participantIds.forEach { userId ->
                socialRepository.sendChallengeCancelledNotification(
                    toUserId = userId,
                    challengeId = challenge.id,
                    challengeTitle = challenge.title,
                    reason = reason
                )
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    private suspend fun notifyCompleted(challenge: Challenge, participantIds: List<String>, winnerId: String) {
        try {
            val winnerName = socialRepository.getUserById(winnerId)?.name ?: "Участник"
            participantIds.forEach { userId ->
                val isWinner = userId == winnerId
                val message = if (isWinner) {
                    "Поздравляем! Вы победили в челлендже \"${challenge.title}\"!"
                } else {
                    "Челлендж \"${challenge.title}\" завершен. $winnerName стал победителем!"
                }

                socialRepository.sendChallengeCompletedNotification(
                    toUserId = userId,
                    challengeId = challenge.id,
                    challengeTitle = challenge.title,
                    message = message,
                    isWinner = isWinner
                )
            }
        } catch (_: Exception) {
            // ignore
        }
    }
}
