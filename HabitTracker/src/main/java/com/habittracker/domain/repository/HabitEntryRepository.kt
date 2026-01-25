package com.habittracker.domain.repository

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface HabitEntryRepository {

    suspend fun insertEntry(entry: HabitEntry)

    suspend fun updateEntry(entry: HabitEntry)

    fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?>

    suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry?

    fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>>

    fun getEntriesForUser(userId: String): Flow<List<HabitEntry>>

    fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>>

    suspend fun getEntriesForUserAndHabitOnce(userId: String, habitId: String): List<HabitEntry>

    suspend fun deleteEntriesForUserAndHabit(userId: String, habitId: String)

    suspend fun getEntriesForUserAndPeriod(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitEntry>

    suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser>

    suspend fun getCompletionRateForHabit(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Float

    suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String? = null)

    suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate)

    suspend fun setRestDay(habitId: String, userId: String, date: LocalDate, isRestDay: Boolean)
}
