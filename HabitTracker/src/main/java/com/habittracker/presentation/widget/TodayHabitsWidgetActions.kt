package com.habittracker.presentation.widget

import android.content.Context
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber

class ToggleHabitAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[KEY_HABIT_ID] ?: return

        // 1. Optimistic Update: Update UI immediately before touching DB
        val optimisticNewState = TodayHabitsWidget.toggleHabitOptimistically(context, habitId)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TodayHabitsWidgetEntryPoint::class.java
        )

        val userId = entryPoint.authRepository().getCurrentUserId()
        if (userId.isNullOrBlank()) return

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        try {
            // 2. Perform DB/Network operations based on the new optimistic state (or fallback to DB read)
            if (optimisticNewState != null) {
                if (optimisticNewState) {
                    entryPoint.markHabitCompletedUseCase().invoke(habitId, userId, today)
                } else {
                    entryPoint.markHabitIncompleteUseCase().invoke(habitId, userId, today)
                }
            } else {
                // Fallback if widget state was missing the item
                val isCompleted = entryPoint.getHabitCompletionStatusUseCase().invoke(habitId, userId, today)
                if (isCompleted) {
                    entryPoint.markHabitIncompleteUseCase().invoke(habitId, userId, today)
                } else {
                    entryPoint.markHabitCompletedUseCase().invoke(habitId, userId, today)
                }
            }
        } catch (e: Exception) {
            Timber.tag("TodayHabitsWidget").w(e, "ToggleHabitAction failed: ${e.message}")
        }

        // 3. Refresh widget state from source of truth (Eventual Consistency)
        runCatching {
            TodayHabitsWidgetRefresh.refresh(context)
        }.onFailure { e ->
            Timber.tag("TodayHabitsWidget").w(e, "Refresh after toggle failed: ${e.message}")
        }
    }

    companion object {
        private val KEY_HABIT_ID = ActionParameters.Key<String>("habit_id")

        fun params(habitId: String) = actionParametersOf(KEY_HABIT_ID to habitId)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayHabitsWidgetEntryPoint {
    fun authRepository(): com.habittracker.domain.repository.AuthRepository
    fun getHabitsForDateUseCase(): com.habittracker.domain.usecase.GetHabitsForDateUseCase
    fun getHabitCompletionStatusUseCase(): com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
    fun markHabitCompletedUseCase(): com.habittracker.domain.usecase.MarkHabitCompletedUseCase
    fun markHabitIncompleteUseCase(): com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
}
