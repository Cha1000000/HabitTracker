package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Use case для добавления новой привычки.
 * Инкапсулирует бизнес-логику, связанную с созданием привычки.
 * Сохраняет данные в Firestore для синхронизации и локально для офлайн-доступа.
 */
class AddHabitUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val habitRepository: HabitRepository
) {
    /**
     * Выполняет основное действие use case.
     * @param habit Привычка для добавления.
     */
    suspend operator fun invoke(habit: Habit) {
        android.util.Log.d("AddHabitUseCase", "📅 Saving habit '${habit.title}' with startDate=${habit.startDate}, endDate=${habit.endDate}")
        try {
            // Сначала сохраняем в Firestore для синхронизации
            firestoreRepository.saveHabit(habit)
            
            // Затем сохраняем локально для офлайн-доступа
            habitRepository.insertHabit(habit)
            android.util.Log.d("AddHabitUseCase", "✅ Habit saved successfully")
        } catch (e: Exception) {
            // Если Firestore недоступен, сохраняем только локально
            // В будущем можно добавить логику отложенной синхронизации
            android.util.Log.w("AddHabitUseCase", "⚠️ Firestore save failed, saving locally only: ${e.message}")
            habitRepository.insertHabit(habit)
            throw e
        }
    }
}
