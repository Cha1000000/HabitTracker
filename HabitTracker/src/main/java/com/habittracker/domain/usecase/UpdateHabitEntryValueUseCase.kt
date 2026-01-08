package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.usecase.gamification.CalculatePointsUseCase
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import com.habittracker.domain.usecase.challenge.UpdateChallengeProgressUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для обновления значения выполнения привычки (для измеримых привычек).
 */
class UpdateHabitEntryValueUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val habitRepository: HabitRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val calculatePointsUseCase: CalculatePointsUseCase,
    private val checkAchievementsUseCase: CheckAchievementsUseCase,
    private val sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase,
    private val updateChallengeProgressUseCase: UpdateChallengeProgressUseCase,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val coroutineScope: CoroutineScope
) {
    private val backgroundTasksScope = coroutineScope

    /**
     * Обновляет значение выполнения привычки.
     * Если новое значение >= целевого значения привычки, отмечает её как выполненную.
     * Если меньше - как невыполненную (но сохраняет прогресс).
     *
     * @param habitId ID привычки
     * @param userId ID пользователя
     * @param value Новое значение
     * @param date Дата выполнения
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String,
        value: Int,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        // Получаем привычку для проверки целевого значения
        val habit = habitRepository.getHabitById(habitId) ?: return
        val targetValue = habit.targetValue
        val isCompleted = value >= targetValue

        // Получаем существующую запись
        val existingEntry = habitEntryRepository.getEntry(habitId, userId, date)
        val wasAlreadyCompleted = existingEntry?.completed == true

        // Создаем или обновляем запись
        val entryToSave = if (existingEntry != null) {
            existingEntry.copy(
                value = value,
                completed = isCompleted,
                completedAt = if (isCompleted && existingEntry.completedAt == null) now else existingEntry.completedAt,
                updatedAt = today
            )
        } else {
            HabitEntry(
                habitId = habitId,
                userId = userId,
                date = date,
                completed = isCompleted,
                completedAt = if (isCompleted) now else null,
                value = value,
                createdAt = today,
                updatedAt = today
            )
        }

        // Сохраняем локально (Repository handles Outbox internally if we use updateEntry/insertEntry)
        // Но здесь мы используем updateEntry/insertEntry напрямую, которые уже должны быть в HabitEntryRepository
        if (existingEntry != null) {
            habitEntryRepository.updateEntry(entryToSave)
        } else {
            habitEntryRepository.insertEntry(entryToSave)
        }

        // Экспедитим синхронизацию
        try {
            syncWorkScheduler.scheduleOneTimeSync()
        } catch (e: Exception) {
            Log.w("UpdateHabitValue", "scheduleOneTimeSync failed", e)
        }

        backgroundTasksScope.launch {
            try { syncManager.drainPending() } catch (e: Exception) {
                Log.d("UpdateHabitValue", "drainPending failed", e)
            }
        }

        // Побочные эффекты (очки, уведомления, челленджи)
        backgroundTasksScope.launch {
            // Если привычка стала выполненной (а раньше не была)
            if (isCompleted && !wasAlreadyCompleted) {
                updateUserPointsUseCase(userId, GamificationEvent.HabitCompleted)

                // Проверяем серию
                try {
                    val entries = habitEntryRepository.getEntriesForUserAndHabit(userId, habitId).first()
                    val streak = calculatePointsUseCase.calculateCurrentStreak(habitId, entries, date)
                    if (streak == 3 || streak == 7 || streak == 30) {
                        updateUserPointsUseCase(userId, GamificationEvent.StreakBonus(streak))
                    }
                } catch (e: Exception) {
                    Log.w("UpdateHabitValue", "Streak calc failed", e)
                }

                sendGroupHabitPushNotificationUseCase(habitId, userId)
            } 
            // Если привычка перестала быть выполненной (например, уменьшили значение)
            else if (!isCompleted && wasAlreadyCompleted) {
                updateUserPointsUseCase(userId, GamificationEvent.HabitUncompleted)
            }

            // Обновляем прогресс челленджей (значение могло измениться, даже если статус выполнения не поменялся)
            try {
                updateChallengeProgressUseCase(habitId, userId)
            } catch (e: Exception) {
                Log.w("UpdateHabitValue", "updateChallengeProgress failed", e)
            }

            // Проверяем достижения
            try {
                checkAchievementsUseCase(userId)
            } catch (e: Exception) {
                Log.w("UpdateHabitValue", "checkAchievements failed", e)
            }
        }
    }
}
