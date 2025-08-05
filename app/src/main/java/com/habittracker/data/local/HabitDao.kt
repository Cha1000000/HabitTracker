package com.habittracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.HabitEntity
import com.habittracker.domain.model.HabitType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :habitId")
    fun getHabitByIdFlow(habitId: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE inviteCode = :inviteCode AND isActive = 1")
    suspend fun getHabitByInviteCode(inviteCode: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY createdAt DESC
    """)
    fun getUserHabits(userId: String): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND type = :type AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY createdAt DESC
    """)
    fun getUserHabitsByType(userId: String, type: HabitType): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND type = 'GROUP' AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY updatedAt DESC
    """)
    fun getUserGroupHabits(userId: String): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND type = 'PERSONAL' AND createdBy = :userId
        ORDER BY createdAt DESC
    """)
    fun getUserPersonalHabits(userId: String): Flow<List<HabitEntity>>

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND reminderEnabled = 1 AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        ORDER BY reminderTime ASC
    """)
    suspend fun getHabitsWithReminders(userId: String): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE createdBy = :userId ORDER BY createdAt DESC")
    fun getHabitsCreatedByUser(userId: String): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :habitId")
    suspend fun updateHabitActiveStatus(habitId: String, isActive: Boolean, updatedAt: Instant)

    @Query("UPDATE habits SET participants = :participants, updatedAt = :updatedAt WHERE id = :habitId")
    suspend fun updateHabitParticipants(habitId: String, participants: List<String>, updatedAt: Instant)

    @Query("""
        UPDATE habits 
        SET reminderEnabled = :enabled, reminderTime = :time, updatedAt = :updatedAt 
        WHERE id = :habitId
    """)
    suspend fun updateHabitReminder(
        habitId: String,
        enabled: Boolean,
        time: kotlinx.datetime.LocalTime?,
        updatedAt: Instant
    )

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabitById(habitId: String)

    // Поиск и фильтрация
    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
        AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun searchUserHabits(userId: String, query: String): Flow<List<HabitEntity>>

    // Статистические запросы
    @Query("SELECT COUNT(*) FROM habits WHERE isActive = 1")
    suspend fun getActiveHabitsCount(): Int

    @Query("SELECT COUNT(*) FROM habits WHERE isActive = 1 AND createdBy = :userId")
    suspend fun getUserHabitsCount(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM habits 
        WHERE isActive = 1 AND type = 'GROUP' AND (createdBy = :userId OR participants LIKE '%' || :userId || '%')
    """)
    suspend fun getUserGroupHabitsCount(userId: String): Int

    @Query("""
        SELECT * FROM habits 
        WHERE isActive = 1 AND type = 'GROUP' 
        ORDER BY LENGTH(participants) DESC 
        LIMIT :limit
    """)
    suspend fun getMostPopularGroupHabits(limit: Int = 10): List<HabitEntity>
}
