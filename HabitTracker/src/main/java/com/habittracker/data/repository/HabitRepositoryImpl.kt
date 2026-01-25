package com.habittracker.data.repository

import com.habittracker.data.local.dao.HabitDao
import com.habittracker.data.local.dao.HabitParticipantSettingsDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val habitParticipantSettingsDao: HabitParticipantSettingsDao,
    private val firestoreRepository: FirestoreRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val applicationScope: CoroutineScope
) : HabitRepository {
    
    private var syncJob: Job? = null
    private var currentSyncUserId: String? = null

    private fun startObservingRemoteHabits(userId: String) {
        if (currentSyncUserId == userId && syncJob?.isActive == true) return
        
        syncJob?.cancel()
        currentSyncUserId = userId
        syncJob = applicationScope.launch {
            firestoreRepository.getUserHabits(userId).collect { remoteHabits ->
                // Для каждой привычки проверяем, не новее ли локальная версия
                for (remoteHabit in remoteHabits) {
                    val localHabit = habitDao.getHabitById(remoteHabit.id)
                    if (localHabit == null) {
                        // Локальной версии нет — вставляем
                        habitDao.insertHabit(remoteHabit.toEntity())
                    } else {
                        // LWW: сравниваем updatedAtMillis (fallback уже нормализован на уровне парсинга)
                        if (remoteHabit.updatedAtMillis >= localHabit.updatedAtMillis) {
                            // Дополнительно проверяем, нет ли pending операций в Outbox для этой привычки
                            val hasPendingSync = syncManager.hasPendingOperations("habit", remoteHabit.id)
                            if (!hasPendingSync) {
                                habitDao.insertHabit(remoteHabit.toEntity())
                            }
                        }
                        // Если local новее — не перезаписываем
                    }
                }
            }
        }
    }

    override suspend fun insertHabit(habit: Habit) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = habit.copy(
            updatedAtMillis = now,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(now)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date,
            deletedAtMillis = null
        )
        // 1. Local insert
        habitDao.insertHabit(updated.toEntity())
        // 2. Outbox enqueue
        val payload = SyncPayloads.habitToJson(updated)
        syncManager.enqueueUpsert(
            entityType = "habit",
            entityId = habit.id,
            payloadJson = payload,
            updatedAt = now
        )

        // 3. Expedite sync (like other write flows)
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        applicationScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    override suspend fun updateHabit(habit: Habit) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = habit.copy(
            updatedAtMillis = now,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(now)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
        // 1. Local update
        habitDao.insertHabit(updated.toEntity())
        // 2. Outbox enqueue
        val payload = SyncPayloads.habitToJson(updated)
        syncManager.enqueueUpsert(
            entityType = "habit",
            entityId = habit.id,
            payloadJson = payload,
            updatedAt = now
        )

        // 3. Expedite sync (otherwise edits may stay local until next periodic sync)
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        applicationScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    override suspend fun deleteHabit(habitId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        // 1. Local soft delete (tombstone)
        habitDao.softDeleteHabitById(habitId, deletedAtMillis = now, updatedAtMillis = now)
        // 2. Outbox enqueue (remote будет soft-delete через SyncManager.pushDelete)
        syncManager.enqueueDelete(
            entityType = "habit",
            entityId = habitId,
            updatedAt = now
        )

        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        applicationScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    override suspend fun getHabitById(habitId: String): Habit? {
        return habitDao.getHabitById(habitId)?.toDomain()
    }
    
    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
        return firestoreRepository.getHabitByInviteCode(inviteCode)
    }
    
    override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
        return habitDao.getHabitByIdFlow(habitId).map { entity ->
            entity?.toDomain()
        }
    }
    
    override fun getAllHabits(): Flow<List<Habit>> {
        return habitDao.getAllHabits().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getHabitsByUserId(userId: String): Flow<List<Habit>> {
        return habitDao.getUserHabits(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .onStart { startObservingRemoteHabits(userId) }
    }
    
    override fun getPersonalHabits(userId: String): Flow<List<Habit>> {
        return habitDao.getUserPersonalHabits(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .onStart { startObservingRemoteHabits(userId) }
    }
    
    override fun getGroupHabits(userId: String): Flow<List<Habit>> {
        return habitDao.getUserGroupHabits(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .onStart { startObservingRemoteHabits(userId) }
    }
    
    override suspend fun addParticipantToHabit(habitId: String, userId: String) {
        val localHabit = habitDao.getHabitById(habitId)
        val baseHabit = localHabit?.toDomain() ?: firestoreRepository.getHabitById(habitId)
        if (baseHabit == null) return

        if (baseHabit.participants.contains(userId)) return

        val updatedAt = Clock.System.now().toEpochMilliseconds()
        val participants = (baseHabit.participants + userId).distinct()
        val updatedHabit = baseHabit.copy(
            participants = participants,
            updatedAtMillis = updatedAt,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        )

        // Local upsert (important for invited users who don't have this habit in Room yet)
        habitDao.insertHabit(updatedHabit.toEntity())

        // Outbox update (full upsert)
        val payload = SyncPayloads.habitToJson(updatedHabit)
        syncManager.enqueueUpsert(
            entityType = "habit",
            entityId = habitId,
            payloadJson = payload,
            updatedAt = updatedAt
        )

        // Expedite sync so participants update reaches Firestore quickly
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        applicationScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {
        val localHabit = habitDao.getHabitById(habitId)
        val baseHabit = localHabit?.toDomain() ?: firestoreRepository.getHabitById(habitId)
        if (baseHabit == null) return

        if (!baseHabit.participants.contains(userId)) return

        val updatedAt = Clock.System.now().toEpochMilliseconds()
        val participants = baseHabit.participants.filterNot { it == userId }
        val updatedHabit = baseHabit.copy(
            participants = participants,
            updatedAtMillis = updatedAt,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        )

        habitDao.insertHabit(updatedHabit.toEntity())

        val payload = SyncPayloads.habitToJson(updatedHabit)
        syncManager.enqueueUpsert(
            entityType = "habit",
            entityId = habitId,
            payloadJson = payload,
            updatedAt = updatedAt
        )

        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        applicationScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    override suspend fun getHabitParticipants(habitId: String): List<String> {
        return habitDao.getHabitById(habitId)?.participants ?: emptyList()
    }
    
    override suspend fun searchHabits(userId: String, query: String): List<Habit> {
        return habitDao.searchUserHabits(userId, query)
            .map { entities -> entities.map { it.toDomain() } }
            .first()
    }
    
    override fun getPublicHabits(): Flow<List<Habit>> {
        // Получаем все публичные привычки из Firebase, исключая привычки текущего пользователя
        val currentUserId = authRepository.getCurrentUserId() 
            ?: return firestoreRepository.getAllPublicHabits() // На всякий случай, если userId null
        
        return firestoreRepository.getAllPublicHabits(excludeUserId = currentUserId)
    }
    
    // HabitParticipantSettings methods
    override suspend fun getHabitParticipantSettings(habitId: String, userId: String): HabitParticipantSettings? {
        return habitParticipantSettingsDao.get(habitId, userId)?.toDomain()
    }
    
    override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updatedSettings = settings.copy(updatedAt = now)
        habitParticipantSettingsDao.upsert(updatedSettings.toEntity())
        
        // Sync via Outbox
        // EntityId format for routing: "habitId:userId"
        val entityId = "${updatedSettings.habitId}:${updatedSettings.userId}"
        val payload = SyncPayloads.habitParticipantSettingsToJson(updatedSettings)
        syncManager.enqueueUpsert(
            entityType = "habit_participant_settings",
            entityId = entityId,
            payloadJson = payload,
            updatedAt = now
        )
    }
    
    override fun observeHabitParticipantSettings(habitId: String, userId: String): Flow<HabitParticipantSettings?> {
        return habitParticipantSettingsDao.observe(habitId, userId).map { it?.toDomain() }
    }
}
