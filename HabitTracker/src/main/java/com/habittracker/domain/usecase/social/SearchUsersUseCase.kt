package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.User
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

class SearchUsersUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    suspend operator fun invoke(query: String, currentUserId: String): List<User> {
        if (query.isBlank() || query.length < 2) {
            return emptyList()
        }
        
        return try {
            socialRepository.searchUsers(
                query = query.trim(),
                currentUserId = currentUserId
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}
