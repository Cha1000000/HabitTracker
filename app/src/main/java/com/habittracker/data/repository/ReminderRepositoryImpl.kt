package com.habittracker.data.repository

import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.habittracker.data.worker.HabitReminderWorker
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ReminderRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
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
    private val workManager: WorkManager,
    private val authRepository: AuthRepository
) : ReminderRepository {

    companion object {
        private const val REMINDER_WORK_PREFIX = "habit_reminder_"
    }

    override suspend fun scheduleReminder(habit: Habit) {
        Log.d("ReminderRepositoryImpl", "scheduleReminder: $habit")

        // Отменяем существующее напоминание
        cancelReminder(habit.id)
        
        // Вычисляем задержку до первого напоминания с учетом частоты и выбранных дней
        val delay = calculateDelayForNextOccurrence(habit)
        
        // Создаем данные для Worker
        val inputData = Data.Builder()
            .putString(HabitReminderWorker.HABIT_ID_KEY, habit.id)
            .putString(HabitReminderWorker.HABIT_TITLE_KEY, habit.title)
            .putString(HabitReminderWorker.HABIT_ICON_KEY, habit.icon)
            .putString(HabitReminderWorker.USER_ID_KEY, authRepository.getCurrentUserId())
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

        Log.d("ReminderRepositoryImpl", "Scheduled reminder for habit ${habit.id} with delay $delay")

        // Если привычка повторяющаяся, планируем следующие напоминания
        scheduleRecurringReminders(habit, delay)
    }

    override suspend fun cancelReminder(habitId: String) {
        Log.d("ReminderRepositoryImpl", "cancelReminder: $habitId")
        workManager.cancelUniqueWork(getWorkName(habitId))
        workManager.cancelAllWorkByTag(getWorkTag(habitId))
    }

    override suspend fun cancelAllReminders() {
        Log.d("ReminderRepositoryImpl", "cancelAllReminders")
        workManager.cancelAllWorkByTag("habit_reminder")
    }

    override suspend fun isReminderScheduled(habitId: String): Boolean {
        Log.d("ReminderRepositoryImpl", "isReminderScheduled: $habitId")
        val workInfos = workManager.getWorkInfosForUniqueWork(getWorkName(habitId)).get()
        return workInfos.any { !it.state.isFinished }
    }

    /**
     * Планирует повторяющиеся напоминания в зависимости от частоты привычки
     */
    private fun scheduleRecurringReminders(habit: Habit, initialDelay: Long) {
        Log.d("ReminderRepositoryImpl", "scheduleRecurringReminders: $habit")

        // Горизонт планирования (дней вперед)
        val horizonDays = 21
        
        fun scheduleByAllowedDays(allowedDays: Set<Int>) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            repeat(horizonDays) { dayOffset ->
                if (dayOffset > 0) {
                    val targetDate = now.date.plus(dayOffset, DateTimeUnit.DAY)
                    val dow = targetDate.dayOfWeek.value // 1..7
                    if (allowedDays.contains(dow)) {
                        scheduleReminderForDay(habit, dayOffset, initialDelay)
                    }
                }
            }
        }

        when (habit.frequency) {
            HabitFrequency.DAILY -> {
                // Все дни
                scheduleByAllowedDays(setOf(1,2,3,4,5,6,7))
            }
            HabitFrequency.WEEKDAYS -> {
                scheduleWeekdayReminders(habit, initialDelay)
            }
            HabitFrequency.WEEKENDS -> {
                scheduleWeekendReminders(habit, initialDelay)
            }
            HabitFrequency.WEEKLY, HabitFrequency.TWICE_A_WEEK, HabitFrequency.CUSTOM -> {
                val selected = habit.reminderDays.map { it.value }.toSet()
                if (selected.isNotEmpty()) {
                    scheduleByAllowedDays(selected)
                } else {
                    // Fallback к старой логике, чтобы не сломать существующие данные
                    when (habit.frequency) {
                        HabitFrequency.WEEKLY -> repeat(4) { weekOffset -> if (weekOffset > 0) scheduleReminderForDay(habit,
                            weekOffset * 7, initialDelay) }
                        HabitFrequency.CUSTOM -> scheduleReminderForDay(habit, 1, initialDelay)
                        HabitFrequency.TWICE_A_WEEK -> repeat(4) { weekOffset -> if (weekOffset > 0) scheduleReminderForDay(habit,
                            weekOffset * 7, initialDelay) }
                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * Планирует напоминание на конкретный день
     */
    private fun scheduleReminderForDay(
        habit: Habit,
        dayOffset: Int,
        baseDelay: Long
    ) {
        Log.d("ReminderRepositoryImpl", "scheduleReminderForDay: $habit, dayOffset=$dayOffset")
        val additionalDelay = dayOffset * 24 * 60 * 60 * 1000L // дни в миллисекундах
        val totalDelay = baseDelay + additionalDelay

        val inputData = Data.Builder()
            .putString(HabitReminderWorker.HABIT_ID_KEY, habit.id)
            .putString(HabitReminderWorker.HABIT_TITLE_KEY, habit.title)
            .putString(HabitReminderWorker.HABIT_ICON_KEY, habit.icon)
            .putString(HabitReminderWorker.USER_ID_KEY, authRepository.getCurrentUserId())
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
    private fun scheduleWeekdayReminders(habit: Habit, baseDelay: Long) {
        Log.d("ReminderRepositoryImpl", "scheduleWeekdayReminders: $habit")
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        
        // Планируем на следующие 14 дней, но только на будние
        repeat(14) { dayOffset ->
            if (dayOffset > 0) {
                val targetDate = now.date.plus(dayOffset, DateTimeUnit.DAY)
                val dayOfWeek = targetDate.dayOfWeek.value // 1=понедельник, 7=воскресенье
                
                // Проверяем, что это будний день (пн-пт)
                if (dayOfWeek in 1..5) {
                    scheduleReminderForDay(habit, dayOffset, baseDelay)
                }
            }
        }
    }

    /**
     * Планирует напоминания только на выходные дни
     */
    private fun scheduleWeekendReminders(habit: Habit, baseDelay: Long) {
        Log.d("ReminderRepositoryImpl", "scheduleWeekendReminders: $habit")
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        
        // Планируем на следующие 14 дней, но только на выходные
        repeat(14) { dayOffset ->
            if (dayOffset > 0) {
                val targetDate = now.date.plus(dayOffset, DateTimeUnit.DAY)
                val dayOfWeek = targetDate.dayOfWeek.value // 1=понедельник, 7=воскресенье
                
                // Проверяем, что это выходной день (сб-вс)
                if (dayOfWeek in 6..7) {
                    scheduleReminderForDay(habit, dayOffset, baseDelay)
                }
            }
        }
    }

    /**
     * Вычисляет задержку в миллисекундах до времени напоминания
     */
    private fun calculateDelayForNextOccurrence(habit: Habit): Long {
        val reminderTime = habit.reminderTime ?: return 0L
        Log.d("ReminderRepositoryImpl", "calculateDelayForNextOccurrence: time=$reminderTime, freq=${habit.frequency}, days=${habit.reminderDays}")
        val nowInstant = Clock.System.now()
        val now = nowInstant.toLocalDateTime(TimeZone.currentSystemDefault())
        val selectedDays = habit.reminderDays.map { it.value }.toSet()

        for (dayOffset in 0..28) {
            val targetDate = now.date.plus(dayOffset, DateTimeUnit.DAY)
            val dow = targetDate.dayOfWeek.value
            val targetDateTime = LocalDateTime(targetDate, reminderTime)
            val targetInstant = targetDateTime.toInstant(TimeZone.currentSystemDefault())
            val strictlyFuture = targetInstant > nowInstant

            val allowed = when (habit.frequency) {
                HabitFrequency.DAILY -> true
                HabitFrequency.WEEKDAYS -> dow in 1..5
                HabitFrequency.WEEKENDS -> dow in 6..7
                HabitFrequency.WEEKLY, HabitFrequency.TWICE_A_WEEK, HabitFrequency.CUSTOM -> {
                    if (selectedDays.isNotEmpty()) {
                        selectedDays.contains(dow)
                    } else {
                        // После миграции старые привычки могут не иметь явно выбранных дней.
                        // Гарантируем ближайшее уведомление:
                        //  - если время сегодня ещё впереди — ставим на сегодня
                        //  - дальше оставляем прежний фоллбек по частоте
                        when (habit.frequency) {
                            HabitFrequency.WEEKLY -> (dayOffset == 0) || (dayOffset % 7 == 0)
                            HabitFrequency.TWICE_A_WEEK -> (dayOffset == 0) || (dayOffset % 7 == 0)
                            HabitFrequency.CUSTOM -> (dayOffset == 0) || (dayOffset == 1)
                            else -> false
                        }
                    }
                }
            }

            if (allowed && strictlyFuture) {
                val delay = (targetInstant - nowInstant).inWholeMilliseconds
                Log.d("ReminderRepositoryImpl", "Next occurrence in $delay ms on $targetDateTime (dow=$dow, offset=$dayOffset, selectedEmpty=${selectedDays.isEmpty()})")
                return delay
            }
        }
        // Фоллбек: 24 часа
        Log.w("ReminderRepositoryImpl", "No future occurrence found within 28 days, fallback to 24h")
        return 24 * 60 * 60 * 1000L
    }

    private fun getWorkName(habitId: String): String = "${REMINDER_WORK_PREFIX}$habitId"
    
    private fun getWorkTag(habitId: String): String = "habit_reminder_$habitId"
}
