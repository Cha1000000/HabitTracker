package com.habittracker.domain.usecase.habit

import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для отмены приглашения в групповую привычку
 */
class CancelHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    /**
     * Отменяет отправленное приглашение в групповую привычку
     * @param invitationId ID приглашения
     * @param userId ID пользователя, отменяющего приглашение
     * @return Result с результатом операции
     */
    suspend operator fun invoke(
        invitationId: String,
        userId: String
    ): Result<Unit> {
        return try {
            // Получаем приглашение
            val invitation = socialRepository.getHabitInvitationById(invitationId)
                ?: return Result.failure(Exception("Приглашение не найдено"))

            // Проверяем, что приглашение отправлено этим пользователем
            if (invitation.fromUserId != userId) {
                return Result.failure(Exception("Нет прав для отмены этого приглашения"))
            }

            // Отменяем приглашение через SocialRepository
            socialRepository.cancelHabitInvitation(invitationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
