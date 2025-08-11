package com.habittracker.data.repository

import com.habittracker.data.local.dao.FriendshipDao
import com.habittracker.data.local.entity.toDomain
import com.habittracker.data.local.entity.toEntity
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipStatus
import com.habittracker.domain.repository.FriendshipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendshipRepositoryImpl @Inject constructor(
    private val friendshipDao: FriendshipDao
) : FriendshipRepository {
    
    override suspend fun insertFriendship(friendship: Friendship) {
        friendshipDao.insertFriendship(friendship.toEntity())
    }
    
    override suspend fun updateFriendship(friendship: Friendship) {
        friendshipDao.updateFriendship(friendship.toEntity())
    }
    
    override suspend fun deleteFriendship(userId1: String, userId2: String) {
        friendshipDao.deleteFriendship(userId1, userId2)
    }

    override suspend fun getFriendship(userId1: String, userId2: String): Friendship? {
        return friendshipDao.getFriendshipStatus(userId1, userId2).first()?.toDomain()
    }
    
    override fun getAllFriendships(): Flow<List<Friendship>> {
        return kotlinx.coroutines.flow.flowOf(emptyList<Friendship>())
    }
    
    override fun getFriendsForUser(userId: String): Flow<List<Friendship>> {
        return friendshipDao.getFriends(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getPendingFriendRequests(userId: String): Flow<List<Friendship>> {
        return friendshipDao.getPendingFriendRequests(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getSentFriendRequests(userId: String): Flow<List<Friendship>> {
        return friendshipDao.getSentFriendRequests(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String) {
        val now = Clock.System.now()
        val friendship = Friendship(
            userId1 = fromUserId,
            userId2 = toUserId,
            status = FriendshipStatus.PENDING,
            requestedBy = fromUserId,
            requestedAt = now,
            updatedAt = now
        )
        insertFriendship(friendship)
    }
    
    override suspend fun acceptFriendRequest(userId1: String, userId2: String) {
        val friendship = getFriendship(userId1, userId2)
        if (friendship != null) {
            val updatedFriendship = friendship.copy(
                status = FriendshipStatus.ACCEPTED,
                acceptedAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            updateFriendship(updatedFriendship)
        }
    }
    
    override suspend fun rejectFriendRequest(userId1: String, userId2: String) {
        deleteFriendship(userId1, userId2)
    }
    
    override suspend fun removeFriend(userId1: String, userId2: String) {
        deleteFriendship(userId1, userId2)
    }
    
    override suspend fun areFriends(userId1: String, userId2: String): Boolean {
        val friendship = getFriendship(userId1, userId2)
        return friendship?.status == FriendshipStatus.ACCEPTED
    }
    
    override suspend fun getFriendIds(userId: String): List<String> {
        return getFriendsForUser(userId).first().mapNotNull {
            if (it.userId1 == userId) it.userId2 else if (it.userId2 == userId) it.userId1 else null
        }
    }
}
