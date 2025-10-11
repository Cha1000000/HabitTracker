package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.isActiveOnDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use case для получения списка привычек, активных на конкретную дату.
 * Фильтрует привычки по их датам начала и окончания, учитывая персональные даты участников.
 */
class GetHabitsForDateUseCase @Inject constructor(
    private val getHabitsUseCase: GetHabitsUseCase
) {
    /**
     * Возвращает Flow со списком привычек, активных на указанную дату.
     * Для групповых привычек учитывает персональные даты окончания участников.
     * @param userId ID пользователя
     * @param date Дата для фильтрации привычек
     */
    operator fun invoke(userId: String, date: LocalDate): Flow<List<Habit>> {
        return getHabitsUseCase(userId).map { habits ->
            habits.filter { habit ->
                // Получаем дату создания привычки
                val createdDate = if (habit.createdAt > 0) {
                    fromEpochMilliseconds(habit.createdAt)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                } else null
                
                // Базовая проверка активности привычки на дату
                val isActive = habit.isActiveOnDate(date, createdDate)
                
                // Логируем для отладки
                if (habit.startDate != null && habit.startDate > date) {
                    Log.d("GetHabitsForDateUseCase",
                        "📅 Habit '${habit.title}' has future startDate=${habit.startDate}, currentDate=$date, isActive=$isActive")
                }
                
                isActive
                // Примечание: персональные даты окончания обрабатываются отдельно в UI слое
                // через observeHabitParticipantSettings для каждой групповой привычки
            }
        }
    }
}
