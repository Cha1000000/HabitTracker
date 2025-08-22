package com.habittracker.data.repository

import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.local.mappers.toDomain as habitEntryToDomain
import com.habittracker.data.local.mappers.toEntity as habitEntryToEntity

@Singleton
class HabitEntryRepositoryImpl @Inject constructor(
    private val habitEntryDao: HabitEntryDao
) : HabitEntryRepository {

    override suspend fun insertEntry(entry: HabitEntry) {
        habitEntryDao.insertEntry(entry.habitEntryToEntity())
    }

    override suspend fun updateEntry(entry: HabitEntry) {
        habitEntryDao.updateEntry(entry.habitEntryToEntity())
    }

    override fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?> {
        // Используем flow который будет эмитить изменения записи для конкретной даты
        return habitEntryDao.getEntriesForUserAndHabit(habitId, userId).map { entries ->
            entries.find { it.date == date }?.habitEntryToDomain()
        }
    }

    override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? {
        return habitEntryDao.getEntry(habitId, userId, date)?.habitEntryToDomain()
    }

    override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> {
        return habitEntryDao.getEntriesForHabit(habitId).map { entities ->
            entities.map { it.habitEntryToDomain() }
        }
    }

    override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> {
        return habitEntryDao.getEntriesForUser(userId).map { entities ->
            entities.map { it.habitEntryToDomain() }
        }
    }

    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> {
        return habitEntryDao.getEntriesForUserAndHabit(userId, habitId).map { entities ->
            entities.map { it.habitEntryToDomain() }
        }
    }

    override suspend fun getEntriesForUserAndPeriod(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitEntry> {
        return habitEntryDao.getEntriesForUserInDateRange(userId, startDate, endDate)
            .first().filter { it.habitId == habitId }.map { it.habitEntryToDomain() }
    }

    override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser> {
        return habitEntryDao.getEntriesWithUsersForHabitAndDate(habitId, date).first().map { it.toDomain() }
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

    override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {
        val existingEntry = getEntry(habitId, userId, date)
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = true,
                completedAt = nowMillis,
                note = note,
                updatedAt = nowMillis
            )
            updateEntry(updatedEntry)
        } else {
            val newEntry = HabitEntry(
                habitId = habitId,
                userId = userId,
                date = date,
                completed = true,
                completedAt = nowMillis,
                note = note,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
            insertEntry(newEntry)
        }
    }

    override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {
        val existingEntry = getEntry(habitId, userId, date)
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = false,
                completedAt = null,
                updatedAt = nowMillis
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
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
            insertEntry(newEntry)
        }
    }
}
