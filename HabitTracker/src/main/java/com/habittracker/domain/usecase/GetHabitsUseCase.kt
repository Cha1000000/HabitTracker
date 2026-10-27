package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для получения списка всех привычек пользователя.
 * Возвращает локальный Flow из Room, сеть не трогает.
 */
class GetHabitsUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    /**
     * Возвращает Flow со списком привычек для указанного пользователя.
     * Источник истины — Room; из Firestore его наполняет RemoteDataSync.
     * @param userId ID пользователя.
     */
    operator fun invoke(userId: String): Flow<List<Habit>> {
        return habitRepository.getHabitsByUserId(userId)
    }
}
