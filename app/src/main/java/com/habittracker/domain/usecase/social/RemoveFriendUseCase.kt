package com.habittracker.domain.usecase.social

import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

class RemoveFriendUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    suspend operator fun invoke(currentUserId: String, friendId: String): Result<Unit> {
        if (currentUserId == friendId) {
            return Result.failure(IllegalArgumentException("Нельзя удалить самого себя из друзей"))
        }
        
        return try {
            // Проверяем, что пользователи действительно друзья
            val areFriends = socialRepository.areFriends(currentUserId, friendId)
            if (!areFriends) {
                return Result.failure(IllegalStateException("Пользователи не являются друзьями"))
            }
            
            socialRepository.removeFriend(currentUserId, friendId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
