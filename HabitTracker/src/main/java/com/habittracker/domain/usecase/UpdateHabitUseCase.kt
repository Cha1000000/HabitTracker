package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для обновления существующей привычки.
 * Обновляет данные в Firestore для синхронизации и локально для офлайн-доступа.
 */
class UpdateHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(habit: Habit) {
        habitRepository.updateHabit(habit)
    }
}
