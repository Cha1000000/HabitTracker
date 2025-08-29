package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isExpired
import com.habittracker.domain.model.isPending
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для принятия приглашения в групповую привычку
 */
class AcceptHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val habitRepository: HabitRepository
) {
    /**
     * Принимает приглашение в групповую привычку
     * @param invitationId ID приглашения
     * @param userId ID пользователя, принимающего приглашение
     * @return Result с обновленным приглашением или ошибкой
     */
    suspend operator fun invoke(
        invitationId: String,
        userId: String
    ): Result<HabitInvitation> {
        return try {
            // Получаем приглашение
            val invitation = socialRepository.getHabitInvitationById(invitationId)
                ?: return Result.failure(Exception("Приглашение не найдено"))

            // Проверяем, что приглашение адресовано этому пользователю
            if (invitation.toUserId != userId) {
                return Result.failure(Exception("Нет прав для принятия этого приглашения"))
            }

            // Проверяем статус приглашения
            if (!invitation.isPending()) {
                return Result.failure(Exception("Приглашение уже обработано или недействительно"))
            }

            // Проверяем, не истекло ли приглашение
            if (invitation.isExpired()) {
                return Result.failure(Exception("Приглашение истекло"))
            }

            // Получаем привычку для дополнительных проверок
            val habit = habitRepository.getHabitById(invitation.habitId)
                ?: return Result.failure(Exception("Привычка не найдена"))

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                return Result.failure(Exception("Нельзя присоединиться к неактивной привычке"))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                return Result.failure(Exception("В привычке нет свободных мест (максимум ${habit.maxParticipants} участников)"))
            }

            // Проверяем, что пользователь еще не участвует в привычке (дополнительная проверка)
            if (habit.isUserParticipant(userId)) {
                return Result.failure(Exception("Вы уже участвуете в этой привычке"))
            }

            // Проверяем, что пользователь существует
            val user = socialRepository.getUserById(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            // Принимаем приглашение через SocialRepository
            val acceptedInvitation = socialRepository.acceptHabitInvitation(invitationId)
            // Побочные эффекты (добавление участника, пуш-уведомления) выполняются на уровне репозитория
            
            acceptedInvitation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
