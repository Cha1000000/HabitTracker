package com.habittracker.presentation.widget

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.habittracker.domain.model.isGroupHabit
import timber.log.Timber

object TodayHabitsWidgetRefresh {

    suspend fun refresh(context: Context, force: Boolean = false) {
        if (force) {
            TodayHabitsWidget.clearTodayHabits(context)
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TodayHabitsWidgetEntryPoint::class.java
        )

        val userId = entryPoint.authRepository().getCurrentUserId()
        if (userId.isNullOrBlank()) {
            Timber.tag("TodayHabitsWidget").d("refresh: skip (no user)")
            if (force) {
                TodayHabitsWidget.updateTodayHabits(context, emptyList())
            }
            return
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val habits = runCatching {
            entryPoint.getHabitsForDateUseCase().invoke(userId, today).firstOrNull().orEmpty()
        }.getOrDefault(emptyList())

        val items = habits.map { habit ->
            // Получаем полную запись о выполнении, чтобы узнать текущий прогресс
            val entry = runCatching {
                entryPoint.getHabitCompletionStatusUseCase().getEntry(habit.id, userId, today)
            }.getOrNull()

            val isCompleted = entry?.completed ?: false
            val isRestDay = entry?.isRestDay ?: false
            val currentValue = entry?.value ?: 0

            TodayHabitWidgetItem(
                id = habit.id,
                title = habit.title,
                isCompleted = isCompleted,
                isRestDay = isRestDay,
                isGroup = habit.isGroupHabit(),
                currentValue = currentValue,
                targetValue = habit.targetValue,
                unit = habit.unit,
                type = habit.goalType.name,
                timeOfDay = habit.timeOfDay.name
            )
        }.sortedWith(TodayHabitWidgetItem.COMPARATOR)

        TodayHabitsWidget.updateTodayHabits(context, items)
    }
}
