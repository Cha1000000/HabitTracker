package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Use case для получения списка всех привычек пользователя.
 * Приоритетно использует Firestore для актуальных данных с локальным фоллбэком.
 */
class GetHabitsUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitRepository: HabitRepository
) {
    /**
     * Возвращает Flow со списком привычек для указанного пользователя.
     * Сначала пытается получить данные из Firestore, при ошибке использует локальные данные.
     * @param userId ID пользователя.
     */
    operator fun invoke(userId: String): Flow<List<Habit>> {
        return firestoreRepository.getUserHabits(userId)
            .onStart {
                // При старте можно показать локальные данные для быстрого отображения
                // emit(habitRepository.getHabitsByUserId(userId).first())
            }
            .catch { throwable ->
                // При ошибке Firestore используем локальные данные
                habitRepository.getHabitsByUserId(userId).collect { localHabits ->
                    emit(localHabits)
                }
            }
    }
}
