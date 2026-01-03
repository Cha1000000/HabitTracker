package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/**
 * Use case для установки/снятия отметки "выходной" для привычки
 */
class SetHabitRestDayUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        habitId: String,
        date: LocalDate,
        isRestDay: Boolean
    ) {
        val userId = authRepository.getCurrentUserId() ?: throw IllegalStateException("User not authenticated")
        habitEntryRepository.setRestDay(habitId, userId, date, isRestDay)
    }
}
