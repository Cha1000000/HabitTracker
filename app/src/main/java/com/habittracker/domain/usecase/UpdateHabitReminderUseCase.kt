package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * Use case для обновления напоминания о привычке
 */
class UpdateHabitReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository
) {
    /**
     * Обновляет напоминание для привычки
     * Отменяет старое напоминание и создает новое с обновленными параметрами
     * @param habit Привычка с обновленными параметрами напоминания
     */
    suspend operator fun invoke(habit: Habit) {
        // Сначала отменяем существующее напоминание
        reminderRepository.cancelReminder(habit.id)
        
        // Затем создаем новое, если напоминания включены
        if (habit.reminderEnabled && habit.reminderTime != null) {
            reminderRepository.scheduleReminder(habit)
        }
    }
}
