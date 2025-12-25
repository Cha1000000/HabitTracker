package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.model.FirebaseHabitEntry
import com.habittracker.data.model.FirebaseHabitEntry.Companion.toDomain
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
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
            Log.e("HabitEntryRepo", "Failed to enqueue sync for entry", e)
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

    override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)

            val registration = firestore.collection("habit_entries")
                .whereEqualTo("habitId", habitId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("HabitEntryRepo", "Listener error (habitId=$habitId): ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try { 
                            doc.toObject(FirebaseHabitEntry::class.java)?.toDomain()
                        } catch (e: Exception) {
                            Log.d("HabitEntryRepo", "Map doc to HabitEntry failed: ${e.message}")
                            null
                        }
                    } ?: emptyList()
                    scope.launch {
                        try {
                            val filtered = mutableListOf<HabitEntry>()
                            for (entry in list) {
                                val entityId = HabitEntryIds.make(entry.habitId, entry.userId, entry.date)
                                val hasPending = try {
                                    syncManager.hasPendingOperations("habit_entry", entityId)
                                } catch (e: Exception) {
                                    Log.w("HabitEntryRepo", "hasPendingOperations failed (habit_entry id=$entityId): ${e.message}")
                                    false
                                }
                                if (!hasPending) filtered.add(entry)
                            }
                            if (filtered.isNotEmpty()) {
                                habitEntryDao.insertEntries(filtered.map { it.habitEntryToEntity() })
                            }
                        } catch (e: Exception) {
                            Log.w("HabitEntryRepo", "Insert entries failed: ${e.message}", e)
                        }
                    }
                }

            val job = scope.launch {
                habitEntryDao.getEntriesForHabit(habitId).collect { entities ->
                    trySend(entities.map { it.habitEntryToDomain() })
                }
            }

            awaitClose {
                registration.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)

            val registration = firestore.collection("habit_entries")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("HabitEntryRepo", "Listener error (userId=$userId): ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try { doc.toObject(FirebaseHabitEntry::class.java)?.toDomain() } catch (e: Exception) {
                            Log.d("HabitEntryRepo", "Map doc to HabitEntry failed: ${e.message}")
                            null
                        }
                    } ?: emptyList()
                    scope.launch {
                        try {
                            val filtered = mutableListOf<HabitEntry>()
                            for (entry in list) {
                                val entityId = HabitEntryIds.make(entry.habitId, entry.userId, entry.date)
                                val hasPending = try {
                                    syncManager.hasPendingOperations("habit_entry", entityId)
                                } catch (e: Exception) {
                                    Log.w("HabitEntryRepo", "hasPendingOperations failed (habit_entry id=$entityId): ${e.message}")
                                    false
                                }
                                if (!hasPending) filtered.add(entry)
                            }
                            if (filtered.isNotEmpty()) {
                                habitEntryDao.insertEntries(filtered.map { it.habitEntryToEntity() })
                            }
                        } catch (e: Exception) {
                            Log.w("HabitEntryRepo", "Insert entries failed: ${e.message}", e)
                        }
                    }
                }

            val job = scope.launch {
                habitEntryDao.getEntriesForUser(userId).collect { entities ->
                    trySend(entities.map { it.habitEntryToDomain() })
                }
            }

            awaitClose {
                registration.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)

            val registration = firestore.collection("habit_entries")
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("HabitEntryRepo", "Listener error (userId=$userId, habitId=$habitId): ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try { doc.toObject(FirebaseHabitEntry::class.java)?.toDomain() } catch (e: Exception) {
                            Log.d("HabitEntryRepo", "Map doc to HabitEntry failed: ${e.message}")
                            null
                        }
                    } ?: emptyList()
                    scope.launch {
                        try {
                            val filtered = mutableListOf<HabitEntry>()
                            for (entry in list) {
                                val entityId = HabitEntryIds.make(entry.habitId, entry.userId, entry.date)
                                val hasPending = try {
                                    syncManager.hasPendingOperations("habit_entry", entityId)
                                } catch (e: Exception) {
                                    Log.w("HabitEntryRepo", "hasPendingOperations failed (habit_entry id=$entityId): ${e.message}")
                                    false
                                }
                                if (!hasPending) filtered.add(entry)
                            }
                            if (filtered.isNotEmpty()) {
                                habitEntryDao.insertEntries(filtered.map { it.habitEntryToEntity() })
                            }
                        } catch (e: Exception) {
                            Log.w("HabitEntryRepo", "Insert entries failed: ${e.message}", e)
                        }
                    }
                }

            val job = scope.launch {
                habitEntryDao.getEntriesForUserAndHabit(habitId, userId).collect { entities ->
                    trySend(entities.map { it.habitEntryToDomain() })
                }
            }

            awaitClose {
                registration.remove()
                job.cancel()
                scope.cancel()
            }
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
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        if (existingEntry != null) {
            val updatedEntry = existingEntry.copy(
                completed = true,
                completedAt = now,
                note = note,
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
}
