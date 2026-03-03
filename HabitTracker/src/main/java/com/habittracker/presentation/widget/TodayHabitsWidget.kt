package com.habittracker.presentation.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.habittracker.MainActivity
import com.habittracker.R

class TodayHabitsWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val items = prefs.getSerializedHabits()
            val nowMs = System.currentTimeMillis()
            val refreshingUntilMs = prefs[PREF_KEY_REFRESH_UNTIL_MS] ?: 0L
            val isRefreshing = nowMs < refreshingUntilMs

            // Colors for Tahoe Theme
            val textMain = ColorProvider(R.color.widget_text_main)
            val textTertiary = ColorProvider(R.color.widget_text_tertiary)
            val dividerColor = ColorProvider(R.color.widget_divider)

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .background(ImageProvider(R.drawable.widget_background_tahoe))
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.widget_today_title),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = textMain
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Image(
                            provider = ImageProvider(R.drawable.ic_tahoe_arrow_path),
                            contentDescription = context.getString(R.string.cd_refresh),
                            modifier = GlanceModifier
                                .size(18.dp)
                                .clickable(actionRunCallback<RefreshTodayHabitsWidgetAction>())
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.mipmap.ic_launcher),
                                contentDescription = null,
                                modifier = GlanceModifier.size(16.dp)
                            )
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = context.getString(R.string.app_name),
                                modifier = GlanceModifier.clickable(
                                    actionStartActivity(
                                        Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                    )
                                ),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = textTertiary
                                )
                            )
                        }
                    }

                    if (isRefreshing) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Text(
                            text = context.getString(R.string.widget_loading),
                            style = TextStyle(color = textTertiary, fontSize = 13.sp)
                        )
                    }
                    
                    Spacer(modifier = GlanceModifier.height(10.dp))

                    when {
                        items == null -> {
                            Text(
                                text = context.getString(R.string.widget_loading),
                                style = TextStyle(color = textMain)
                            )
                        }
                        items.isEmpty() -> {
                            Text(
                                text = context.getString(R.string.widget_no_habits),
                                style = TextStyle(color = textMain)
                            )
                        }
                        items.all { it.isCompleted } -> {
                            Column(
                                modifier = GlanceModifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🎉",
                                    style = TextStyle(fontSize = 32.sp)
                                )
                                Spacer(modifier = GlanceModifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.widget_all_done),
                                    style = TextStyle(
                                        color = textMain,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = GlanceModifier.defaultWeight()
                            ) {
                                itemsIndexed(items) { index, item ->
                                    Column(
                                        modifier = GlanceModifier.fillMaxWidth()
                                    ) {
                                        HabitRow(
                                            context = context,
                                            item = item,
                                            textColor = textMain,
                                            secondaryColor = textTertiary,
                                        )
                                        if (index != items.lastIndex) {
                                            Spacer(
                                                modifier = GlanceModifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(dividerColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitRow(
        context: Context, 
        item: TodayHabitWidgetItem, 
        textColor: ColorProvider,
        secondaryColor: ColorProvider,
    ) {
        val openIntent = buildOpenHabitIntent(context, item)
        val isMeasurable = item.type == "MEASURABLE"
        
        // Interaction logic:
        // Measurable -> Click opens app to enter value
        // Simple -> Click toggles completion
        val checkboxAction = if (isMeasurable) {
             actionStartActivity(openIntent)
        } else {
             actionRunCallback<ToggleHabitAction>(ToggleHabitAction.params(item.id))
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp), // Larger tap area and spacing
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Title and Info
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openIntent))
            ) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        fontWeight = if (item.isRestDay || item.isCompleted) FontWeight.Normal else FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (item.isRestDay) secondaryColor else textColor,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1
                )
                
                // Subtitle info (Time + Progress)
                val subtitleText = if (item.isRestDay) {
                    context.getString(R.string.widget_rest_day)
                } else {
                    val timeString = getTimeOfDayString(context, item.timeOfDay)
                    val progressString = if (isMeasurable) {
                        "${item.currentValue}/${item.targetValue} ${item.unit}"
                    } else null
                    listOfNotNull(timeString, progressString).joinToString(" • ")
                }

                if (subtitleText.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = subtitleText,
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = secondaryColor
                        ),
                        maxLines = 1
                    )
                }
            }

            // Right Side: Checkbox
            Box(
                modifier = GlanceModifier
                    .size(48.dp) // Larger checkbox area
                    .clickable(checkboxAction),
                contentAlignment = Alignment.Center
            ) {
                val imageResId = if (item.isCompleted) {
                    R.drawable.widget_checkbox_checked
                } else {
                    R.drawable.widget_checkbox_unchecked
                }

                Image(
                    provider = ImageProvider(imageResId),
                    contentDescription = if (item.isCompleted) "Completed" else "Not completed",
                    modifier = GlanceModifier.size(32.dp)
                )
            }
        }
    }

    private fun getTimeOfDayString(context: Context, timeOfDay: String): String? {
        return when (timeOfDay) {
            "MORNING" -> context.getString(R.string.time_of_day_morning)
            "AFTERNOON" -> context.getString(R.string.time_of_day_afternoon)
            "EVENING" -> context.getString(R.string.time_of_day_evening)
            else -> null // Don't show "Any time" to keep it clean, or show if desired
        }
    }

    companion object {
        internal const val PREF_KEY_TODAY_HABITS = "today_habits_json"
        private val PREF_KEY = stringPreferencesKey(PREF_KEY_TODAY_HABITS)
        internal val PREF_KEY_REFRESH_UNTIL_MS = longPreferencesKey("today_habits_refresh_until_ms")

        suspend fun updateTodayHabits(context: Context, items: List<TodayHabitWidgetItem>) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodayHabitsWidget::class.java)
            if (glanceIds.isEmpty()) return

            val json = TodayHabitWidgetItem.toJson(items)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { mutablePrefs ->
                    mutablePrefs[PREF_KEY] = json
                    mutablePrefs.remove(PREF_KEY_REFRESH_UNTIL_MS)
                }
            }
            TodayHabitsWidget().updateAll(context)
        }

        suspend fun clearTodayHabits(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodayHabitsWidget::class.java)
            if (glanceIds.isEmpty()) return

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { mutablePrefs ->
                    mutablePrefs.remove(PREF_KEY)
                    mutablePrefs.remove(PREF_KEY_REFRESH_UNTIL_MS)
                }
            }
            TodayHabitsWidget().updateAll(context)
        }

        /**
         * Optimistically toggles habit state in the widget and returns the new state.
         * Returns null if habit was not found in the current widget state.
         */
        suspend fun toggleHabitOptimistically(context: Context, habitId: String): Boolean? {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodayHabitsWidget::class.java)
            if (glanceIds.isEmpty()) return null

            var newStatus: Boolean? = null

            // Update all widgets to keep them in sync
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { mutablePrefs ->
                    val json = mutablePrefs[PREF_KEY]
                    if (json != null) {
                        val items = TodayHabitWidgetItem.fromJson(json).toMutableList()
                        val index = items.indexOfFirst { it.id == habitId }
                        if (index != -1) {
                            val item = items[index]
                            val newCompleted = !item.isCompleted
                            val newItem = item.copy(
                                isCompleted = newCompleted,
                                isRestDay = if (newCompleted) false else item.isRestDay
                            )
                            items[index] = newItem
                            
                            // Capture new status from the first occurrence found
                            if (newStatus == null) {
                                newStatus = newItem.isCompleted
                            }
                            
                            // Re-sort using the shared comparator
                            items.sortWith(TodayHabitWidgetItem.COMPARATOR)
                            
                            mutablePrefs[PREF_KEY] = TodayHabitWidgetItem.toJson(items)
                        }
                    }
                }
            }
            
            if (newStatus != null) {
                TodayHabitsWidget().updateAll(context)
            }
            
            return newStatus
        }
    }
}

private fun buildOpenHabitIntent(context: Context, item: TodayHabitWidgetItem): Intent {
    val navigateTo = if (item.isGroup) "group_habit_detail" else "self_habit_detail"
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("navigate_to", navigateTo)
        putExtra("habit_id", item.id)
        putExtra("habitId", item.id)
    }
}

private fun Preferences.getSerializedHabits(): List<TodayHabitWidgetItem>? {
    val json = this[stringPreferencesKey(TodayHabitsWidget.PREF_KEY_TODAY_HABITS)]
        ?: return null

    return TodayHabitWidgetItem.fromJson(json)
}
