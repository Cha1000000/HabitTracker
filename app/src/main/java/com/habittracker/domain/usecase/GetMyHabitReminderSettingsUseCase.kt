package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMyHabitReminderSettingsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    operator fun invoke(habitId: String): Flow<HabitParticipantSettings?> {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")
        return participantSettingsRepository.observe(habitId, userId)
    }

    // Одноразовое получение с приоритетом свежих данных (через repository.get)
    suspend fun getOnce(habitId: String): HabitParticipantSettings? {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")
        return participantSettingsRepository.get(habitId, userId)
    }
}
