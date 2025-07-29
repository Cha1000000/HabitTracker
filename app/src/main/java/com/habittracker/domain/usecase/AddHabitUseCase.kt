package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для добавления новой привычки.
 * Инкапсулирует бизнес-логику, связанную с созданием привычки.
 */
class AddHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    /**
     * Выполняет основное действие use case.
     * @param habit Привычка для добавления.
     */
    suspend operator fun invoke(habit: Habit) {
        // Здесь можно добавить дополнительную логику перед сохранением,
        // например, валидацию или установку значений по умолчанию.
        habitRepository.insertHabit(habit)
    }
}
