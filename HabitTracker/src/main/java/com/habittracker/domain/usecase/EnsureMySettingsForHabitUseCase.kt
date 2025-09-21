package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import javax.inject.Inject

class EnsureMySettingsForHabitUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    suspend operator fun invoke(habit: Habit) {
        val userId = authRepository.getCurrentUserId() ?: return
        val existing = participantSettingsRepository.get(habit.id, userId)
        if (existing == null) {
            participantSettingsRepository.upsertDefaultsFromHabit(habit, userId)
        } else if (existing.reminderTime == null && habit.reminderTime != null) {
            // Если у личных настроек нет времени, а у привычки есть дефолт — обновим личные по дефолту
            participantSettingsRepository.upsertDefaultsFromHabit(habit, userId)
        }
    }
}
