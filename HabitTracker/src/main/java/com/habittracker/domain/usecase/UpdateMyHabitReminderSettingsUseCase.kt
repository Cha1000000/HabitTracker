package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import javax.inject.Inject

class UpdateMyHabitReminderSettingsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val participantSettingsRepository: ParticipantSettingsRepository,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase
) {
    suspend operator fun invoke(settings: HabitParticipantSettings) {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")
        if (settings.userId != userId) {
            throw IllegalArgumentException("Settings.userId must equal current userId")
        }
        participantSettingsRepository.upsert(settings.copy(updatedAt = System.currentTimeMillis()))
        // Перепланировать напоминание с учётом новых персональных настроек
        // Для этого нужен сам Habit — в schedule use case он используется, но мы можем передать заглушку с id
        // Однако schedule требует Habit со многими полями; вызываем его на стороне, где есть Habit.
        // Здесь просто обновим настройки; планирование вызывается там, где изменяют настройки UI.
    }
}
