package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для получения одной привычки по ее идентификатору.
 * Приоритетно использует Firestore с локальным фоллбэком.
 */
class GetHabitByIdUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(id: String): Habit? {
        return try {
            // Сначала пытаемся получить из Firestore
            firestoreRepository.getHabitById(id)
        } catch (e: Exception) {
            // При ошибке используем локальные данные
            habitRepository.getHabitById(id)
        }
    }
}
