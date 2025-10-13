package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import com.habittracker.domain.util.HabitEntryIds
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для отметки привычки как выполненной.
 * Room-first синхронизация с Outbox: локальная запись в Room, затем enqueue UPSERT и форсированный one-time sync.
 */
class MarkHabitCompletedUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    coroutineScope: CoroutineScope,
) {

    private val backgroundTasksScope = coroutineScope

    /**
     * Отмечает привычку как выполненную для определенной даты.
     * Room-first: сначала локальная запись, затем постановка UPSERT в Outbox и триггер one-time sync.
     * Начисляет очки за выполнение и отправляет уведомления для групповых привычек.
     *
     * @param habitId ID привычки.
     * @param userId ID пользователя.
     * @param date Дата выполнения.
     * @param note Заметка к выполнению (опционально).
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        note: String? = null
    ) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date
        
        // Проверяем, была ли привычка уже выполнена в этот день
        val existingEntry = habitEntryRepository.getEntry(habitId, userId, date)
        val wasAlreadyCompleted = existingEntry?.completed == true
        
        val entry = HabitEntry(
            habitId = habitId,
            userId = userId,
            date = date,
            completed = true,
            completedAt = now,
            note = note,
            createdAt = today,
            updatedAt = today
        )

        // 1) Локально в Room — всегда сначала
        habitEntryRepository.markHabitCompleted(habitId, userId, date, note)

        // 2) Outbox UPSERT + форс-синк (ошибки не ломают локальный UX)
        val entityId = HabitEntryIds.make(habitId, userId, date)
        val payloadJson = SyncPayloads.habitEntryToJson(entry)
        try {
            syncManager.enqueueUpsert(
                entityType = "habit_entry",
                entityId = entityId,
                payloadJson = payloadJson,
                updatedAt = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )
        } catch (e: Exception) {
            // Игнорируем критично для UI, но логируем для диагностики: запись останется локально, воркер позже повторит
            Log.w("MarkHabitCompleted", "enqueueUpsert failed: ${e.message}", e)
        } finally {
            // Экспедитим синхронизацию
            try {
                syncWorkScheduler.scheduleOneTimeSync()
            } catch (e: Exception) {
                Log.w("MarkHabitCompleted", "scheduleOneTimeSync failed: ${e.message}", e)
            }

            // 3) Немедленный неблокирующий дренаж outbox для ускорения синка
            backgroundTasksScope.launch {
                try { syncManager.drainPending() } catch (e: Exception) {
                    Log.d("MarkHabitCompleted", "drainPending failed (non-fatal): ${e.message}")
                }
            }

            // 4) Асинхронные побочные эффекты (очки, уведомления)
            // Начисляем очки только если привычка НЕ была выполнена ранее
            backgroundTasksScope.launch {
                if (!wasAlreadyCompleted) {
                    updateUserPointsUseCase(userId, GamificationEvent.HabitCompleted)
                }
                sendGroupHabitPushNotificationUseCase.invoke(habitId, userId)
            }
        }
    }
}

