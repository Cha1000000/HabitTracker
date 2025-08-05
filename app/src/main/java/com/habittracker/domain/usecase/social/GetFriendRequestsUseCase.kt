package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFriendRequestsUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    fun getIncomingRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return socialRepository.getIncomingFriendRequests(userId)
    }
    
    fun getOutgoingRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return socialRepository.getOutgoingFriendRequests(userId)
    }
    
    fun getIncomingRequestsCount(userId: String): Flow<Int> {
        return socialRepository.getIncomingRequestsCount(userId)
    }
}
