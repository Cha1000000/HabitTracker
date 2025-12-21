package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.challenge.UpdateChallengeProgressUseCase
import com.habittracker.domain.usecase.gamification.CalculatePointsUseCase
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import com.habittracker.domain.util.HabitEntryIds
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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
    private val calculatePointsUseCase: CalculatePointsUseCase,
    private val checkAchievementsUseCase: CheckAchievementsUseCase,
    private val sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase,
    private val updateChallengeProgressUseCase: UpdateChallengeProgressUseCase,
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

        // 1) Локально в Room + Outbox (Repository handles both)
        habitEntryRepository.markHabitCompleted(habitId, userId, date, note)

        // 2) Экспедитим синхронизацию (форс-синк)
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

        // 4) Асинхронные побочные эффекты (очки, уведомления, челленджи)
            // Начисляем очки только если привычка НЕ была выполнена ранее
            backgroundTasksScope.launch {
                if (!wasAlreadyCompleted) {
                    updateUserPointsUseCase(userId, GamificationEvent.HabitCompleted)
                    
                    // Проверяем серию и начисляем бонус
                    try {
                        val entries = habitEntryRepository.getEntriesForUserAndHabit(userId, habitId).first()
                        val streak = calculatePointsUseCase.calculateCurrentStreak(habitId, entries, date)
                        if (streak == 3 || streak == 7 || streak == 30) {
                            updateUserPointsUseCase(userId, GamificationEvent.StreakBonus(streak))
                        }
                    } catch (e: Exception) {
                        Log.w("MarkHabitCompleted", "Streak bonus calculation failed: ${e.message}", e)
                    }
                }
                sendGroupHabitPushNotificationUseCase.invoke(habitId, userId)
                
                // Обновляем прогресс челленджей, связанных с этой привычкой
                try {
                    updateChallengeProgressUseCase(habitId, userId)
                } catch (e: Exception) {
                    Log.w("MarkHabitCompleted", "updateChallengeProgress failed: ${e.message}", e)
                }

                // Проверяем достижения
                try {
                    checkAchievementsUseCase(userId)
                } catch (e: Exception) {
                    Log.w("MarkHabitCompleted", "checkAchievements failed: ${e.message}", e)
                }
            }
    }
}

