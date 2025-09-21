package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * Use case для планирования напоминания о привычке
 */
class ScheduleHabitReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val authRepository: AuthRepository,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    /**
     * Планирует напоминание для привычки
     * @param habit Привычка для которой нужно запланировать напоминание
     */
    suspend operator fun invoke(habit: Habit) {
        val userId = authRepository.getCurrentUserId()
        val merged = if (userId != null) {
            val settings = participantSettingsRepository.get(habit.id, userId)
            if (settings != null) {
                habit.copy(
                    reminderTime = settings.reminderTime,
                    reminderEnabled = settings.reminderEnabled,
                    reminderDays = settings.reminderDays
                )
            } else habit
        } else habit

        if (merged.reminderTime != null) {
            reminderRepository.scheduleReminder(merged)
        }
    }
}
