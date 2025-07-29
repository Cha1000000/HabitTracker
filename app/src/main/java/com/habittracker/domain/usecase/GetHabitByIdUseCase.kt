package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для получения одной привычки по ее идентификатору.
 */
class GetHabitByIdUseCase @Inject constructor(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(id: String): Habit? {
        return repository.getHabitById(id)
    }
}
