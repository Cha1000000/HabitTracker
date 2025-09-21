package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.canBeDeclined
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

class DeclineFriendRequestUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    suspend operator fun invoke(requestId: String, currentUserId: String): Result<FriendRequest> {
        return try {
            // Проверяем, что запрос существует и адресован текущему пользователю
            val friendRequest = socialRepository.getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException("Запрос в друзья не найден"))
            
            if (friendRequest.toUserId != currentUserId) {
                return Result.failure(IllegalArgumentException("Вы не можете отклонить этот запрос"))
            }
            
            if (!friendRequest.canBeDeclined(currentUserId)) {
                return Result.failure(IllegalStateException("Запрос не может быть отклонен"))
            }
            
            // Отклоняем запрос
            socialRepository.declineFriendRequest(requestId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
