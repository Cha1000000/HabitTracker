package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.tuples.HabitEntryWithUserTuple
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface HabitEntryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HabitEntryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<HabitEntryEntity>)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntriesIfNotExists(entries: List<HabitEntryEntity>)
    
    @Update
    suspend fun updateEntry(entry: HabitEntryEntity)
    
    @Delete
    suspend fun deleteEntry(entry: HabitEntryEntity)
    
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND date = :date AND deletedAtMillis IS NULL")
    suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntryEntity?

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND deletedAtMillis IS NULL ORDER BY date DESC")
    suspend fun getEntriesForUserAndHabitOnce(habitId: String, userId: String): List<HabitEntryEntity>
    
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND deletedAtMillis IS NULL ORDER BY date DESC")
    fun getEntriesForHabit(habitId: String): Flow<List<HabitEntryEntity>>
    
    @Query("SELECT * FROM habit_entries WHERE userId = :userId AND deletedAtMillis IS NULL ORDER BY date DESC")
    fun getEntriesForUser(userId: String): Flow<List<HabitEntryEntity>>
    
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND deletedAtMillis IS NULL ORDER BY date DESC")
    fun getEntriesForUserAndHabit(habitId: String, userId: String): Flow<List<HabitEntryEntity>>
    
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date AND deletedAtMillis IS NULL")
    fun getEntriesForHabitAndDate(habitId: String, date: LocalDate): Flow<List<HabitEntryEntity>>
    
    @Transaction
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND deletedAtMillis IS NULL ORDER BY date DESC")
    fun getEntriesWithUsersForHabit(habitId: String): Flow<List<HabitEntryWithUserTuple>>
    
    @Transaction
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date AND deletedAtMillis IS NULL ORDER BY completedAt ASC")
    fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): Flow<List<HabitEntryWithUserTuple>>
    
    @Query("SELECT * FROM habit_entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate AND deletedAtMillis IS NULL ORDER BY date DESC")
    fun getEntriesForUserInDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<HabitEntryEntity>>
    
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND completed = 1 AND deletedAtMillis IS NULL ORDER BY date DESC")
    fun getCompletedEntriesForHabit(habitId: String): Flow<List<HabitEntryEntity>>
    
    @Query("SELECT COUNT(*) FROM habit_entries WHERE userId = :userId AND completed = 1 AND deletedAtMillis IS NULL")
    suspend fun getCompletedEntriesCountForUser(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND completed = 1 AND deletedAtMillis IS NULL")
    suspend fun getCompletedEntriesCountForUserAndHabit(habitId: String, userId: String): Int
    
    @Query("DELETE FROM habit_entries WHERE habitId = :habitId")
    suspend fun deleteAllEntriesForHabit(habitId: String)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND userId = :userId")
    suspend fun deleteEntriesForUserAndHabit(habitId: String, userId: String)
    
    @Query("DELETE FROM habit_entries WHERE userId = :userId")
    suspend fun deleteAllEntriesForUser(userId: String)
    
    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND completed = 1 AND date BETWEEN :startDate AND :endDate AND deletedAtMillis IS NULL")
    suspend fun getCompletedEntriesCountForUserInRange(
        habitId: String,
        userId: String,
        startDate: String,
        endDate: String
    ): Int
    
    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND date BETWEEN :startDate AND :endDate AND deletedAtMillis IS NULL ORDER BY date DESC")
    suspend fun getEntriesForUserInRange(
        habitId: String,
        userId: String,
        startDate: String,
        endDate: String
    ): List<HabitEntryEntity>
    
    @Query("SELECT COALESCE(SUM(value), 0) FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND date BETWEEN :startDate AND :endDate AND deletedAtMillis IS NULL")
    suspend fun getTotalValueForPeriod(
        habitId: String,
        userId: String,
        startDate: String,
        endDate: String
    ): Int

    @Query("SELECT date, COUNT(*) as count FROM habit_entries WHERE userId = :userId AND completed = 1 AND deletedAtMillis IS NULL GROUP BY date")
    fun getDailyCompletionCounts(userId: String): Flow<List<com.habittracker.data.local.entity.tuples.DailyCompletionCountTuple>>

    @Query("UPDATE habit_entries SET photoUrl = :photoUrl, updatedAtMillis = :updatedAtMillis WHERE habitId = :habitId AND userId = :userId AND date = :date")
    suspend fun updatePhotoUrl(habitId: String, userId: String, date: LocalDate, photoUrl: String?, updatedAtMillis: Long = System.currentTimeMillis())

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND date = :date AND deletedAtMillis IS NULL")
    fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntryEntity?>
}