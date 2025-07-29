package com.habittracker.data.repository

import com.habittracker.data.local.CalendarDayData
import com.habittracker.data.local.HabitEntryDao
import com.habittracker.data.local.entity.toDomain
import com.habittracker.data.local.entity.toEntity
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitEntryRepositoryImpl @Inject constructor(
    private val habitEntryDao: HabitEntryDao
) : HabitEntryRepository {

    override suspend fun insertEntry(entry: HabitEntry) {
        habitEntryDao.insertEntry(entry.toEntity())
    }

    override suspend fun updateEntry(entry: HabitEntry) {
        habitEntryDao.updateEntry(entry.toEntity())
    }

    override fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?> {
        return habitEntryDao.getEntryFlow(habitId, userId, date).map { it?.toDomain() }
    }

    override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? {
        return habitEntryDao.getEntry(habitId, userId, date)?.toDomain()
    }

    override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> {
        return habitEntryDao.getEntriesForHabit(habitId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> {
        return habitEntryDao.getEntriesForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> {
        return habitEntryDao.getEntriesForUserAndHabit(userId, habitId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEntriesForUserAndPeriod(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitEntry> {
        return habitEntryDao.getEntriesForUserAndPeriod(habitId, userId, startDate, endDate)
            .map { it.toDomain() }
    }

    override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser> {
        return habitEntryDao.getEntriesWithUsersForHabitAndDate(habitId, date).map { it.toDomain() }
    }

    override suspend fun getCompletionRateForHabit(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Float {
        val entries = getEntriesForUserAndPeriod(habitId, userId, startDate, endDate)
        if (entries.isEmpty()) return 0f
        val completedCount = entries.count { it.completed }
        return (completedCount.toFloat() / entries.size)
    }

    override suspend fun getCalendarData(userId: String, startDate: LocalDate, endDate: LocalDate): List<CalendarDayData> {
        return habitEntryDao.getCalendarData(userId, startDate, endDate)
    }

    override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {
        val existingEntry = getEntry(habitId, userId, date)
        val now = Clock.System.now()

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = true,
                completedAt = now,
                note = note,
                updatedAt = now
            )
            updateEntry(updatedEntry)
        } else {
            val newEntry = HabitEntry(
                habitId = habitId,
                userId = userId,
                date = date,
                completed = true,
                completedAt = now,
                note = note,
                createdAt = now,
                updatedAt = now
            )
            insertEntry(newEntry)
        }
    }

    override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {
        val existingEntry = getEntry(habitId, userId, date)
        val now = Clock.System.now()

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = false,
                completedAt = null,
                updatedAt = now
            )
            updateEntry(updatedEntry)
        } else {
            val newEntry = HabitEntry(
                habitId = habitId,
                userId = userId,
                date = date,
                completed = false,
                completedAt = null,
                note = null,
                createdAt = now,
                updatedAt = now
            )
            insertEntry(newEntry)
        }
    }
}
