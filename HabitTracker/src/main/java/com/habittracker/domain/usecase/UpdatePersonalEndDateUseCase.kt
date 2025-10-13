package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/**
 * Use case для обновления персональной даты окончания участника группы
 */
class UpdatePersonalEndDateUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(habitId: String, endDate: LocalDate?) {
        val userId = authRepository.getCurrentUserId() ?: return
        
        // Получаем текущие настройки участника
        val currentSettings = habitRepository.getHabitParticipantSettings(habitId, userId)
        
        // Обновляем только дату окончания
        val updatedSettings = currentSettings?.copy(
            personalEndDate = endDate,
            updatedAt = System.currentTimeMillis()
        ) ?: return
        
        // Сохраняем обновленные настройки
        habitRepository.updateHabitParticipantSettings(updatedSettings)
    }
}
