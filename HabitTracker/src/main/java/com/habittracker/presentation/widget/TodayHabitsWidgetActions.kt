package com.habittracker.presentation.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock
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
                    runCatching {
                        entryPoint.setHabitRestDayUseCase().invoke(habitId, today, false)
                    }.onFailure { e ->
                        Timber.tag("TodayHabitsWidget").w(e, "Failed to remove rest day before marking completed")
                    }
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
                    runCatching {
                        entryPoint.setHabitRestDayUseCase().invoke(habitId, today, false)
                    }.onFailure { e ->
                        Timber.tag("TodayHabitsWidget").w(e, "Failed to remove rest day before marking completed")
                    }
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

class RefreshTodayHabitsWidgetAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nowMs = System.currentTimeMillis()
        val lastMs = prefs.getLong(KEY_LAST_REFRESH_MS, 0L)
        val elapsedMs = nowMs - lastMs

        if (elapsedMs in 1 until COOLDOWN_MS) {
            Timber.tag(TAG).d("Manual widget refresh skipped due to cooldown: ${elapsedMs}ms")
            return
        }

        prefs.edit().putLong(KEY_LAST_REFRESH_MS, nowMs).apply()

        runCatching {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodayHabitsWidget::class.java)
            if (glanceIds.isNotEmpty()) {
                glanceIds.forEach { widgetId ->
                    updateAppWidgetState(context, widgetId) { mutablePrefs ->
                        mutablePrefs[TodayHabitsWidget.PREF_KEY_REFRESH_UNTIL_MS] = nowMs + MIN_LOADING_MS
                    }
                }
                TodayHabitsWidget().updateAll(context)
            }
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to set loading state: ${e.message}")
        }

        runCatching {
            TodayHabitsWidgetScheduler.enqueueRefresh(context)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Manual widget refresh enqueue failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TodayHabitsWidget"
        private const val PREFS_NAME = "today_habits_widget_prefs"
        private const val KEY_LAST_REFRESH_MS = "last_manual_refresh_ms"
        private const val COOLDOWN_MS = 5 * 60 * 1000L
        private const val MIN_LOADING_MS = 1500L
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
    fun setHabitRestDayUseCase(): com.habittracker.domain.usecase.SetHabitRestDayUseCase
}
