package com.habittracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.habittracker.domain.model.UserPreferences
import com.habittracker.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val SORT_COMPLETED_BOTTOM = booleanPreferencesKey("sort_completed_bottom")
        val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
        val DAILY_REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute")
    }

    override val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            val sortBottom = preferences[PreferencesKeys.SORT_COMPLETED_BOTTOM] ?: true
            val reminderEnabled = preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] ?: true
            val hour = preferences[PreferencesKeys.DAILY_REMINDER_HOUR] ?: 19
            val minute = preferences[PreferencesKeys.DAILY_REMINDER_MINUTE] ?: 0
            
            UserPreferences(
                sortCompletedHabitsToBottom = sortBottom,
                dailyReminderEnabled = reminderEnabled,
                dailyReminderTime = LocalTime(hour, minute)
            )
        }

    override suspend fun updateSortCompletedHabitsToBottom(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_COMPLETED_BOTTOM] = enabled
        }
    }

    override suspend fun updateDailyReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] = enabled
        }
    }

    override suspend fun updateDailyReminderTime(time: LocalTime) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_REMINDER_HOUR] = time.hour
            preferences[PreferencesKeys.DAILY_REMINDER_MINUTE] = time.minute
        }
    }
}
