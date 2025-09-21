package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.util.SyncPayloads
import com.habittracker.domain.util.HabitEntryIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для отметки привычки как невыполненной.
 * Room-first: локальная запись в Room, затем постановка UPSERT в Outbox и триггер one-time sync.
 */
class MarkHabitIncompleteUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val coroutineScope: CoroutineScope
) {
    private val backgroundTasksScope = coroutineScope
    /**
     * Отмечает привычку как невыполненную за указанную дату
     * @param habitId ID привычки
     * @param userId ID пользователя
     * @param date Дата (по умолчанию - сегодня)
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ) {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val habitEntry = HabitEntry(
            habitId = habitId,
            userId = userId,
            date = date,
            completed = false,
            completedAt = null,
            note = null,
            createdAt = nowMillis,
            updatedAt = nowMillis
        )

        // 1) Локально в Room — всегда сначала
        habitEntryRepository.markHabitIncomplete(habitId, userId, date)

        // 2) Outbox UPSERT + форс-синк
        val entityId = HabitEntryIds.make(habitId, userId, date)
        val payloadJson = SyncPayloads.habitEntryToJson(habitEntry)

        try {
            syncManager.enqueueUpsert(
                entityType = "habit_entry",
                entityId = entityId,
                payloadJson = payloadJson,
                updatedAt = nowMillis
            )
        } catch (e: Exception) {
            // Не критично для UI, но логируем, чтобы не терять причину
            Log.w("MarkHabitIncomplete", "enqueueUpsert failed: ${e.message}", e)
        } finally {
            try {
                syncWorkScheduler.scheduleOneTimeSync()
            } catch (e: Exception) {
                Log.w("MarkHabitIncomplete", "scheduleOneTimeSync failed: ${e.message}", e)
            }
            // Немедленный неблокирующий дренаж outbox для ускорения синка
            backgroundTasksScope.launch {
                try { syncManager.drainPending() } catch (e: Exception) {
                    Log.d("MarkHabitIncomplete", "drainPending failed (non-fatal): ${e.message}")
                }
            }
        }
    }
}


