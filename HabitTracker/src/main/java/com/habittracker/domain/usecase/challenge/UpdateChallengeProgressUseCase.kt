package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.isCompleted
import com.habittracker.R
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import javax.inject.Inject

class UpdateChallengeProgressUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val socialRepository: SocialRepository,
    private val getChallengeResultsUseCase: GetChallengeResultsUseCase,
    private val stringProvider: StringProvider
) {
    /**
     * Обновляет прогресс челленджа после выполнения/невыполнения привычки
     * Автоматически вызывается при изменении HabitEntry
     * @param habitId ID привычки
     * @param userId ID пользователя
     * @return Result с количеством обновленных челленджей
     */
    suspend operator fun invoke(habitId: String, userId: String): Result<Int> {
        return try {
            // Получаем все активные челленджи для данной привычки
            val challenges = challengeRepository.getChallengesForHabit(habitId)
                .filter { challenge ->
                    challenge.status == ChallengeStatus.ACTIVE &&
                    challenge.participants.contains(userId)
                }
            
            var updatedCount = 0
            
            for (challenge in challenges) {
                // Проверяем, не завершился ли челлендж по времени
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                if (challenge.endDate <= today && challenge.status == ChallengeStatus.ACTIVE) {
                    // Завершаем челлендж и определяем победителя
                    finishChallenge(challenge)
                    updatedCount++
                    continue
                }
                
                // Проверяем условия досрочного завершения для FIRST_TO_TARGET
                if (challenge.rules.type == ChallengeType.FIRST_TO_TARGET) {
                    val hasWinner = checkForEarlyWinner(challenge)
                    if (hasWinner) {
                        updatedCount++
                    }
                }
            }
            
            Result.success(updatedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Завершает челлендж и определяет победителя
     */
    private suspend fun finishChallenge(challenge: Challenge) {
        try {
            // Получаем результаты челленджа
            val resultsResult = getChallengeResultsUseCase(challenge.id)
            if (resultsResult.isFailure) return
            
            val results = resultsResult.getOrNull() ?: return
            val hasProgress = results.participants.any { it.score > 0 }
            val winner = if (hasProgress) results.winner else null
            
            // Обновляем статус челленджа
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val updatedChallenge = challenge.copy(
                status = if (hasProgress) ChallengeStatus.COMPLETED else ChallengeStatus.EXPIRED,
                winnerId = if (hasProgress) winner?.user?.id else null,
                updatedAt = today
            )
            
            challengeRepository.updateChallenge(updatedChallenge)
            
            // Отправляем уведомления участникам
            if (hasProgress) {
                sendChallengeCompletedNotifications(updatedChallenge, results.participants)
            } else {
                try {
                    challenge.participants.forEach { participantId ->
                        socialRepository.sendChallengeCancelledNotification(
                            participantId,
                            challenge.id,
                            challenge.title,
                            stringProvider.getString(R.string.challenge_expired_no_winner)
                        )
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
            
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем выполнение
        }
    }
    
    /**
     * Проверяет условия досрочного завершения для челленджа типа FIRST_TO_TARGET
     * Для измеряемых привычек проверяет суммарное значение (totalValue),
     * для простых — количество дней выполнения
     */
    private suspend fun checkForEarlyWinner(challenge: Challenge): Boolean {
        return try {
            val resultsResult = getChallengeResultsUseCase(challenge.id)
            if (resultsResult.isFailure) return false
            
            val results = resultsResult.getOrNull() ?: return false
            
            // Проверяем, достиг ли кто-то целевого значения
            // score уже вычислен с учётом goalType привычки в GetChallengeResultsUseCase
            val targetAchieved = results.participants.any { participant ->
                when (challenge.rules.type) {
                    ChallengeType.FIRST_TO_TARGET -> {
                        participant.score >= challenge.rules.targetValue
                    }
                    else -> false
                }
            }
            
            if (targetAchieved) {
                finishChallenge(challenge)
                return true
            }
            
            false
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Отправляет уведомления о завершении челленджа
     */
    private suspend fun sendChallengeCompletedNotifications(
        challenge: Challenge,
        participants: List<ChallengeParticipantResult>
    ) {
        try {
            val winner = participants.firstOrNull { it.isWinner }
            
            participants.forEach { participant ->
                val isWinner = participant.isWinner
                val message = if (isWinner) {
                    stringProvider.getString(R.string.challenge_won_message, challenge.title)
                } else {
                    stringProvider.getString(R.string.challenge_completed_winner_message, challenge.title, winner?.user?.name ?: stringProvider.getString(R.string.default_participant_name))
                }
                
                socialRepository.sendChallengeCompletedNotification(
                    participant.user.id,
                    challenge.id,
                    challenge.title,
                    message,
                    isWinner
                )
            }
        } catch (_: Exception) {
            // Игнорируем ошибки уведомлений
        }
    }
    
    /**
     * Принудительно завершает челлендж (для использования администратором)
     * @param challengeId ID челленджа
     * @param winnerId ID победителя (опционально)
     * @return Result с обновленным челленджем
     */
    suspend fun forceFinishChallenge(challengeId: String, winnerId: String? = null): Result<Challenge> {
        return try {
            val challenge = challengeRepository.getChallengeById(challengeId)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_not_found)))
            
            if (challenge.isCompleted()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_completed)))
            }
            
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val updatedChallenge = challenge.copy(
                status = ChallengeStatus.COMPLETED,
                winnerId = winnerId,
                updatedAt = today
            )
            
            challengeRepository.updateChallenge(updatedChallenge)
            
            // Получаем результаты для уведомлений
            val resultsResult = getChallengeResultsUseCase(challengeId)
            if (resultsResult.isSuccess) {
                val results = resultsResult.getOrNull()
                if (results != null) {
                    sendChallengeCompletedNotifications(updatedChallenge, results.participants)
                }
            }
            
            Result.success(updatedChallenge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Отменяет челлендж
     * @param challengeId ID челленджа
     * @param reason Причина отмены
     * @return Result с обновленным челленджем
     */
    suspend fun cancelChallenge(challengeId: String, reason: String = ""): Result<Challenge> {
        return try {
            val challenge = challengeRepository.getChallengeById(challengeId)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_not_found)))
            
            if (challenge.isCompleted()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_cannot_cancel_completed)))
            }
            
            if (challenge.status == ChallengeStatus.CANCELLED) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_already_cancelled)))
            }
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val updatedChallenge = challenge.copy(
                status = ChallengeStatus.CANCELLED,
                updatedAt = today
            )
            
            challengeRepository.updateChallenge(updatedChallenge)
            
            // Уведомляем всех участников об отмене
            try {
                challenge.participants.forEach { participantId ->
                    socialRepository.sendChallengeCancelledNotification(
                        participantId,
                        challenge.id,
                        challenge.title,
                        reason.ifBlank { stringProvider.getString(R.string.challenge_cancelled_by_creator) }
                    )
                }
            } catch (_: Exception) {
                // Игнорируем ошибки уведомлений
            }
            
            Result.success(updatedChallenge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
