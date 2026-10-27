package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.repository.HabitEntryRepository
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для получения статуса выполнения привычки за определенную дату.
 * Читает только Room: отметки с других устройств туда кладёт RemoteDataSync.
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
        return getEntry(habitId, userId, date)?.completed ?: false
    }

    /** Полная запись привычки за дату или null, если отметки нет. */
    suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? =
        habitEntryRepository.getEntry(habitId, userId, date)
}
