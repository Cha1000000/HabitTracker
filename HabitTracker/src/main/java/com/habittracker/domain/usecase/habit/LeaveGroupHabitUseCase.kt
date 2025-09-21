package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для выхода из групповой привычки
 */
class LeaveGroupHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    /**
     * Удаляет пользователя из участников групповой привычки
     * @param habitId ID привычки
     * @param userId ID пользователя, покидающего привычку
     * @return Result с результатом операции
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String
    ): Result<Unit> {
        return try {
            // Получаем привычку для проверки
            val habit = habitRepository.getHabitById(habitId)
                ?: return Result.failure(Exception("Привычка не найдена"))

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                return Result.failure(Exception("Можно покинуть только групповую привычку"))
            }

            // Проверяем, что пользователь является участником (но не создателем)
            if (habit.createdBy == userId) {
                return Result.failure(Exception("Создатель не может покинуть привычку. Удалите привычку полностью."))
            }

            if (!habit.isUserParticipant(userId)) {
                return Result.failure(Exception("Пользователь не является участником этой привычки"))
            }

            // Удаляем пользователя из участников
            habitRepository.removeParticipantFromHabit(habitId, userId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
