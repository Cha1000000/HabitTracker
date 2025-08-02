package com.habittracker.domain.usecase

import com.habittracker.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * Use case для отмены напоминания о привычке
 */
class CancelHabitReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository
) {
    /**
     * Отменяет напоминание для привычки
     * @param habitId ID привычки для которой нужно отменить напоминание
     */
    suspend operator fun invoke(habitId: String) {
        reminderRepository.cancelReminder(habitId)
    }
}
