package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
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
 * Use case для отметки привычки как выполненной.
 * Room-first синхронизация с Outbox: локальная запись в Room, затем enqueue UPSERT и форсированный one-time sync.
 */
class MarkHabitCompletedUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase,
    private val coroutineScope: CoroutineScope,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler
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
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val entry = HabitEntry(
            habitId = habitId,
            userId = userId,
            date = date,
            completed = true,
            completedAt = nowMillis,
            note = note,
            createdAt = nowMillis,
            updatedAt = nowMillis
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
                updatedAt = nowMillis
            )
        } catch (_: Exception) {
            // Игнорируем: запись останется локально, воркер позже повторит
        } finally {
            // Экспедитим синхронизацию
            syncWorkScheduler.scheduleOneTimeSync()

            // 3) Немедленный неблокирующий дренаж outbox для ускорения синка
            backgroundTasksScope.launch {
                try { syncManager.drainPending() } catch (_: Exception) { }
            }

            // 4) Асинхронные побочные эффекты (очки, уведомления)
            backgroundTasksScope.launch {
                updateUserPointsUseCase(userId, GamificationEvent.HabitCompleted)
                sendGroupHabitPushNotificationUseCase.invoke(habitId, userId)
            }
        }
    }
}

