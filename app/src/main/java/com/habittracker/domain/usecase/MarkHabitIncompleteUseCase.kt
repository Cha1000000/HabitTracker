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
 * Use case для отметки привычки как невыполненной.
 * Сохраняет запись в Firestore для синхронизации и локально для офлайн-доступа.
 */
class MarkHabitIncompleteUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitEntryRepository: HabitEntryRepository
) {
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
        val habitEntry = HabitEntry(
            habitId = habitId,
            userId = userId,
            date = date,
            completed = false,
            completedAt = null,
            note = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )

        try {
            // Сначала сохраняем в Firestore
            firestoreRepository.saveHabitEntry(habitEntry)
            
            // Затем сохраняем локально
            habitEntryRepository.markHabitIncomplete(habitId, userId, date)
        } catch (e: Exception) {
            // Если Firestore недоступен, сохраняем только локально
            habitEntryRepository.markHabitIncomplete(habitId, userId, date)
            throw e
        }
    }
}
