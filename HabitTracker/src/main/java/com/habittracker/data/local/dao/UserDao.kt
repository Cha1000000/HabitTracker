package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
    suspend fun updateLastActive(userId: String, timestamp: Long)

    @Query("UPDATE users SET avatarUrl = :avatarUrl WHERE id = :userId")
    suspend fun updateUserAvatar(userId: String, avatarUrl: String?)

    @Query("UPDATE users SET avatarThumbnailUrl = :avatarThumbnailUrl WHERE id = :userId")
    suspend fun updateUserAvatarThumbnail(userId: String, avatarThumbnailUrl: String?)

    @Query("UPDATE users SET avatarUrl = :avatarUrl, avatarThumbnailUrl = :avatarThumbnailUrl WHERE id = :userId")
    suspend fun updateUserAvatars(userId: String, avatarUrl: String?, avatarThumbnailUrl: String?)

    /** [query] — LIKE-шаблон; спецсимволы в пользовательском вводе экранируются символом `\`. */
    @Query("SELECT * FROM users WHERE name LIKE :query ESCAPE '\\' OR email LIKE :query ESCAPE '\\' ORDER BY name ASC LIMIT 50")
    suspend fun searchUsers(query: String): List<UserEntity>
}