package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case для отметки привычки как невыполненной.
 * Room-first: локальная запись в Room, затем постановка UPSERT в Outbox и триггер one-time sync.
 */
class MarkHabitIncompleteUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    coroutineScope: CoroutineScope
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
        // Проверяем, была ли привычка выполнена в этот день
        val existingEntry = habitEntryRepository.getEntry(habitId, userId, date)
        val wasCompleted = existingEntry?.completed == true

        // 1) Локально в Room — всегда сначала (Repository handles Outbox internally)
        habitEntryRepository.markHabitIncomplete(habitId, userId, date)

        // 2) Экспедитим синхронизацию (форс-синк)
        try {
            syncWorkScheduler.scheduleOneTimeSync()
        } catch (e: Exception) {
            Timber.tag("MarkHabitIncomplete").w(e, "scheduleOneTimeSync failed: ${e.message}")
        }
        
        // Немедленный неблокирующий дренаж outbox для ускорения синка
        backgroundTasksScope.launch {
            try { syncManager.drainPending() } catch (e: Exception) {
                Timber.tag("MarkHabitIncomplete").d("drainPending failed (non-fatal): ${e.message}")
            }
        }
        
        // 3) Вычитаем очки, если привычка была выполнена
        if (wasCompleted) {
            backgroundTasksScope.launch {
                updateUserPointsUseCase(userId, GamificationEvent.HabitUncompleted)
            }
        }
    }
}


