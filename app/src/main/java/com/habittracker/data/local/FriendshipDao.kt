package com.habittracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.FriendshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendshipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: FriendshipEntity)

    @Update
    suspend fun updateFriendship(friendship: FriendshipEntity)

    @Query("DELETE FROM friendships WHERE userId1 = :userId1 AND userId2 = :userId2")
    suspend fun deleteFriendship(userId1: String, userId2: String)

    @Query("SELECT * FROM friendships WHERE (userId1 = :userId OR userId2 = :userId) AND status = 'ACCEPTED'")
    fun getFriends(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userId2 = :userId AND status = 'PENDING'")
    fun getPendingFriendRequests(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userId1 = :userId1 AND userId2 = :userId2")
    fun getFriendshipStatus(userId1: String, userId2: String): Flow<FriendshipEntity?>
}
