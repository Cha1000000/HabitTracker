package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.isCompleted
import com.habittracker.R
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.SocialRepository
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

class JoinChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val socialRepository: SocialRepository,
    private val stringProvider: StringProvider
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
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_not_found)))
            
            // Проверяем, что пользователь еще не участвует
            if (challenge.participants.contains(userId)) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_already_joined_challenge)))
            }
            
            // Проверяем статус челленджа
            if (challenge.isCompleted()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_completed)))
            }
            
            if (challenge.status == ChallengeStatus.CANCELLED) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_cancelled)))
            }
            
            // Проверяем лимит участников
            if (challenge.participants.size >= 10) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_max_participants)))
            }
            
            // Проверяем дружбу с создателем
            val areFriends = socialRepository.areFriends(userId, challenge.createdBy)
            if (!areFriends) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_must_be_friend_creator)))
            }
            
            // Добавляем участника
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val updatedChallenge = challenge.copy(
                participants = challenge.participants + userId,
                updatedAt = today
            )
            
            challengeRepository.updateChallenge(updatedChallenge)
            
            // Уведомляем создателя о новом участнике
            try {
                val joiningUser = socialRepository.getUserById(userId)
                socialRepository.sendChallengeJoinedNotification(
                    challenge.createdBy,
                    joiningUser?.name ?: stringProvider.getString(R.string.default_user_name),
                    challenge.id,
                    challenge.title
                )
            } catch (_: Exception) {
                // Игнорируем ошибки уведомлений
            }
            
            Result.success(updatedChallenge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
