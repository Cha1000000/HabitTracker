package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import javax.inject.Inject
import timber.log.Timber

class ApplyReminderToAllParticipantsUseCase @Inject constructor(
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val participantSettingsRepository: ParticipantSettingsRepository,
    private val habitRepository: HabitRepository,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase
) {
    suspend operator fun invoke(
        habitId: String,
        reminderTime: LocalTime?,
        reminderDays: Set<DayOfWeek>,
        enabled: Boolean
    ): Result<Unit> {
        return try {
            val habit = getHabitByIdUseCase(habitId) ?: return Result.failure(IllegalArgumentException("Habit not found"))

            // Обновляем дефолты в Habit для будущих участников
            val updatedHabit: Habit = habit.copy(
                reminderTime = reminderTime,
                reminderEnabled = reminderTime != null && enabled,
                reminderDays = reminderDays
            )
            updateHabitUseCase(updatedHabit)

            // Обновляем настройки для всех существующих участников
            var participants = (updatedHabit.participants + updatedHabit.createdBy).toSet()
            if (participants.isEmpty() || (participants.size == 1 && participants.first() == updatedHabit.createdBy)) {
                // Фолбэк: добираем участников из локального репозитория, чтобы избежать гонок UI
                try {
                    val repoParticipants = habitRepository.getHabitParticipants(updatedHabit.id)
                    participants = (repoParticipants + updatedHabit.createdBy).toSet()
                } catch (e: Exception) {
                    Timber.tag("ApplyReminderAllUC").w("fallback participants failed: ${e.message}")
                }
            }
            for (userId in participants) {
                val existing = participantSettingsRepository.get(updatedHabit.id, userId)
                val merged = HabitParticipantSettings(
                    habitId = updatedHabit.id,
                    userId = userId,
                    reminderTime = reminderTime,
                    reminderEnabled = reminderTime != null && enabled,
                    reminderDays = reminderDays,
                    notificationsMuted = existing?.notificationsMuted ?: false,
                    timeZone = existing?.timeZone,
                    updatedAt = System.currentTimeMillis()
                )
                participantSettingsRepository.upsert(merged)
            }

            // На текущем устройстве — перепланируем сразу
            try {
                scheduleHabitReminderUseCase(updatedHabit)
            } catch (e: Exception) {
                Timber.tag("ApplyReminderAllUC").w("schedule failed locally: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
