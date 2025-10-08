package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Use case для получения списка всех привычек пользователя.
 * Приоритетно использует Firestore для актуальных данных с локальным фоллбэком.
 * Автоматически синхронизирует привычки из Firestore в Room для офлайн-доступа.
 */
class GetHabitsUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitRepository: HabitRepository
) {
    /**
     * Возвращает Flow со списком привычек для указанного пользователя.
     * Сначала пытается получить данные из Firestore, при ошибке использует локальные данные.
     * Автоматически сохраняет привычки из Firestore в Room для офлайн-доступа и корректной работы других use cases.
     * @param userId ID пользователя.
     */
    operator fun invoke(userId: String): Flow<List<Habit>> {
        return firestoreRepository.getUserHabits(userId)
            .onEach { habits ->
                // Синхронизируем привычки из Firestore в Room
                // Это критически важно для работы DeleteHabitUseCase и других use cases,
                // которые читают из Room через habitRepository.getHabitById()
                try {
                    habits.forEach { habit ->
                        try {
                            val existing = habitRepository.getHabitById(habit.id)
                            if (existing != null) {
                                habitRepository.updateHabit(habit)
                            } else {
                                habitRepository.insertHabit(habit)
                            }
                        } catch (e: Exception) {
                            Log.e("GetHabitsUseCase", "Failed to sync habit ${habit.id}: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GetHabitsUseCase", "Failed to sync habits to Room: ${e.message}")
                }
            }
            .onStart {
                // При старте можно показать локальные данные для быстрого отображения
                // emit(habitRepository.getHabitsByUserId(userId).first())
            }
            .catch { throwable ->
                Log.w("GetHabitsUseCase", "Firestore error, falling back to Room: ${throwable.message}")
                habitRepository.getHabitsByUserId(userId).collect { localHabits ->
                    emit(localHabits)
                }
            }
    }
}
