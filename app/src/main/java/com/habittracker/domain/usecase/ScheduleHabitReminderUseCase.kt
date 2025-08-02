package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * Use case для планирования напоминания о привычке
 */
class ScheduleHabitReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository
) {
    /**
     * Планирует напоминание для привычки
     * @param habit Привычка для которой нужно запланировать напоминание
     */
    suspend operator fun invoke(habit: Habit) {
        if (habit.reminderEnabled && habit.reminderTime != null) {
            reminderRepository.scheduleReminder(habit)
        }
    }
}
