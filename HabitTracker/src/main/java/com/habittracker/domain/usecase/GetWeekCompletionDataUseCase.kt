package com.habittracker.domain.usecase

import com.habittracker.domain.repository.FirestoreRepository
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
 * Приоритетно использует Firestore с локальным фоллбэком на Room для оффлайн поддержки.
 */
class GetWeekCompletionDataUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository,
    private val firestoreRepository: FirestoreRepository,
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
        
        // Получаем все привычки пользователя с фоллбэком на локальную БД
        val habits = try {
            // Сначала пытаемся получить из Firestore (поддерживает оффлайн кэш)
            firestoreRepository.getUserHabits(userId).firstOrNull() ?: emptyList()
        } catch (_: Exception) {
            // При ошибке используем локальные данные из Room
            habitRepository.getHabitsByUserId(userId).firstOrNull() ?: emptyList()
        }
        
        if (habits.isEmpty()) {
            return@coroutineScope weekDates.associateWith { 0f }
        }
        
        // Для каждой даты недели подсчитываем процент выполнения
        val completionData = weekDates.map { weekDate ->
            async {
                val totalHabits = habits.size
                var completedHabits = 0
                
                // Проверяем выполнение каждой привычки в этот день
                habits.forEach { habit ->
                    val isCompleted = try {
                        // Сначала пытаемся получить из Firestore
                        val firestoreEntry = firestoreRepository.getHabitEntry(
                            habitId = habit.id,
                            userId = userId,
                            date = weekDate
                        )
                        firestoreEntry?.completed ?: false
                    } catch (_: Exception) {
                        // При ошибке используем локальные данные
                        val localEntry = habitEntryRepository.getEntry(
                            habitId = habit.id,
                            userId = userId,
                            date = weekDate
                        )
                        localEntry?.completed ?: false
                    }
                    
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
