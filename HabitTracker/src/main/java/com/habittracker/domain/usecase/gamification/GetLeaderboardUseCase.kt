package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.User
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case для получения таблицы лидеров (рейтинга) среди друзей.
 */
class GetLeaderboardUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val userRepository: UserRepository
) {

    /**
     * Получает список пользователей (друзья + текущий пользователь),
     * отсортированный по количеству очков.
     */
    operator fun invoke(currentUserId: String): Flow<List<User>> {
        val friendsFlow = socialRepository.getFriends(currentUserId)
        val currentUserFlow = userRepository.observeUser(currentUserId)

        return combine(friendsFlow, currentUserFlow) { friends, currentUser ->
            val users = mutableListOf<User>()
            
            // Добавляем текущего пользователя
            if (currentUser != null) {
                users.add(currentUser)
            }
            
            // Добавляем друзей
            friends.forEach { friendshipWithUsers ->
                val friend = friendshipWithUsers.getOtherUser(currentUserId)
                if (friend != null) {
                    users.add(friend)
                }
            }
            
            // Сортируем по очкам (по убыванию)
            users.sortedByDescending { it.points }
        }
    }
}
