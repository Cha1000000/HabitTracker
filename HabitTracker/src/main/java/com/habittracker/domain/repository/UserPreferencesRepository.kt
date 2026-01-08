package com.habittracker.domain.repository

import com.habittracker.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalTime

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    
    suspend fun updateSortCompletedHabitsToBottom(enabled: Boolean)
    suspend fun updateDailyReminderEnabled(enabled: Boolean)
    suspend fun updateDailyReminderTime(time: LocalTime)
}
