package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import timber.log.Timber

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
            Timber.tag(TAG).d("Starting bulk leave from shared habits: currentUser=$currentUserId, friend=$friendId")
            
            // Получаем все групповые привычки текущего пользователя
            // Снимок, а не collect: поток из Room не завершается, и выход из привычек зависал навсегда
            val userHabits = habitRepository.getHabitsByUserId(currentUserId).first()
            
            var leftCount = 0
            
            for (habit in userHabits) {
                // Пропускаем не групповые привычки
                if (!habit.isGroupHabit()) continue
                
                // Пропускаем привычки, где друг не участвует
                if (!habit.isUserParticipant(friendId)) continue
                
                // Пропускаем привычки, созданные текущим пользователем
                // (он не может "выйти" из своей привычки)
                if (habit.createdBy == currentUserId) {
                    Timber.tag(TAG).d("Skipping habit ${habit.id} - created by current user")
                    continue
                }
                
                // Выходим из привычки
                try {
                    habitRepository.removeParticipantFromHabit(habit.id, currentUserId)
                    leftCount++
                    Timber.tag(TAG).d("Left habit: ${habit.title} (${habit.id})")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w("Failed to leave habit ${habit.id}: ${e.message}")
                }
            }
            
            Timber.tag(TAG).d("Bulk leave completed: left $leftCount habits")
            Result.success(leftCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Bulk leave failed: ${e.message}")
            Result.failure(e)
        }
    }
}
