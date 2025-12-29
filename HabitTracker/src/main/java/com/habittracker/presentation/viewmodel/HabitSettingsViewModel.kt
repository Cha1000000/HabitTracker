package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.repository.UserPreferencesRepository
import com.habittracker.domain.scheduler.DailyReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import javax.inject.Inject

@HiltViewModel
class HabitSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dailyReminderScheduler: DailyReminderScheduler
) : ViewModel() {

    val userPreferences = userPreferencesRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateSortCompletedHabitsToBottom(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateSortCompletedHabitsToBottom(enabled)
        }
    }

    fun updateDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDailyReminderEnabled(enabled)
            val preferences = userPreferences.value
            if (preferences != null) {
                if (enabled) {
                    dailyReminderScheduler.schedule(preferences.dailyReminderTime)
                } else {
                    dailyReminderScheduler.cancel()
                }
            }
        }
    }

    fun updateDailyReminderTime(time: LocalTime) {
        viewModelScope.launch {
            userPreferencesRepository.updateDailyReminderTime(time)
            val preferences = userPreferences.value
            // If currently enabled, reschedule with new time
            if (preferences?.dailyReminderEnabled == true) {
                dailyReminderScheduler.schedule(time)
            }
        }
    }
}
