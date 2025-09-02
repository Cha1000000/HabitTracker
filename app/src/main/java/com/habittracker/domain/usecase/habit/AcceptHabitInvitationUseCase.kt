package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.isExpired
import com.habittracker.domain.model.isPending
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для принятия приглашения в групповую привычку
 */
class AcceptHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository
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
