package com.habittracker.domain.usecase

import android.util.Log
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
            firestoreRepository.updateHabit(habit)
            habitRepository.updateHabit(habit)
        } catch (e: Exception) {
            Log.w("UpdateHabitUseCase", "Firestore update failed: ${e.message}")
            habitRepository.updateHabit(habit)
            throw e
        }
    }
}
