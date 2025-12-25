package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.canBeAccepted
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import javax.inject.Inject

class AcceptFriendRequestUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) {
    suspend operator fun invoke(requestId: String, currentUserId: String): Result<Friendship> {
        return try {
            // Проверяем, что запрос существует и адресован текущему пользователю
            val friendRequest = socialRepository.getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException("Запрос в друзья не найден"))
            
            if (friendRequest.toUserId != currentUserId) {
                return Result.failure(IllegalArgumentException("Вы не можете принять этот запрос"))
            }
            
            if (!friendRequest.canBeAccepted(currentUserId)) {
                return Result.failure(IllegalStateException("Запрос не может быть принят"))
            }
            
            // Принимаем запрос
            val result = socialRepository.acceptFriendRequest(requestId)
            
            // Если успешно, отправляем уведомление отправителю
            if (result.isSuccess) {
                try {
                    // Award points to both users
                    updateUserPointsUseCase(currentUserId, GamificationEvent.NewFriendAdded)
                    updateUserPointsUseCase(friendRequest.fromUserId, GamificationEvent.NewFriendAdded)

                    // Check achievements for both users
                    checkAchievementsUseCase(currentUserId)
                    checkAchievementsUseCase(friendRequest.fromUserId)

                    val acceptedByUser = socialRepository.getUserById(currentUserId)
                    acceptedByUser?.let { user ->
                        socialRepository.sendFriendAcceptedNotification(
                            friendRequest.fromUserId, 
                            user.name
                        )
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки уведомлений
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
