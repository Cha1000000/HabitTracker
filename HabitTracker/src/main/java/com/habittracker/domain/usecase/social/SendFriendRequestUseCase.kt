package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import javax.inject.Inject

class SendFriendRequestUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) {
    suspend operator fun invoke(
        fromUserId: String,
        toUserId: String,
        message: String? = null
    ): Result<FriendRequest> {
        if (fromUserId == toUserId) {
            return Result.failure(IllegalArgumentException("Нельзя отправить запрос самому себе"))
        }
        
        return try {
            // Проверяем, можно ли отправить запрос
            val canSend = socialRepository.canSendFriendRequest(fromUserId, toUserId)
            if (!canSend) {
                return Result.failure(IllegalStateException("Запрос в друзья уже существует или пользователи уже друзья"))
            }
            
            // Отправляем запрос
            val result = socialRepository.sendFriendRequest(fromUserId, toUserId, message)
            
            // Если успешно, отправляем уведомление и начисляем очки
            if (result.isSuccess) {
                try {
                    // Award points for inviting a friend
                    updateUserPointsUseCase(fromUserId, GamificationEvent.FriendInvited)

                    // Check achievements
                    checkAchievementsUseCase(fromUserId)

                    val fromUser = socialRepository.getUserById(fromUserId)
                    fromUser?.let { user ->
                        socialRepository.sendFriendRequestNotification(toUserId, user.name)
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки уведомлений, главное - запрос отправлен
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
