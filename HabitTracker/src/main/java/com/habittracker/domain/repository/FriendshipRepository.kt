package com.habittracker.domain.repository

import com.habittracker.domain.model.Friendship
import kotlinx.coroutines.flow.Flow

interface FriendshipRepository {
    
    suspend fun insertFriendship(friendship: Friendship)
    
    suspend fun updateFriendship(friendship: Friendship)
    
    suspend fun deleteFriendship(userId1: String, userId2: String)

    suspend fun getFriendship(userId1: String, userId2: String): Friendship?
    
    fun getAllFriendships(): Flow<List<Friendship>>
    
    fun getFriendsForUser(userId: String): Flow<List<Friendship>>
    
    fun getPendingFriendRequests(userId: String): Flow<List<Friendship>>
    
    fun getSentFriendRequests(userId: String): Flow<List<Friendship>>
    
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String)
    
    suspend fun acceptFriendRequest(userId1: String, userId2: String)
    
    suspend fun rejectFriendRequest(userId1: String, userId2: String)
    
    suspend fun removeFriend(userId1: String, userId2: String)
    
    suspend fun areFriends(userId1: String, userId2: String): Boolean
    
    suspend fun getFriendIds(userId: String): List<String>
}
