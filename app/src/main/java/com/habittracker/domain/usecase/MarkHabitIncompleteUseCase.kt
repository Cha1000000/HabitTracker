package com.habittracker.domain.usecase

import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для отметки привычки как невыполненной
 */
class MarkHabitIncompleteUseCase @Inject constructor(
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
        habitEntryRepository.markHabitIncomplete(habitId, userId, date)
    }
}
