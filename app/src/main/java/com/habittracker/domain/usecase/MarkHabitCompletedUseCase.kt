package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository,
    private val sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase
) {
    /**
     * Отмечает привычку как выполненную за указанную дату
     * @param habitId ID привычки
     * @param userId ID пользователя
     * @param date Дата выполнения (по умолчанию - сегодня)
     * @param note Заметка о выполнении (опционально)
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        note: String? = null
    ) {
        val habitEntry = HabitEntry(
            habitId = habitId,
            userId = userId,
            date = date,
            completed = true,
            completedAt = Clock.System.now(),
            note = note,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        try {
            // Сначала сохраняем в Firestore
            firestoreRepository.saveHabitEntry(habitEntry)
            
            // Затем сохраняем локально
            habitEntryRepository.markHabitCompleted(habitId, userId, date, note)
            
            // Отправляем push-уведомления для групповых привычек
            sendGroupNotificationsAsync(habitId, userId)
            
        } catch (e: Exception) {
            // При ошибке Firestore всё равно сохраняем локально
            habitEntryRepository.markHabitCompleted(habitId, userId, date, note)
            throw e
        }
    }

    /**
     * Асинхронно отправляет push-уведомления для групповых привычек
     */
    private fun sendGroupNotificationsAsync(habitId: String, userId: String) {
        notificationScope.launch {
            try {
                val habit = habitRepository.getHabitById(habitId)
                if (habit != null && habit.isGroupHabit()) {
                    val user = socialRepository.getUserById(userId)
                    if (user != null) {
                        // Уведомляем других участников о выполнении привычки
                        sendGroupHabitPushNotificationUseCase.sendParticipantCompletedNotification(
                            habit = habit,
                            completedByUser = user,
                            excludeUserId = userId // Исключаем самого пользователя
                        )
                    }
                }
            } catch (e: Exception) {
                // Логируем ошибку, но не прерываем основной процесс
                // TODO: Добавить логирование
            }
        }
    }

    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
