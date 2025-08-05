package com.habittracker.data.local

import androidx.room.*
import com.habittracker.data.local.entity.FriendRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendRequestDao {
    
    @Query("SELECT * FROM friend_requests WHERE id = :id")
    suspend fun getFriendRequestById(id: String): FriendRequestEntity?
    
    @Query("SELECT * FROM friend_requests WHERE fromUserId = :userId OR toUserId = :userId")
    fun getFriendRequestsForUser(userId: String): Flow<List<FriendRequestEntity>>
    
    @Query("SELECT * FROM friend_requests WHERE toUserId = :userId AND status = 'PENDING'")
    fun getIncomingFriendRequests(userId: String): Flow<List<FriendRequestEntity>>
    
    @Query("SELECT * FROM friend_requests WHERE fromUserId = :userId AND status = 'PENDING'")
    fun getOutgoingFriendRequests(userId: String): Flow<List<FriendRequestEntity>>
    
    @Query("SELECT * FROM friend_requests WHERE fromUserId = :fromUserId AND toUserId = :toUserId")
    suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String): FriendRequestEntity?
    
    @Query("SELECT COUNT(*) FROM friend_requests WHERE toUserId = :userId AND status = 'PENDING'")
    fun getIncomingRequestsCount(userId: String): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendRequest(friendRequest: FriendRequestEntity)
    
    @Update
    suspend fun updateFriendRequest(friendRequest: FriendRequestEntity)
    
    @Delete
    suspend fun deleteFriendRequest(friendRequest: FriendRequestEntity)
    
    @Query("DELETE FROM friend_requests WHERE id = :id")
    suspend fun deleteFriendRequestById(id: String)
    
    @Query("DELETE FROM friend_requests WHERE (fromUserId = :userId1 AND toUserId = :userId2) OR (fromUserId = :userId2 AND toUserId = :userId1)")
    suspend fun deleteFriendRequestsBetweenUsers(userId1: String, userId2: String)
    
    @Query("UPDATE friend_requests SET status = 'EXPIRED' WHERE status = 'PENDING' AND createdAt < :expiredBefore")
    suspend fun expireOldRequests(expiredBefore: Long)
}
