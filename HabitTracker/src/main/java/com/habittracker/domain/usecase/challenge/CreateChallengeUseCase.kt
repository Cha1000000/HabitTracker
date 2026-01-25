package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.R
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

class CreateChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository,
    private val stringProvider: StringProvider
) {
    /**
     * Создает новый челлендж между друзьями
     * @param habitId ID привычки для челленджа
     * @param title Название челленджа
     * @param description Описание челленджа
     * @param createdBy ID создателя челленджа
     * @param participantIds Список ID участников (должны быть друзьями создателя)
     * @param startDate Дата начала челленджа
     * @param endDate Дата окончания челленджа
     * @param rules Правила челленджа
     * @param reward Описание награды
     * @return Result с созданным челленджем или ошибкой
     */
    suspend operator fun invoke(
        habitId: String,
        title: String,
        description: String = "",
        createdBy: String,
        participantIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate,
        rules: ChallengeRules,
        reward: String = ""
    ): Result<Challenge> {
        return try {
            // Валидация входных данных
            if (title.isBlank()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_title_empty)))
            }
            
            if (participantIds.isEmpty()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_min_participants)))
            }
            
            if (participantIds.size > 10) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_challenge_max_participants_limit)))
            }
            
            if (startDate >= endDate) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_end_date_before_start)))
            }
            
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            if (endDate <= today) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_end_date_past)))
            }
            
            // Проверяем, что привычка существует
            val habit = habitRepository.getHabitById(habitId)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_habit_not_found_domain)))
            
            // Проверяем, что создатель имеет доступ к привычке
            if (habit.type.name == "PERSONAL" && habit.createdBy != createdBy) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_no_habit_access)))
            }
            
            if (habit.type.name == "GROUP" && !habit.participants.contains(createdBy)) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_not_habit_participant)))
            }
            
            // Проверяем дружбу с участниками
            val allParticipants = participantIds + createdBy
            for (participantId in participantIds) {
                if (participantId == createdBy) continue
                
                val areFriends = socialRepository.areFriends(createdBy, participantId)
                if (!areFriends) {
                    val user = socialRepository.getUserById(participantId)
                    return Result.failure(Exception(stringProvider.getString(R.string.error_not_friend_template, user?.name ?: stringProvider.getString(R.string.default_user_name))))
                }
            }
            
            // Создаем челлендж
            val challenge = Challenge(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                title = title,
                description = description,
                createdBy = createdBy,
                participants = allParticipants,
                startDate = startDate,
                endDate = endDate,
                rules = rules,
                reward = reward,
                status = if (startDate <= today) ChallengeStatus.ACTIVE else ChallengeStatus.PENDING,
                winnerId = null,
                createdAt = today,
                updatedAt = today
            )
            
            // Сохраняем в репозиторий
            challengeRepository.insertChallenge(challenge)
            
            // Отправляем уведомления участникам
            try {
                val creatorUser = socialRepository.getUserById(createdBy)
                participantIds.forEach { participantId ->
                    if (participantId != createdBy) {
                        Timber.tag("CreateChallengeUseCase").d("Sending challenge invitation to $participantId")
                        socialRepository.sendChallengeInvitationNotification(
                            participantId,
                            creatorUser?.name ?: stringProvider.getString(R.string.default_user_name),
                            challenge.id,
                            title,
                            habit.title
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag("CreateChallengeUseCase").e(e, "Error sending challenge notifications")
            }
            
            Result.success(challenge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
