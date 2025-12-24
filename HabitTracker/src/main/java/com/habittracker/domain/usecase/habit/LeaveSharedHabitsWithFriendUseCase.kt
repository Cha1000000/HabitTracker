package com.habittracker.domain.usecase.habit

import android.util.Log
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для массового выхода из всех совместных привычек с конкретным другом.
 * Используется при удалении друга с опцией выхода из общих привычек.
 */
class LeaveSharedHabitsWithFriendUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    companion object {
        private const val TAG = "LeaveSharedHabitsUC"
    }

    /**
     * Выходит из всех групповых привычек, где участвуют оба пользователя.
     * Не затрагивает привычки, созданные текущим пользователем.
     * 
     * @param currentUserId ID текущего пользователя
     * @param friendId ID друга, с которым разрываем связь
     * @return Result с количеством привычек, из которых вышли
     */
    suspend operator fun invoke(
        currentUserId: String,
        friendId: String
    ): Result<Int> {
        return try {
            Log.d(TAG, "Starting bulk leave from shared habits: currentUser=$currentUserId, friend=$friendId")
            
            // Получаем все групповые привычки текущего пользователя
            val userHabits = habitRepository.getHabitsByUserId(currentUserId)
                .let { flow ->
                    var habits = emptyList<com.habittracker.domain.model.Habit>()
                    flow.collect { habits = it }
                    habits
                }
            
            var leftCount = 0
            
            for (habit in userHabits) {
                // Пропускаем не групповые привычки
                if (!habit.isGroupHabit()) continue
                
                // Пропускаем привычки, где друг не участвует
                if (!habit.isUserParticipant(friendId)) continue
                
                // Пропускаем привычки, созданные текущим пользователем
                // (он не может "выйти" из своей привычки)
                if (habit.createdBy == currentUserId) {
                    Log.d(TAG, "Skipping habit ${habit.id} - created by current user")
                    continue
                }
                
                // Выходим из привычки
                try {
                    habitRepository.removeParticipantFromHabit(habit.id, currentUserId)
                    leftCount++
                    Log.d(TAG, "Left habit: ${habit.title} (${habit.id})")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to leave habit ${habit.id}: ${e.message}")
                }
            }
            
            Log.d(TAG, "Bulk leave completed: left $leftCount habits")
            Result.success(leftCount)
        } catch (e: Exception) {
            Log.e(TAG, "Bulk leave failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
