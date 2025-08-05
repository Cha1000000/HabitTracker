package com.habittracker.domain.usecase.social

import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для отмены отправленного запроса в друзья
 */
class CancelFriendRequestUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    /**
     * Отменяет отправленный запрос в друзья
     * @param requestId ID запроса
     * @param fromUserId ID пользователя, который отправил запрос
     * @return Result с результатом операции
     */
    suspend operator fun invoke(requestId: String, fromUserId: String): Result<Unit> {
        return try {
            // Проверяем, что запрос существует и отправлен текущим пользователем
            val request = socialRepository.getFriendRequestById(requestId)
            if (request == null) {
                Result.failure(Exception("Запрос в друзья не найден"))
            } else if (request.fromUserId != fromUserId) {
                Result.failure(Exception("Нет прав для отмены этого запроса"))
            } else {
                socialRepository.cancelFriendRequest(requestId)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
