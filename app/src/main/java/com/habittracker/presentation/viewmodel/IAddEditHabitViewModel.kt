package com.habittracker.presentation.viewmodel

import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalTime

interface IAddEditHabitViewModel {
    val uiState: StateFlow<AddEditHabitUiState>
    
    fun onTitleChange(title: String)
    fun onDescriptionChange(description: String)
    fun onTypeChange(type: HabitType)
    fun onFrequencyChange(frequency: HabitFrequency)
    fun onColorChange(color: Int)
    fun onIconChange(icon: String)
    fun onReminderTimeChange(time: LocalTime?)
    fun onReminderEnabledChange(enabled: Boolean)
    fun onTargetValueChange(value: Int)
    fun onUnitChange(unit: String)
    fun saveHabit()
    fun loadHabit(habitId: String)
    
    // Методы для групповых привычек
    // Обработчик текстового ввода для поля «Максимум участников»
    fun onMaxParticipantsInputChange(text: String)
    fun onMaxParticipantsChange(maxParticipants: Int)
    fun onIsPublicChange(isPublic: Boolean)
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
    
    fun onErrorShown()
    fun onSuccessMessageShown()
}
