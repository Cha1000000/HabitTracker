package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для отметки привычки как выполненной.
 * Сохраняет запись в Firestore для синхронизации и локально для офлайн-доступа.
 */
class MarkHabitCompletedUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase,
    private val coroutineScope: CoroutineScope
) {

    private val backgroundTasksScope = coroutineScope

    /**
     * Отмечает привычку как выполненную для определенной даты.
     * Сохраняет запись о выполнении в Firestore и локальной базе данных.
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
        try {
            val now = Clock.System.now()
            val entry = HabitEntry(
                habitId = habitId,
                userId = userId,
                date = date,
                completed = true,
                completedAt = now,
                note = note,
                createdAt = now,
                updatedAt = now
            )
            // Сначала пробуем сохранить в Firestore
            firestoreRepository.saveHabitEntry(entry)
            // Если успешно, сохраняем в локальную базу
            habitEntryRepository.markHabitCompleted(habitId, userId, date, note)

            // Асинхронно обновляем очки и отправляем уведомления
            backgroundTasksScope.launch {
                updateUserPointsUseCase(userId, GamificationEvent.HabitCompleted)
                sendGroupHabitPushNotificationUseCase.invoke(habitId, userId)
            }

        } catch (e: Exception) {
            // Если Firestore недоступен, сохраняем только локально
            habitEntryRepository.markHabitCompleted(habitId, userId, date, note)
            // Также начисляем очки даже при ошибке сети
            backgroundTasksScope.launch { updateUserPointsUseCase(userId, GamificationEvent.HabitCompleted) }
            throw e // Пробрасываем исключение дальше, чтобы вызывающий код мог его обработать
        }
    }
}
