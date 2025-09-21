package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.repository.ChallengeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject

class GetActiveChallengesUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository
) {
    /**
     * Получает активные челленджи для пользователя
     * @param userId ID пользователя
     * @return Flow со списком активных челленджей пользователя
     */
    operator fun invoke(userId: String): Flow<List<Challenge>> {
        return challengeRepository.getChallengesForUser(userId).map { challenges ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            
            challenges.filter { challenge ->
                // Фильтруем только активные челленджи или те, что должны стать активными
                when (challenge.status) {
                    ChallengeStatus.ACTIVE -> true
                    ChallengeStatus.PENDING -> challenge.startDate <= today
                    else -> false
                }
            }.map { challenge ->
                // Обновляем статус PENDING челленджей, которые должны стать активными
                if (challenge.status == ChallengeStatus.PENDING && challenge.startDate <= today) {
                    challenge.copy(status = ChallengeStatus.ACTIVE)
                } else {
                    challenge
                }
            }
        }
    }
    
    /**
     * Получает все активные челленджи в системе
     * @return Flow со списком всех активных челленджей
     */
    fun getAllActive(): Flow<List<Challenge>> {
        return challengeRepository.getActiveChallenges()
    }
    
    /**
     * Получает количество активных челленджей для пользователя
     * @param userId ID пользователя
     * @return Flow с количеством активных челленджей
     */
    fun getActiveCount(userId: String): Flow<Int> {
        return invoke(userId).map { it.size }
    }
    
    /**
     * Получает челленджи, которые скоро завершатся (в течение 3 дней)
     * @param userId ID пользователя
     * @return Flow со списком челленджей, которые скоро завершатся
     */
    fun getEndingSoon(userId: String): Flow<List<Challenge>> {
        return invoke(userId).map { challenges ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val threeDaysFromNow = today.plus(3, DateTimeUnit.DAY)
            
            challenges.filter { challenge ->
                challenge.endDate <= threeDaysFromNow && challenge.endDate > today
            }
        }
    }
}
