package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для отклонения приглашения в групповую привычку
 */
class DeclineHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    /**
     * Отклоняет приглашение в групповую привычку
     * @param invitationId ID приглашения
     * @param userId ID пользователя, отклоняющего приглашение
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
                return Result.failure(Exception("Нет прав для отклонения этого приглашения"))
            }

            // Отклоняем приглашение через SocialRepository
            socialRepository.declineHabitInvitation(invitationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
