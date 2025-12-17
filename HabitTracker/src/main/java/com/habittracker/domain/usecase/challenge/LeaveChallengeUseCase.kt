package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

class LeaveChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val socialRepository: SocialRepository
) {
    /**
     * Покидает челлендж
     * @param challengeId ID челленджа
     * @param userId ID пользователя, который хочет покинуть челлендж
     * @return Result с обновленным челленджем или ошибкой
     */
    suspend operator fun invoke(
        challengeId: String,
        userId: String
    ): Result<Challenge> {
        return try {
            // Получаем челлендж
            val challenge = challengeRepository.getChallengeById(challengeId)
                ?: return Result.failure(Exception("Челлендж не найден"))
            
            // Проверяем, что пользователь участвует в челлендже
            if (!challenge.participants.contains(userId)) {
                return Result.failure(Exception("Вы не участвуете в этом челлендже"))
            }
            
            // Проверяем, что это не создатель (создатель не может покинуть свой челлендж)
            if (challenge.createdBy == userId) {
                return Result.failure(Exception("Создатель челленджа не может его покинуть. Используйте отмену челленджа."))
            }
            
            // Проверяем статус челленджа
            if (challenge.status == ChallengeStatus.COMPLETED) {
                return Result.failure(Exception("Нельзя покинуть завершенный челлендж"))
            }
            
            if (challenge.status == ChallengeStatus.CANCELLED) {
                return Result.failure(Exception("Челлендж уже отменен"))
            }
            
            // Удаляем участника из списков
            val updatedParticipants = challenge.participants.filter { it != userId }
            val updatedAcceptedParticipants = challenge.acceptedParticipants.filter { it != userId }
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            
            // Не отменяем челлендж автоматически - автор может добавить новых участников
            val updatedChallenge = challenge.copy(
                participants = updatedParticipants,
                acceptedParticipants = updatedAcceptedParticipants,
                updatedAt = today
            )
            
            challengeRepository.leaveChallenge(challengeId, userId)
            
            // Уведомляем создателя
            try {
                val leavingUser = socialRepository.getUserById(userId)
                socialRepository.sendChallengeLeftNotification(
                    challenge.createdBy,
                    leavingUser?.name ?: "Пользователь",
                    challenge.title
                )
            } catch (e: Exception) {
                // Игнорируем ошибки уведомлений
            }
            
            Result.success(updatedChallenge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
