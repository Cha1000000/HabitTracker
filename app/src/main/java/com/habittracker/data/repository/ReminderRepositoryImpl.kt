package com.habittracker.data.repository

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.habittracker.data.worker.HabitReminderWorker
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.repository.ReminderRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Имплементация репозитория для управления напоминаниями о привычках
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val workManager: WorkManager
) : ReminderRepository {

    companion object {
        private const val REMINDER_WORK_PREFIX = "habit_reminder_"
    }

    override suspend fun scheduleReminder(habit: Habit) {
        val reminderTime = habit.reminderTime ?: return
        
        // Отменяем существующее напоминание
        cancelReminder(habit.id)
        
        // Вычисляем задержку до первого напоминания
        val delay = calculateDelayUntilReminderTime(reminderTime)
        
        // Создаем данные для Worker
        val inputData = Data.Builder()
            .putString(HabitReminderWorker.HABIT_ID_KEY, habit.id)
            .putString(HabitReminderWorker.HABIT_TITLE_KEY, habit.title)
            .putString(HabitReminderWorker.HABIT_ICON_KEY, habit.icon)
            .build()

        // Создаем запрос на выполнение работы
        val workRequest = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(getWorkTag(habit.id))
            .build()

        // Планируем работу
        workManager.enqueueUniqueWork(
            getWorkName(habit.id),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Если привычка повторяющаяся, планируем следующие напоминания
        scheduleRecurringReminders(habit, delay)
    }

    override suspend fun cancelReminder(habitId: String) {
        workManager.cancelUniqueWork(getWorkName(habitId))
        workManager.cancelAllWorkByTag(getWorkTag(habitId))
    }

    override suspend fun cancelAllReminders() {
        workManager.cancelAllWorkByTag("habit_reminder")
    }

    override suspend fun isReminderScheduled(habitId: String): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(getWorkName(habitId)).get()
        return workInfos.any { !it.state.isFinished }
    }

    /**
     * Планирует повторяющиеся напоминания в зависимости от частоты привычки
     */
    private fun scheduleRecurringReminders(habit: Habit, initialDelay: Long) {
        val reminderTime = habit.reminderTime ?: return
        
        when (habit.frequency) {
            HabitFrequency.DAILY -> {
                // Планируем на следующие 7 дней
                repeat(7) { dayOffset ->
                    if (dayOffset > 0) {
                        scheduleReminderForDay(habit, reminderTime, dayOffset, initialDelay)
                    }
                }
            }
            HabitFrequency.WEEKDAYS -> {
                // Планируем только на будние дни (пн-пт)
                scheduleWeekdayReminders(habit, reminderTime, initialDelay)
            }
            HabitFrequency.WEEKENDS -> {
                // Планируем только на выходные (сб-вс)
                scheduleWeekendReminders(habit, reminderTime, initialDelay)
            }
            HabitFrequency.WEEKLY -> {
                // Планируем на следующие 4 недели
                repeat(4) { weekOffset ->
                    if (weekOffset > 0) {
                        scheduleReminderForDay(habit, reminderTime, weekOffset * 7, initialDelay)
                    }
                }
            }
            HabitFrequency.CUSTOM -> {
                // Для произвольной частоты планируем только на завтра
                scheduleReminderForDay(habit, reminderTime, 1, initialDelay)
            }
        }
    }

    /**
     * Планирует напоминание на конкретный день
     */
    private fun scheduleReminderForDay(
        habit: Habit,
        reminderTime: LocalTime,
        dayOffset: Int,
        baseDelay: Long
    ) {
        val additionalDelay = dayOffset * 24 * 60 * 60 * 1000L // дни в миллисекундах
        val totalDelay = baseDelay + additionalDelay

        val inputData = Data.Builder()
            .putString(HabitReminderWorker.HABIT_ID_KEY, habit.id)
            .putString(HabitReminderWorker.HABIT_TITLE_KEY, habit.title)
            .putString(HabitReminderWorker.HABIT_ICON_KEY, habit.icon)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(totalDelay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(getWorkTag(habit.id))
            .addTag("habit_reminder")
            .build()

        workManager.enqueue(workRequest)
    }

    /**
     * Планирует напоминания только на будние дни
     */
    private fun scheduleWeekdayReminders(habit: Habit, reminderTime: LocalTime, baseDelay: Long) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        
        // Планируем на следующие 14 дней, но только на будние
        repeat(14) { dayOffset ->
            if (dayOffset > 0) {
                val targetDate = now.date.plus(dayOffset, DateTimeUnit.DAY)
                val dayOfWeek = targetDate.dayOfWeek.value // 1=понедельник, 7=воскресенье
                
                // Проверяем, что это будний день (пн-пт)
                if (dayOfWeek in 1..5) {
                    scheduleReminderForDay(habit, reminderTime, dayOffset, baseDelay)
                }
            }
        }
    }

    /**
     * Планирует напоминания только на выходные дни
     */
    private fun scheduleWeekendReminders(habit: Habit, reminderTime: LocalTime, baseDelay: Long) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        
        // Планируем на следующие 14 дней, но только на выходные
        repeat(14) { dayOffset ->
            if (dayOffset > 0) {
                val targetDate = now.date.plus(dayOffset, DateTimeUnit.DAY)
                val dayOfWeek = targetDate.dayOfWeek.value // 1=понедельник, 7=воскресенье
                
                // Проверяем, что это выходной день (сб-вс)
                if (dayOfWeek in 6..7) {
                    scheduleReminderForDay(habit, reminderTime, dayOffset, baseDelay)
                }
            }
        }
    }

    /**
     * Вычисляет задержку в миллисекундах до времени напоминания
     */
    private fun calculateDelayUntilReminderTime(reminderTime: LocalTime): Long {
        val now = Clock.System.now()
        val currentDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val currentTime = currentDateTime.time
        
        // Если время напоминания уже прошло сегодня, планируем на завтра
        val targetDate = if (reminderTime <= currentTime) {
            currentDateTime.date.plus(1, DateTimeUnit.DAY)
        } else {
            currentDateTime.date
        }
        
        val targetDateTime = LocalDateTime(targetDate, reminderTime)
        val targetInstant = targetDateTime.toInstant(TimeZone.UTC)
        
        val delayMillis = targetInstant.toEpochMilliseconds() - now.toEpochMilliseconds()
        return maxOf(0L, delayMillis) // Убеждаемся, что задержка не отрицательная
    }

    private fun getWorkName(habitId: String): String = "${REMINDER_WORK_PREFIX}$habitId"
    
    private fun getWorkTag(habitId: String): String = "habit_reminder_$habitId"
}
