package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.util.HabitEntryIds
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.local.mappers.toDomain as habitEntryToDomain
import com.habittracker.data.local.mappers.toEntity as habitEntryToEntity

@Singleton
class HabitEntryRepositoryImpl @Inject constructor(
    private val habitEntryDao: HabitEntryDao,
    private val firestore: FirebaseFirestore,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler
) : HabitEntryRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        repoScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }

    override suspend fun getEntriesForUserAndHabitOnce(userId: String, habitId: String): List<HabitEntry> {
        return habitEntryDao.getEntriesForUserAndHabitOnce(habitId, userId).map { it.habitEntryToDomain() }
    }

    override suspend fun deleteEntriesForUserAndHabit(userId: String, habitId: String) {
        val entries = habitEntryDao.getEntriesForUserAndHabitOnce(habitId, userId)
        if (entries.isEmpty()) return

        habitEntryDao.deleteEntriesForUserAndHabit(habitId, userId)

        val updatedAt = Clock.System.now().toEpochMilliseconds()
        for (e in entries) {
            try {
                val entityId = HabitEntryIds.make(e.habitId, e.userId, e.date)
                syncManager.enqueueDelete(
                    entityType = "habit_entry",
                    entityId = entityId,
                    updatedAt = updatedAt
                )
            } catch (ex: Exception) {
                Timber.tag("HabitEntryRepo").w(ex, "Failed to enqueue delete for habit_entry")
            }
        }
        kickSync()
    }

    override suspend fun deleteEntriesForHabit(habitId: String) {
        val entries = habitEntryDao.getEntriesForHabitOnce(habitId)
        if (entries.isEmpty()) return

        habitEntryDao.deleteAllEntriesForHabit(habitId)

        val updatedAt = Clock.System.now().toEpochMilliseconds()
        for (e in entries) {
            try {
                val entityId = HabitEntryIds.make(e.habitId, e.userId, e.date)
                syncManager.enqueueDelete(
                    entityType = "habit_entry",
                    entityId = entityId,
                    updatedAt = updatedAt
                )
            } catch (ex: Exception) {
                Timber.tag("HabitEntryRepo").w(ex, "Failed to enqueue delete for habit_entry")
            }
        }
        kickSync()
    }

    override suspend fun insertEntry(entry: HabitEntry) {
        habitEntryDao.insertEntry(entry.habitEntryToEntity())
        enqueueSync(entry)
    }

    override suspend fun updateEntry(entry: HabitEntry) {
        habitEntryDao.updateEntry(entry.habitEntryToEntity())
        enqueueSync(entry)
    }
    
    private suspend fun enqueueSync(entry: HabitEntry) {
        try {
            val id = HabitEntryIds.make(entry.habitId, entry.userId, entry.date)
            val payload = SyncPayloads.habitEntryToJson(entry)
            // Используем текущее время для updatedAt, так как у entry точность только до дня
            val updatedAt = Clock.System.now().toEpochMilliseconds()
            
            syncManager.enqueueUpsert(
                entityType = "habit_entry",
                entityId = id,
                payloadJson = payload,
                updatedAt = updatedAt
            )

            kickSync()
        } catch (e: Exception) {
            Timber.tag("HabitEntryRepo").e(e, "Failed to enqueue sync for entry")
        }
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

    // Отметки из облака кладёт в Room RemoteDataSync, здесь только чтение Room

    override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> =
        habitEntryDao.getEntriesForHabit(habitId).map { entities -> entities.map { it.habitEntryToDomain() } }

    override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> =
        habitEntryDao.getEntriesForUser(userId).map { entities -> entities.map { it.habitEntryToDomain() } }

    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> =
        habitEntryDao.getEntriesForUserAndHabit(habitId, userId).map { entities -> entities.map { it.habitEntryToDomain() } }

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
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = true,
                completedAt = now,
                note = note,
                isRestDay = false,
                updatedAt = today
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
                createdAt = today,
                updatedAt = today
            )
            insertEntry(newEntry)
        }
    }

    override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {
        val existingEntry = getEntry(habitId, userId, date)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = false,
                completedAt = null,
                updatedAt = today
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
                createdAt = today,
                updatedAt = today
            )
            insertEntry(newEntry)
        }
    }

    override suspend fun setRestDay(habitId: String, userId: String, date: LocalDate, isRestDay: Boolean) {
        val existingEntry = getEntry(habitId, userId, date)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        if (existingEntry != null) {
            // Если включаем выходной, то сбрасываем выполнение
            val newCompleted = if (isRestDay) false else existingEntry.completed
            val newCompletedAt = if (isRestDay) null else existingEntry.completedAt
            
            val updatedEntry = existingEntry.copy(
                isRestDay = isRestDay,
                completed = newCompleted,
                completedAt = newCompletedAt,
                updatedAt = today
            )
            updateEntry(updatedEntry)
        } else {
            val newEntry = HabitEntry(
                habitId = habitId,
                userId = userId,
                date = date,
                completed = false,
                isRestDay = isRestDay,
                createdAt = today,
                updatedAt = today
            )
            insertEntry(newEntry)
        }
    }

    override fun getDailyCompletionCounts(userId: String): Flow<Map<LocalDate, Int>> {
        return habitEntryDao.getDailyCompletionCounts(userId)
            .map { list ->
                list.associate { it.date to it.count }
            }
    }

    override suspend fun updatePhotoUrl(habitId: String, userId: String, date: LocalDate, photoUrl: String?) {
        // Обновляем локальную БД
        habitEntryDao.updatePhotoUrl(habitId, userId, date, photoUrl)
        
        // Напрямую обновляем в Firestore
        try {
            val docId = HabitEntryIds.make(habitId, userId, date)
            val updates = mapOf(
                "photoUrl" to photoUrl,
                "updatedAt" to date.toString()
            )
            firestore.collection("habit_entries")
                .document(docId)
                .update(updates)
                .addOnSuccessListener {
                    Timber.tag("HabitEntryRepo").d("Photo URL synced to Firestore: habitId=$habitId, date=$date")
                }
                .addOnFailureListener { e ->
                    Timber.tag("HabitEntryRepo").e(e, "Failed to sync photo URL to Firestore")
                }
        } catch (e: Exception) {
            Timber.tag("HabitEntryRepo").e(e, "Error updating photo URL in Firestore")
        }
        
        Timber.tag("HabitEntryRepo").d("Photo URL updated locally: habitId=$habitId, date=$date, photoUrl=$photoUrl")
    }

    override suspend fun getEntryOnce(habitId: String, userId: String, date: LocalDate): HabitEntry? {
        return habitEntryDao.getEntry(habitId, userId, date)?.toDomain()
    }
}
