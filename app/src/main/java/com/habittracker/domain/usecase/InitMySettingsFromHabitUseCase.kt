package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import javax.inject.Inject

class InitMySettingsFromHabitUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    suspend operator fun invoke(habit: Habit) {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")
        participantSettingsRepository.upsertDefaultsFromHabit(habit, userId)
    }
}
