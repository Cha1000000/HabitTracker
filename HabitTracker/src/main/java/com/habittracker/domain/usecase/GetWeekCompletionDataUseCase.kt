package com.habittracker.domain.usecase

import com.habittracker.domain.model.isActiveOnDate
import com.habittracker.domain.model.isActiveOnDayOfWeek
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

/**
 * Use case для получения данных о выполнении привычек за неделю.
 * Возвращает Map с ключами - датами и значениями - процентом выполнения (0f-1f).
 * Читает только Room: данные из облака туда кладёт RemoteDataSync.
 */
class GetWeekCompletionDataUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val habitRepository: HabitRepository
) {
    /**
     * Получает данные о выполнении привычек за неделю, содержащую указанную дату.
     * @param userId ID пользователя
     * @param date Дата, для которой нужно получить неделю (по умолчанию - сегодня)
     * @return Map<LocalDate, Float> где Float - процент выполнения привычек в этот день (0f-1f)
     */
    suspend operator fun invoke(
        userId: String,
        date: LocalDate
    ): Map<LocalDate, Float> = coroutineScope {
        // Получаем начало недели (понедельник)
        val startOfWeek = getStartOfWeek(date)
        val weekDates = (0..6).map { startOfWeek.plus(it, DateTimeUnit.DAY) }
        
        val habits = habitRepository.getHabitsByUserId(userId).firstOrNull() ?: emptyList()
        
        if (habits.isEmpty()) {
            return@coroutineScope weekDates.associateWith { 0f }
        }
        
        // Для каждой даты недели подсчитываем процент выполнения
        val completionData = weekDates.map { weekDate ->
            async {
                // Фильтруем привычки, которые активны в этот день недели и в этот период
                val activeHabitsForDay = habits.filter { habit ->
                    val isActiveByDate = habit.isActiveOnDate(weekDate, habit.createdAt)
                    val isActiveByDayOfWeek = habit.isActiveOnDayOfWeek(weekDate.dayOfWeek)
                    isActiveByDate && isActiveByDayOfWeek
                }
                
                val totalHabits = activeHabitsForDay.size
                var completedHabits = 0
                
                // Проверяем выполнение каждой активной привычки в этот день
                activeHabitsForDay.forEach { habit ->
                    val isCompleted = habitEntryRepository.getEntry(
                        habitId = habit.id,
                        userId = userId,
                        date = weekDate
                    )?.completed ?: false
                    
                    if (isCompleted) {
                        completedHabits++
                    }
                }
                
                val completionPercentage = if (totalHabits > 0) {
                    completedHabits.toFloat() / totalHabits.toFloat()
                } else {
                    0f
                }
                
                weekDate to completionPercentage
            }
        }

        completionData.associate { it.await() }
    }
    
    /**
     * Получает начало недели (понедельник) для указанной даты
     */
    private fun getStartOfWeek(date: LocalDate): LocalDate {
        val daysFromMonday = when(date.dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        return date.minus(daysFromMonday, DateTimeUnit.DAY)
    }
}
