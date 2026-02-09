package com.habittracker.presentation.viewmodel

import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.User
import com.habittracker.presentation.state.AddEditHabitUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DayOfWeek
import com.habittracker.domain.model.HabitTimeOfDay

interface IAddEditHabitViewModel {
    val uiState: StateFlow<AddEditHabitUiState>
    
    fun onTitleChange(title: String)
    fun onDescriptionChange(description: String)
    fun onTypeChange(type: HabitType)
    fun onGoalTypeChange(goalType: HabitGoalType)
    fun onFrequencyChange(frequency: HabitFrequency)
    fun onTimeOfDayToggle(timeOfDay: HabitTimeOfDay)
    fun onColorChange(color: Int)
    fun onIconChange(icon: String)
    fun onReminderTimeChange(time: LocalTime?)
    fun onReminderEnabledChange(enabled: Boolean)
    fun onReminderDayToggle(day: DayOfWeek)
    
    // Follow-up reminder
    fun onFollowUpReminderEnabledChange(enabled: Boolean)
    fun onFollowUpReminderDurationChange(duration: LocalTime)

    // Текстовый ввод для поля «Целевое значение» (поддержка промежуточных состояний)
    fun onTargetValueInputChange(text: String)
    fun onTargetValueChange(value: Int)
    fun onUnitChange(unit: String)
    fun saveHabit()
    fun loadHabit(habitId: String)
    
    // Методы для групповых привычек
    // Обработчик текстового ввода для поля «Максимум участников»
    fun onMaxParticipantsInputChange(text: String)
    fun onMaxParticipantsChange(maxParticipants: Int)
    fun onIsPublicChange(isPublic: Boolean)
    fun onRemoveFromPublicCatalogChange(remove: Boolean)
    fun onShowFriendSelector(show: Boolean)
    fun generateInviteCode()
    fun disableInviteCode()
    fun shareInviteCode(inviteCode: String)
    fun loadAvailableFriends()
    fun sendInvitations()
    fun requestInvitations()
    fun selectFriend(friend: User)
    fun deselectFriend(friend: User)
    
    // Методы для напоминаний
    fun scheduleReminder()
    fun cancelReminder()
    fun updateReminder()
    fun applyReminderToAllParticipants()
    
    // Методы для работы с датами привычек
    fun onStartDateChange(date: LocalDate?)
    fun onEndDateChange(date: LocalDate?)
    
    fun onErrorShown()
    fun onSuccessMessageShown()

    // Сброс прогресса для личной привычки
    fun resetPersonalHabitSeason()
    fun resetPersonalHabitFull()
}
