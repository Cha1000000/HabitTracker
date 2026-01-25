package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.isActiveOnDate
import com.habittracker.domain.model.isActiveOnDayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case для получения списка привычек, активных на конкретную дату.
 * Фильтрует привычки по их датам начала и окончания, учитывая персональные даты участников.
 * Также фильтрует по дню недели в соответствии с частотой привычки.
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
                // Дата создания привычки теперь уже LocalDate
                val createdDate = habit.createdAt
                
                // Базовая проверка активности привычки на дату
                val isActiveByDate = habit.isActiveOnDate(date, createdDate)
                
                // Проверка активности по дню недели с учётом частоты
                val isActiveByDayOfWeek = habit.isActiveOnDayOfWeek(date.dayOfWeek)
                
                // Привычка активна только если выполнены оба условия
                val isActive = isActiveByDate && isActiveByDayOfWeek
                
                // Логируем для отладки
                if (habit.startDate != null && habit.startDate > date) {
                    Timber.tag("GetHabitsForDateUseCase").d(
                        "📅 Habit '${habit.title}' has future startDate=${habit.startDate}, currentDate=$date, isActive=$isActive"
                    )
                }
                
                if (!isActiveByDayOfWeek) {
                    Timber.tag("GetHabitsForDateUseCase").d(
                        "📆 Habit '${habit.title}' is not active on ${date.dayOfWeek} (frequency=${habit.frequency})"
                    )
                }
                
                isActive
                // Примечание: персональные даты окончания обрабатываются отдельно в UI слое
                // через observeHabitParticipantSettings для каждой групповой привычки
            }
        }
    }
}
