package com.habittracker.domain.usecase

import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для получения статуса выполнения привычки за определенную дату.
 * Приоритетно использует Firestore с локальным фоллбэком.
 */
class GetHabitCompletionStatusUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitEntryRepository: HabitEntryRepository
) {
    /**
     * Проверяет, выполнена ли привычка за указанную дату
     * @param habitId ID привычки
     * @param userId ID пользователя
     * @param date Дата (по умолчанию - сегодня)
     * @return true если привычка выполнена, false если нет
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): Boolean {
        return getEntry(habitId, userId, date)?.completed ?: false
    }

    /**
     * Получает полную запись привычки (или null, если записи нет)
     * Приоритет отдаётся локальным данным, так как они всегда актуальны после сохранения,
     * а синхронизация с Firestore происходит асинхронно.
     */
    suspend fun getEntry(
        habitId: String,
        userId: String,
        date: LocalDate
    ): com.habittracker.domain.model.HabitEntry? {
        // Приоритет локальным данным - они всегда актуальны после локального сохранения
        val localEntry = habitEntryRepository.getEntry(habitId, userId, date)
        if (localEntry != null) {
            return localEntry
        }
        
        // Если локально нет данных, пробуем получить из Firestore
        return try {
            firestoreRepository.getHabitEntry(habitId, userId, date)
        } catch (e: Exception) {
            null
        }
    }
}
