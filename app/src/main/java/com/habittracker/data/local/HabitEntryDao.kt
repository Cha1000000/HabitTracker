package com.habittracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.tuples.HabitEntryWithUserTuple
import com.habittracker.data.local.entity.tuples.HabitStreakTuple
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Dao
interface HabitEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HabitEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<HabitEntryEntity>)

    @Update
    suspend fun updateEntry(entry: HabitEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: HabitEntryEntity)

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND date = :date")
    fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntryEntity?>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND userId = :userId AND date = :date")
    suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntryEntity?

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId ORDER BY date DESC")
    fun getEntriesForHabit(habitId: String): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE userId = :userId ORDER BY date DESC")
    fun getEntriesForUser(userId: String): Flow<List<HabitEntryEntity>>
    
    @Query("SELECT * FROM habit_entries WHERE userId = :userId AND habitId = :habitId ORDER BY date DESC")
    fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId ORDER BY date DESC, userId ASC")
    fun getAllEntriesForHabit(habitId: String): Flow<List<HabitEntryEntity>>

    @Query("""
        SELECT * FROM habit_entries 
        WHERE habitId = :habitId AND userId = :userId 
        AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
    """)
    suspend fun getUserEntriesForPeriod(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitEntryEntity>

    @Query("""
        SELECT * FROM habit_entries 
        WHERE habitId = :habitId AND userId = :userId 
        AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
    """)
    suspend fun getEntriesForUserAndPeriod(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitEntryEntity>

    @Query("""
        SELECT * FROM habit_entries 
        WHERE habitId = :habitId AND date = :date
        ORDER BY completedAt IS NULL, completedAt DESC
    """)
    suspend fun getEntriesForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryEntity>

    @androidx.room.Transaction
    @Query("""
        SELECT * FROM habit_entries
        WHERE habitId = :habitId AND date = :date
        ORDER BY completedAt IS NULL, completedAt DESC
    """)
    suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUserTuple>

    @Query("""
        SELECT * FROM habit_entries 
        WHERE userId = :userId AND date = :date
        ORDER BY updatedAt DESC
    """)
    suspend fun getUserEntriesForDate(userId: String, date: LocalDate): List<HabitEntryEntity>

    @Query("""
        SELECT * FROM habit_entries 
        WHERE userId = :userId AND completed = 1
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getUserRecentCompletedEntries(userId: String, limit: Int = 20): List<HabitEntryEntity>

    @Query("""
        UPDATE habit_entries 
        SET completed = :completed, completedAt = :completedAt, value = :value, 
            note = :note, updatedAt = :updatedAt
        WHERE habitId = :habitId AND userId = :userId AND date = :date
    """)
    suspend fun updateEntryCompletion(
        habitId: String,
        userId: String,
        date: LocalDate,
        completed: Boolean,
        completedAt: Instant?,
        value: Int,
        note: String,
        updatedAt: Instant
    )

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId")
    suspend fun deleteEntriesForHabit(habitId: String)

    @Query("DELETE FROM habit_entries WHERE userId = :userId")
    suspend fun deleteEntriesForUser(userId: String)

    // Статистические запросы
    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND completed = 1")
    suspend fun getCompletedDaysCount(habitId: String): Int

    @Query("""
        SELECT COUNT(*) FROM habit_entries 
        WHERE habitId = :habitId AND userId = :userId AND completed = 1
    """)
    suspend fun getCompletedEntriesCount(habitId: String, userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM habit_entries 
        WHERE habitId = :habitId AND userId = :userId
    """)
    suspend fun getTotalEntriesCount(habitId: String, userId: String): Int

    @Query("""
        SELECT * FROM habit_entries 
        WHERE habitId = :habitId AND userId = :userId AND completed = 1
        ORDER BY date DESC
        LIMIT 1
    """)
    suspend fun getLastCompletedEntry(habitId: String, userId: String): HabitEntryEntity?

    @Query("""
        SELECT AVG(CASE WHEN completed = 1 THEN 1.0 ELSE 0.0 END) as completion_rate
        FROM habit_entries 
        WHERE habitId = :habitId AND userId = :userId
    """)
    suspend fun getCompletionRate(habitId: String, userId: String): Float

    @Query("""
        SELECT COUNT(*) FROM habit_entries 
        WHERE userId = :userId AND completed = 1 AND date = :date
    """)
    suspend fun getCompletedHabitsCountForDate(userId: String, date: LocalDate): Int

    @Query("""
        SELECT habitId, COUNT(*) as streak 
        FROM habit_entries 
        WHERE userId = :userId AND completed = 1 
        AND date >= (
            SELECT MAX(date) 
            FROM habit_entries 
            WHERE userId = :userId AND habitId = habit_entries.habitId AND completed = 0
        ) OR date >= (
            SELECT MAX(date) 
            FROM habit_entries 
            WHERE userId = :userId AND habitId = habit_entries.habitId
        )
        GROUP BY habitId
        ORDER BY streak DESC
    """)
    suspend fun getCurrentStreaks(userId: String): List<HabitStreakTuple>

    @Query("""
        SELECT COUNT(*) as streak
        FROM habit_entries
        WHERE userId = :userId AND habitId = :habitId AND completed = 1
        AND date >= (
            SELECT COALESCE(MAX(date), '1900-01-01')
            FROM habit_entries
            WHERE userId = :userId AND habitId = :habitId AND completed = 0
            AND date < (
                SELECT MAX(date)
                FROM habit_entries
                WHERE userId = :userId AND habitId = :habitId AND completed = 1
            )
        )
    """)
    suspend fun getCurrentStreakForHabit(userId: String, habitId: String): Int?

    @Query("""
        SELECT MAX(streak) FROM (
            SELECT COUNT(*) as streak
            FROM habit_entries h1
            WHERE userId = :userId AND habitId = :habitId AND completed = 1
            AND date >= (
                SELECT COALESCE(MAX(date), '1900-01-01')
                FROM habit_entries h2
                WHERE userId = :userId AND habitId = :habitId AND completed = 0
                AND h2.date < h1.date
            )
            GROUP BY (
                SELECT COUNT(*)
                FROM habit_entries h3
                WHERE userId = :userId AND habitId = :habitId AND completed = 0
                AND h3.date < h1.date
            )
        )
    """)
    suspend fun getLongestStreakForHabit(userId: String, habitId: String): Int?

    @Query("""
        SELECT date, COUNT(*) as total_habits, 
               SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) as completed_habits
        FROM habit_entries 
        WHERE userId = :userId AND date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date
    """)
    suspend fun getCalendarData(userId: String, startDate: LocalDate, endDate: LocalDate): List<CalendarDayData>

    @Query("""
        SELECT COUNT(*) FROM habit_entries
        WHERE habitId = :habitId AND userId = :userId AND completed = 1 AND date >= :since
    """)
    suspend fun getCompletedCountSince(habitId: String, userId: String, since: LocalDate): Int

    @Query("""
        SELECT COUNT(*) FROM habit_entries
        WHERE habitId = :habitId AND userId = :userId AND date >= :since
    """)
    suspend fun getTotalCountSince(habitId: String, userId: String, since: LocalDate): Int
}

// Data class для календарных данных
data class CalendarDayData(
    val date: LocalDate,
    @ColumnInfo(name = "total_habits") val totalHabits: Int,
    @ColumnInfo(name = "completed_habits") val completedHabits: Int
) {
    val completionRate: Float
        get() = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else 0f
}