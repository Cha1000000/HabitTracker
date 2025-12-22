package com.habittracker.data.repository

import com.habittracker.data.local.dao.HabitDao
import com.habittracker.data.local.dao.HabitParticipantSettingsDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.data.sync.SyncManager
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.util.HabitEntryIds
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
import kotlinx.datetime.atStartOfDayIn
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
                        // Сравниваем updatedAt: вставляем только если remote новее или равен
                        val remoteUpdatedAt = remoteHabit.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault())
                        val localUpdatedAt = localHabit.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault())
                        if (remoteUpdatedAt >= localUpdatedAt) {
                            // Дополнительно проверяем, нет ли pending операций в Outbox для этой привычки
                            val hasPendingSync = syncManager.hasPendingOperations("habit", remoteHabit.id)
                            if (!hasPendingSync) {
                                habitDao.insertHabit(remoteHabit.toEntity())
                            }
                            // Если есть pending — не перезаписываем, ждём пока Outbox синхронизирует
                        }
                        // Если local новее — не перезаписываем
                    }
                }
            }
        }
    }

    override suspend fun insertHabit(habit: Habit) {
        // 1. Local insert
        habitDao.insertHabit(habit.toEntity())
        
        // 2. Outbox enqueue
        val payload = SyncPayloads.habitToJson(habit)
        syncManager.enqueueUpsert(
            entityType = "habit",
            entityId = habit.id,
            payloadJson = payload,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
    
    override suspend fun updateHabit(habit: Habit) {
        // 1. Local update
        habitDao.insertHabit(habit.toEntity())
        
        // 2. Outbox enqueue
        val payload = SyncPayloads.habitToJson(habit)
        syncManager.enqueueUpsert(
            entityType = "habit",
            entityId = habit.id,
            payloadJson = payload,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
    
    override suspend fun deleteHabit(habitId: String) {
        // 1. Local delete
        habitDao.deleteHabitById(habitId)
        
        // 2. Outbox enqueue
        syncManager.enqueueDelete(
            entityType = "habit",
            entityId = habitId,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
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
        val habit = habitDao.getHabitById(habitId)
        if (habit != null) {
            val participants = habit.participants.toMutableList()
            if (!participants.contains(userId)) {
                participants.add(userId)
                val updatedAt = Clock.System.now().toEpochMilliseconds()
                habitDao.updateHabitParticipants(habitId, participants, updatedAt)
                
                // Outbox update (partial)
                val updatedHabit = habit.toDomain().copy(participants = participants, updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date)
                // We can either enqueue full upsert or partial. SyncPayloads has habitPartialUpdateToJson but we can use full upsert for simplicity with conflict resolution
                val payload = SyncPayloads.habitToJson(updatedHabit)
                 syncManager.enqueueUpsert(
                    entityType = "habit",
                    entityId = habitId,
                    payloadJson = payload,
                    updatedAt = updatedAt
                )
            }
        }
    }
    
    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {
        val habit = habitDao.getHabitById(habitId)
        if (habit != null) {
            val participants = habit.participants.toMutableList()
            if (participants.contains(userId)) {
                participants.remove(userId)
                val updatedAt = Clock.System.now().toEpochMilliseconds()
                habitDao.updateHabitParticipants(habitId, participants, updatedAt)
                
                 // Outbox update
                val updatedHabit = habit.toDomain().copy(participants = participants, updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date)
                val payload = SyncPayloads.habitToJson(updatedHabit)
                 syncManager.enqueueUpsert(
                    entityType = "habit",
                    entityId = habitId,
                    payloadJson = payload,
                    updatedAt = updatedAt
                )
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
