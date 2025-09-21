package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.UUID
import javax.inject.Inject

class CreateChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository
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
                return Result.failure(Exception("Название челленджа не может быть пустым"))
            }
            
            if (participantIds.isEmpty()) {
                return Result.failure(Exception("Челлендж должен иметь хотя бы одного участника"))
            }
            
            if (participantIds.size > 10) {
                return Result.failure(Exception("Максимальное количество участников: 10"))
            }
            
            if (startDate >= endDate) {
                return Result.failure(Exception("Дата окончания должна быть позже даты начала"))
            }
            
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            if (endDate <= today) {
                return Result.failure(Exception("Дата окончания должна быть в будущем"))
            }
            
            // Проверяем, что привычка существует
            val habit = habitRepository.getHabitById(habitId)
                ?: return Result.failure(Exception("Привычка не найдена"))
            
            // Проверяем, что создатель имеет доступ к привычке
            if (habit.type.name == "PERSONAL" && habit.createdBy != createdBy) {
                return Result.failure(Exception("Нет доступа к этой привычке"))
            }
            
            if (habit.type.name == "GROUP" && !habit.participants.contains(createdBy)) {
                return Result.failure(Exception("Вы не являетесь участником этой привычки"))
            }
            
            // Проверяем дружбу с участниками
            val allParticipants = participantIds + createdBy
            for (participantId in participantIds) {
                if (participantId == createdBy) continue
                
                val areFriends = socialRepository.areFriends(createdBy, participantId)
                if (!areFriends) {
                    val user = socialRepository.getUserById(participantId)
                    return Result.failure(Exception("${user?.name ?: "Пользователь"} не является вашим другом"))
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
                createdAt = Clock.System.now().toEpochMilliseconds(),
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )
            
            // Сохраняем в репозиторий
            challengeRepository.insertChallenge(challenge)
            
            // Отправляем уведомления участникам
            try {
                val creatorUser = socialRepository.getUserById(createdBy)
                participantIds.forEach { participantId ->
                    if (participantId != createdBy) {
                        socialRepository.sendChallengeInvitationNotification(
                            participantId,
                            creatorUser?.name ?: "Пользователь",
                            title,
                            habit.title
                        )
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки уведомлений
            }
            
            Result.success(challenge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
