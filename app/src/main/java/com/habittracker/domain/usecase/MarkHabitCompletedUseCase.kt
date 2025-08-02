package com.habittracker.domain.usecase

import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для отметки привычки как выполненной
 */
class MarkHabitCompletedUseCase @Inject constructor(
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
        habitEntryRepository.markHabitCompleted(habitId, userId, date, note)
    }
}
