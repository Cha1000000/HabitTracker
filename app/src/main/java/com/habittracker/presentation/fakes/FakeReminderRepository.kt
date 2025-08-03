package com.habittracker.presentation.fakes

import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.ReminderRepository

// Fake repository for previews
class FakeReminderRepository : ReminderRepository {
    override suspend fun scheduleReminder(habit: Habit) {
        // Пустая реализация для превью
    }

    override suspend fun cancelReminder(habitId: String) {
        // Пустая реализация для превью
    }

    override suspend fun cancelAllReminders() {
        // Пустая реализация для превью
    }

    override suspend fun isReminderScheduled(habitId: String): Boolean {
        return false // Всегда возвращаем false для превью
    }
}