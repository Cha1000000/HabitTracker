package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.HabitEntity
import com.habittracker.domain.model.HabitType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalTime

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :habitId")
    fun getHabitByIdFlow(habitId: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE inviteCode = :inviteCode AND isActive = 1 AND deletedAtMillis IS NULL")
    suspend fun getHabitByInviteCode(inviteCode: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE isActive = 1 AND deletedAtMillis IS NULL ORDER BY createdAt DESC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY createdAt DESC
    """)
    fun getUserHabits(userId: String): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deletedAtMillis IS NULL ORDER BY createdAt DESC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND type = :type AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY createdAt DESC
    """)
    fun getUserHabitsByType(userId: String, type: HabitType): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND type = 'GROUP' AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY updatedAtMillis DESC
    """)
    fun getUserGroupHabits(userId: String): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND type = 'PERSONAL' AND createdBy = :userId
        ORDER BY createdAt DESC
    """)
    fun getUserPersonalHabits(userId: String): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND reminderEnabled = 1 AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY reminderTime ASC
    """)
    suspend fun getHabitsWithReminders(userId: String): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE deletedAtMillis IS NULL AND createdBy = :userId ORDER BY createdAt DESC")
    fun getHabitsCreatedByUser(userId: String): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET isActive = :isActive, updatedAtMillis = :updatedAtMillis WHERE id = :habitId")
    suspend fun updateHabitActiveStatus(habitId: String, isActive: Boolean, updatedAtMillis: Long)

    @Query("UPDATE habits SET participants = :participants, updatedAtMillis = :updatedAtMillis WHERE id = :habitId")
    suspend fun updateHabitParticipants(habitId: String, participants: List<String>, updatedAtMillis: Long)

    @Query("""
        UPDATE habits 
        SET reminderEnabled = :enabled, reminderTime = :time, updatedAtMillis = :updatedAtMillis 
        WHERE id = :habitId
    """)
    suspend fun updateHabitReminder(
        habitId: String,
        enabled: Boolean,
        time: LocalTime?,
        updatedAtMillis: Long
    )

    @Query("UPDATE habits SET isActive = 0, deletedAtMillis = :deletedAtMillis, updatedAtMillis = :updatedAtMillis WHERE id = :habitId")
    suspend fun softDeleteHabitById(habitId: String, deletedAtMillis: Long, updatedAtMillis: Long)

    // Поиск и фильтрация
    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun searchUserHabits(userId: String, query: String): Flow<List<HabitEntity>>

    // Статистические запросы
    @Query("SELECT COUNT(*) FROM habits WHERE isActive = 1 AND deletedAtMillis IS NULL")
    suspend fun getActiveHabitsCount(): Int

    @Query("SELECT COUNT(*) FROM habits WHERE isActive = 1 AND deletedAtMillis IS NULL AND createdBy = :userId")
    suspend fun getUserHabitsCount(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND type = 'GROUP' AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
    """)
    suspend fun getUserGroupHabitsCount(userId: String): Int

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND deletedAtMillis IS NULL AND type = 'GROUP' 
        ORDER BY LENGTH(participants) DESC 
        LIMIT :limit
    """)
    suspend fun getMostPopularGroupHabits(limit: Int = 10): List<HabitEntity>
}