package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
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
    private val habitEntryRepository: HabitEntryRepository
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
        } catch (e: Exception) {
            // Если Firestore недоступен, сохраняем только локально
            habitEntryRepository.markHabitCompleted(habitId, userId, date, note)
            throw e
        }
    }
}
