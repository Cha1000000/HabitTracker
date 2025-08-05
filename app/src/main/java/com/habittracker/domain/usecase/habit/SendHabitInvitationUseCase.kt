package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для отправки приглашения в групповую привычку
 */
class SendHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val habitRepository: HabitRepository
) {
    /**
     * Отправляет приглашение в групповую привычку
     * @param habitId ID привычки
     * @param fromUserId ID отправителя
     * @param toUserId ID получателя
     * @param message Сообщение (опционально)
     * @return Result с приглашением или ошибкой
     */
    suspend operator fun invoke(
        habitId: String,
        fromUserId: String,
        toUserId: String,
        message: String? = null
    ): Result<HabitInvitation> {
        return try {
            // Проверяем, что пользователь не приглашает сам себя
            if (fromUserId == toUserId) {
                return Result.failure(Exception("Нельзя пригласить самого себя"))
            }

            // Получаем привычку для проверки прав и получения информации
            val habit = habitRepository.getHabitById(habitId)
                ?: return Result.failure(Exception("Привычка не найдена"))

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                return Result.failure(Exception("Нельзя приглашать в неактивную привычку"))
            }

            // Проверяем, что отправитель является создателем привычки
            if (habit.createdBy != fromUserId) {
                return Result.failure(Exception("Только создатель привычки может отправлять приглашения"))
            }

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                return Result.failure(Exception("Можно приглашать только в групповые привычки"))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                return Result.failure(Exception("В привычке нет свободных мест (максимум ${habit.maxParticipants} участников)"))
            }

            // Проверяем, что пользователь еще не участвует в привычке
            if (habit.isUserParticipant(toUserId)) {
                return Result.failure(Exception("Пользователь уже участвует в этой привычке"))
            }

            // Проверяем, что получатель существует
            val toUser = socialRepository.getUserById(toUserId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            // Проверяем дружеские связи (можно приглашать только друзей)
            val areFriends = socialRepository.areFriends(fromUserId, toUserId)
            if (!areFriends) {
                return Result.failure(Exception("Можно приглашать только друзей. Сначала добавьте пользователя в друзья"))
            }

            // Проверяем, нет ли уже активного приглашения для этого пользователя в эту привычку
            val existingInvitation = socialRepository.getPendingHabitInvitation(habitId, toUserId)
            if (existingInvitation != null) {
                return Result.failure(Exception("Пользователю уже отправлено приглашение в эту привычку"))
            }

            // Проверяем лимит активных исходящих приглашений от пользователя (максимум 10)
            val activeInvitationsCount = socialRepository.getActiveOutgoingInvitationsCount(fromUserId)
            if (activeInvitationsCount >= 10) {
                return Result.failure(Exception("Превышен лимит активных приглашений (максимум 10). Дождитесь ответа на предыдущие приглашения"))
            }

            // Получаем информацию об отправителе
            val fromUser = socialRepository.getUserById(fromUserId)
                ?: return Result.failure(Exception("Отправитель не найден"))

            // Отправляем приглашение через SocialRepository
            socialRepository.sendHabitInvitation(
                habitId = habitId,
                habitTitle = habit.title,
                fromUserId = fromUserId,
                fromUserName = fromUser.name,
                toUserId = toUserId,
                message = message
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
