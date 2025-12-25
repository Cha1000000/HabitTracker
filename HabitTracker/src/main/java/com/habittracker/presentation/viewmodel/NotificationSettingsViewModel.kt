package com.habittracker.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.NotificationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<NotificationPreferences> = _preferences.asStateFlow()
    
    private fun loadPreferences(): NotificationPreferences {
        return NotificationPreferences(
            habitRemindersEnabled = prefs.getBoolean(KEY_HABIT_REMINDERS, true),
            friendActivityEnabled = prefs.getBoolean(KEY_FRIEND_ACTIVITY, true),
            groupHabitUpdatesEnabled = prefs.getBoolean(KEY_GROUP_HABITS, true),
            challengeUpdatesEnabled = prefs.getBoolean(KEY_CHALLENGES, true),
            achievementUnlockedEnabled = prefs.getBoolean(KEY_ACHIEVEMENTS, true),
            dailySummaryEnabled = prefs.getBoolean(KEY_DAILY_SUMMARY, false),
            quietHoursEnabled = prefs.getBoolean(KEY_QUIET_HOURS, false),
            quietHoursStart = prefs.getInt(KEY_QUIET_START, 22),
            quietHoursEnd = prefs.getInt(KEY_QUIET_END, 8)
        )
    }
    
    private fun saveAndUpdate(update: NotificationPreferences.() -> NotificationPreferences) {
        viewModelScope.launch {
            val newPrefs = _preferences.value.update()
            _preferences.value = newPrefs
            savePreferences(newPrefs)
        }
    }
    
    private fun savePreferences(prefs: NotificationPreferences) {
        this.prefs.edit().apply {
            putBoolean(KEY_HABIT_REMINDERS, prefs.habitRemindersEnabled)
            putBoolean(KEY_FRIEND_ACTIVITY, prefs.friendActivityEnabled)
            putBoolean(KEY_GROUP_HABITS, prefs.groupHabitUpdatesEnabled)
            putBoolean(KEY_CHALLENGES, prefs.challengeUpdatesEnabled)
            putBoolean(KEY_ACHIEVEMENTS, prefs.achievementUnlockedEnabled)
            putBoolean(KEY_DAILY_SUMMARY, prefs.dailySummaryEnabled)
            putBoolean(KEY_QUIET_HOURS, prefs.quietHoursEnabled)
            putInt(KEY_QUIET_START, prefs.quietHoursStart)
            putInt(KEY_QUIET_END, prefs.quietHoursEnd)
            apply()
        }
    }
    
    fun setHabitRemindersEnabled(enabled: Boolean) = saveAndUpdate { copy(habitRemindersEnabled = enabled) }
    fun setFriendActivityEnabled(enabled: Boolean) = saveAndUpdate { copy(friendActivityEnabled = enabled) }
    fun setGroupHabitUpdatesEnabled(enabled: Boolean) = saveAndUpdate { copy(groupHabitUpdatesEnabled = enabled) }
    fun setChallengeUpdatesEnabled(enabled: Boolean) = saveAndUpdate { copy(challengeUpdatesEnabled = enabled) }
    fun setAchievementUnlockedEnabled(enabled: Boolean) = saveAndUpdate { copy(achievementUnlockedEnabled = enabled) }
    fun setDailySummaryEnabled(enabled: Boolean) = saveAndUpdate { copy(dailySummaryEnabled = enabled) }
    fun setQuietHoursEnabled(enabled: Boolean) = saveAndUpdate { copy(quietHoursEnabled = enabled) }
    fun setQuietHoursStart(hour: Int) = saveAndUpdate { copy(quietHoursStart = hour) }
    fun setQuietHoursEnd(hour: Int) = saveAndUpdate { copy(quietHoursEnd = hour) }
    
    companion object {
        private const val PREFS_NAME = "notification_settings"
        private const val KEY_HABIT_REMINDERS = "habit_reminders"
        private const val KEY_FRIEND_ACTIVITY = "friend_activity"
        private const val KEY_GROUP_HABITS = "group_habits"
        private const val KEY_CHALLENGES = "challenges"
        private const val KEY_ACHIEVEMENTS = "achievements"
        private const val KEY_DAILY_SUMMARY = "daily_summary"
        private const val KEY_QUIET_HOURS = "quiet_hours"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
    }
}
