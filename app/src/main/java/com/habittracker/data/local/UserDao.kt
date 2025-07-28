package com.habittracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserByIdFlow(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id IN (:userIds)")
    suspend fun getUsersByIds(userIds: List<String>): List<UserEntity>

    @Query("SELECT * FROM users WHERE id IN (:userIds)")
    fun getUsersByIdsFlow(userIds: List<String>): Flow<List<UserEntity>>

    @Query("UPDATE users SET points = :points, level = :level WHERE id = :userId")
    suspend fun updateUserPoints(userId: String, points: Int, level: Int)

    @Query("UPDATE users SET streak = :streak, longestStreak = :longestStreak WHERE id = :userId")
    suspend fun updateUserStreaks(userId: String, streak: Int, longestStreak: Int)

    @Query("UPDATE users SET lastActiveAt = :timestamp WHERE id = :userId")
    suspend fun updateLastActive(userId: String, timestamp: Instant)

    @Query("UPDATE users SET avatarUrl = :avatarUrl WHERE id = :userId")
    suspend fun updateUserAvatar(userId: String, avatarUrl: String?)
}
