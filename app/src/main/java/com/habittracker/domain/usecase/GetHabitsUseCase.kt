package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для получения списка всех привычек пользователя.
 */
class GetHabitsUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    /**
     * Возвращает Flow со списком привычек для указанного пользователя.
     * @param userId ID пользователя.
     */
    operator fun invoke(userId: String): Flow<List<Habit>> {
        // В будущем здесь можно добавить логику для получения ID текущего пользователя
        return habitRepository.getHabitsByUserId(userId)
    }
}
