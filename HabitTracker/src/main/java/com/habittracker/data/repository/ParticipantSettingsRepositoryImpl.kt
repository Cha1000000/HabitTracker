package com.habittracker.data.repository

import android.util.Log
import com.habittracker.data.local.dao.HabitParticipantSettingsDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParticipantSettingsRepositoryImpl @Inject constructor(
    private val dao: HabitParticipantSettingsDao,
    private val firestoreRepository: FirestoreRepository
) : ParticipantSettingsRepository {

    override suspend fun get(habitId: String, userId: String): HabitParticipantSettings? {
        val local = dao.get(habitId, userId)?.toDomain()
        // Всегда пытаемся получить удалённые настройки
        val remote = try { firestoreRepository.getParticipantSettings(habitId, userId) } catch (e: Exception) {
            Log.d("ParticipantSettingsRepo", "get() remote fetch failed for habit=$habitId user=$userId: ${e.message}")
            null
        }
        return when {
            local == null && remote == null -> null
            local == null && remote != null -> {
                dao.upsert(remote.toEntity()); remote
            }
            local != null && remote == null -> local
            else -> {
                // При конфликте из-за рассинхрона часов предпочитаем remote, если содержимое отличается
                val l = local!!
                val r = remote!!
                val contentDiffers = (l.reminderEnabled != r.reminderEnabled) ||
                        (l.reminderTime != r.reminderTime) ||
                        (l.reminderDays != r.reminderDays) ||
                        (l.notificationsMuted != r.notificationsMuted) ||
                        (l.timeZone != r.timeZone)
                val chooseRemote = contentDiffers || (r.updatedAt > l.updatedAt)
                val chosen = if (chooseRemote) r else l
                if (chooseRemote) {
                    dao.upsert(r.toEntity())
                }
                chosen
            }
        }
    }

    override fun observe(habitId: String, userId: String): Flow<HabitParticipantSettings?> {
        return dao.observe(habitId, userId).map { it?.toDomain() }
    }

    override suspend fun upsert(settings: HabitParticipantSettings) {
        dao.upsert(settings.toEntity())
        firestoreRepository.setParticipantSettings(settings)
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
