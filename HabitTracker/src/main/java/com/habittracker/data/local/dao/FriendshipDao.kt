package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.FriendshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendshipDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertFriendship(friendship: FriendshipEntity)

    @Update
    suspend fun updateFriendship(friendship: FriendshipEntity)

    @Query("DELETE FROM friendships WHERE userId1 = :userId1 AND userId2 = :userId2")
    suspend fun deleteFriendship(userId1: String, userId2: String)

    @Query("SELECT * FROM friendships WHERE (userId1 = :userId OR userId2 = :userId) AND status = 'ACCEPTED'")
    fun getFriends(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userId2 = :userId AND status = 'PENDING'")
    fun getPendingFriendRequests(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userId1 = :userId AND status = 'PENDING'")
    fun getSentFriendRequests(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userId1 = :userId1 AND userId2 = :userId2")
    fun getFriendshipStatus(userId1: String, userId2: String): Flow<FriendshipEntity?>

    // Дополнительные методы для SocialRepository
    @Query("SELECT * FROM friendships WHERE (userId1 = :userId OR userId2 = :userId) AND status = 'ACCEPTED'")
    fun getFriendsForUser(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT COUNT(*) FROM friendships WHERE (userId1 = :userId OR userId2 = :userId) AND status = 'ACCEPTED'")
    fun getFriendsCount(userId: String): Flow<Int>

    @Query("SELECT * FROM friendships WHERE (userId1 = :userId1 AND userId2 = :userId2) OR (userId1 = :userId2 AND userId2 = :userId1)")
    suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String): FriendshipEntity?

    @Query("DELETE FROM friendships WHERE (userId1 = :userId1 AND userId2 = :userId2) OR (userId1 = :userId2 AND userId2 = :userId1)")
    suspend fun deleteFriendshipBetweenUsers(userId1: String, userId2: String)
}