package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для получения списка всех привычек пользователя.
 * Возвращает локальный Flow из Room, а синхронизацию с Firestore выполняет HabitRepository.
 */
class GetHabitsUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    /**
     * Возвращает Flow со списком привычек для указанного пользователя.
     * Источник истины для UI — Room. Обновление Room из Firestore происходит внутри HabitRepositoryImpl.
     * @param userId ID пользователя.
     */
    operator fun invoke(userId: String): Flow<List<Habit>> {
        return habitRepository.getHabitsByUserId(userId)
    }
}
