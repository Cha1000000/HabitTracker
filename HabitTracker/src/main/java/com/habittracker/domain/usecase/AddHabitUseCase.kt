package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case для добавления новой привычки.
 * Инкапсулирует бизнес-логику, связанную с созданием привычки.
 * Сохраняет данные в Firestore для синхронизации и локально для офлайн-доступа.
 */
class AddHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val updateUserPointsUseCase: UpdateUserPointsUseCase,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) {
    /**
     * Выполняет основное действие use case.
     * @param habit Привычка для добавления.
     */
    suspend operator fun invoke(habit: Habit) {
        Timber.tag("AddHabitUseCase").d("📅 Saving habit '${habit.title}' with startDate=${habit.startDate}, endDate=${habit.endDate}")
        
        // Сохраняем через репозиторий (Local + Outbox)
        habitRepository.insertHabit(habit)
        Timber.tag("AddHabitUseCase").d("✅ Habit saved successfully (Local + Outbox)")

        // Начисление очков за создание групповой привычки
        if (habit.type == HabitType.GROUP) {
            try {
                updateUserPointsUseCase(habit.createdBy, GamificationEvent.GroupHabitCreated)
                checkAchievementsUseCase(habit.createdBy)
            } catch (_: Exception) {
                // Ignore gamification errors
            }
        }
    }
}
