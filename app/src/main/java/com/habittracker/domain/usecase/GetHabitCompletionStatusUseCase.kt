package com.habittracker.domain.usecase

import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для получения статуса выполнения привычки за определенную дату
 */
class GetHabitCompletionStatusUseCase @Inject constructor(
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
        val entry = habitEntryRepository.getEntry(habitId, userId, date)
        return entry?.completed ?: false
    }
}
