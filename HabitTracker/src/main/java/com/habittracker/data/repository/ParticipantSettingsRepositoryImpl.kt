package com.habittracker.data.repository

import com.habittracker.data.local.dao.HabitParticipantSettingsDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.data.sync.SyncManager
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ParticipantSettingsRepositoryImpl @Inject constructor(
    private val dao: HabitParticipantSettingsDao,
    private val firestoreRepository: FirestoreRepository,
    private val syncManager: SyncManager
) : ParticipantSettingsRepository {

    /** Привычки, у которых в облаке нет настроек; появившиеся позже принесёт RemoteDataSync. */
    private val missingRemote: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun get(habitId: String, userId: String): HabitParticipantSettings? {
        // Правки с других устройств приносит в Room RemoteDataSync; в сеть идём, только если записи ещё нет
        dao.get(habitId, userId)?.let { return it.toDomain() }
        // Чтение отсутствующего документа тоже платное, а экран спрашивает при каждой смене даты
        val key = "$habitId:$userId"
        if (key in missingRemote) return null
        val remote = try {
            firestoreRepository.getParticipantSettings(habitId, userId)
                .also { if (it == null) missingRemote += key }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("ParticipantSettingsRepo").d("get() remote fetch failed for habit=$habitId user=$userId: ${e.message}")
            null
        } ?: return null
        dao.upsert(remote.toEntity())
        return remote
    }

    override fun observe(habitId: String, userId: String): Flow<HabitParticipantSettings?> {
        return dao.observe(habitId, userId).map { it?.toDomain() }
    }

    override suspend fun upsert(settings: HabitParticipantSettings) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updatedSettings = settings.copy(updatedAt = now)
        dao.upsert(updatedSettings.toEntity())
        
        // Outbox sync
        val entityId = "${updatedSettings.habitId}:${updatedSettings.userId}"
        val payload = SyncPayloads.habitParticipantSettingsToJson(updatedSettings)
        syncManager.enqueueUpsert(
            entityType = "habit_participant_settings",
            entityId = entityId,
            payloadJson = payload,
            updatedAt = now
        )
    }

    override suspend fun upsertDefaultsFromHabit(habit: Habit, userId: String): HabitParticipantSettings {
        val existing = dao.get(habit.id, userId)?.toDomain()
        if (existing == null) {
            val settings = HabitParticipantSettings(
                habitId = habit.id,
                userId = userId,
                reminderTime = habit.reminderTime,
                reminderEnabled = habit.reminderEnabled,
                reminderDays = habit.reminderDays,
                notificationsMuted = false,
                timeZone = null,
                updatedAt = System.currentTimeMillis()
            )
            upsert(settings)
            return settings
        }
        // Если существующие настройки есть, но reminderTime отсутствует, а у привычки есть дефолт — дольём дефолт
        return if (existing.reminderTime == null && habit.reminderTime != null) {
            val merged = existing.copy(
                reminderTime = habit.reminderTime,
                reminderEnabled = habit.reminderEnabled,
                reminderDays = habit.reminderDays,
                updatedAt = System.currentTimeMillis()
            )
            upsert(merged)
            merged
        } else existing
    }

    override suspend fun deleteForHabit(habitId: String) {
        dao.deleteForHabit(habitId)
        // Optionally delete remote documents per participant — omitted for simplicity
    }

    override suspend fun deleteForHabitAndUser(habitId: String, userId: String) {
        dao.deleteForHabitAndUser(habitId, userId)
        // Optionally delete remote documents — omitted for simplicity
    }
}
