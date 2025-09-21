package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFriendsUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    operator fun invoke(userId: String): Flow<List<FriendshipWithUsers>> {
        return socialRepository.getFriends(userId)
    }
}
