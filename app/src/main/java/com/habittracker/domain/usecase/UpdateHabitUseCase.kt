package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для обновления существующей привычки.
 * Обновляет данные в Firestore для синхронизации и локально для офлайн-доступа.
 */
class UpdateHabitUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(habit: Habit) {
        try {
            // Сначала обновляем в Firestore
            firestoreRepository.updateHabit(habit)
            
            // Затем обновляем локально
            habitRepository.updateHabit(habit)
        } catch (e: Exception) {
            // Если Firestore недоступен, обновляем только локально
            habitRepository.updateHabit(habit)
            throw e
        }
    }
}
