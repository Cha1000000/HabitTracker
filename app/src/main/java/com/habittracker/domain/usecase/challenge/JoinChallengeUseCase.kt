package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.datetime.Clock
import javax.inject.Inject

class JoinChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val socialRepository: SocialRepository
) {
    /**
     * Присоединяется к существующему челленджу
     * @param challengeId ID челленджа
     * @param userId ID пользователя, который хочет присоединиться
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
            
            // Проверяем, что пользователь еще не участвует
            if (challenge.participants.contains(userId)) {
                return Result.failure(Exception("Вы уже участвуете в этом челлендже"))
            }
            
            // Проверяем статус челленджа
            if (challenge.status == ChallengeStatus.COMPLETED) {
                return Result.failure(Exception("Челлендж уже завершен"))
            }
            
            if (challenge.status == ChallengeStatus.CANCELLED) {
                return Result.failure(Exception("Челлендж отменен"))
            }
            
            // Проверяем лимит участников
            if (challenge.participants.size >= 10) {
                return Result.failure(Exception("Достигнуто максимальное количество участников"))
            }
            
            // Проверяем дружбу с создателем
            val areFriends = socialRepository.areFriends(userId, challenge.createdBy)
            if (!areFriends) {
                return Result.failure(Exception("Вы должны быть другом создателя челленджа"))
            }
            
            // Добавляем участника
            val updatedChallenge = challenge.copy(
                participants = challenge.participants + userId,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )
            
            challengeRepository.updateChallenge(updatedChallenge)
            
            // Уведомляем создателя о новом участнике
            try {
                val joiningUser = socialRepository.getUserById(userId)
                socialRepository.sendChallengeJoinedNotification(
                    challenge.createdBy,
                    joiningUser?.name ?: "Пользователь",
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
