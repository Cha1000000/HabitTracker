package com.habittracker.domain.repository

import com.habittracker.domain.model.Habit

/**
 * Репозиторий для управления напоминаниями о привычках
 */
interface ReminderRepository {
    /**
     * Планирует напоминание для привычки
     * @param habit Привычка для которой нужно запланировать напоминание
     */
    suspend fun scheduleReminder(habit: Habit)
    
    /**
     * Отменяет напоминание для привычки
     * @param habitId ID привычки для которой нужно отменить напоминание
     */
    suspend fun cancelReminder(habitId: String)
    
    /**
     * Отменяет все напоминания
     */
    suspend fun cancelAllReminders()
    
    /**
     * Проверяет, запланировано ли напоминание для привычки
     * @param habitId ID привычки
     * @return true если напоминание запланировано
     */
    suspend fun isReminderScheduled(habitId: String): Boolean
}
