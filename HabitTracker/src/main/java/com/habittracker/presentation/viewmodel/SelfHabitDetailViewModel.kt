package com.habittracker.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.isActiveOnDate
import com.habittracker.domain.model.isActiveOnDayOfWeek
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.habit.AttachPhotoToEntryUseCase
import com.habittracker.domain.usecase.reset.ResetProgressUseCase
import com.habittracker.presentation.state.SelfHabitDetailUiState
import com.habittracker.presentation.widget.TodayHabitsWidgetScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SelfHabitDetailViewModel @Inject constructor(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase,
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase,
    private val attachPhotoToEntryUseCase: AttachPhotoToEntryUseCase,
    private val resetProgressUseCase: ResetProgressUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SelfHabitDetailUiState())
    val uiState: StateFlow<SelfHabitDetailUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = authRepository.getCurrentUserId() ?: ""

    private var entriesCollectionJob: Job? = null
    private var currentSelectedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    fun loadHabitDetails(habitId: String, selectedDate: LocalDate) {
        currentSelectedDate = selectedDate
        entriesCollectionJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentUserId = currentUserId) }
            try {
                val habit = getHabitByIdUseCase(habitId)
                if (habit == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = application.getString(R.string.error_habit_not_found)
                        )
                    }
                    return@launch
                }
                if (habit.isGroupHabit()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = application.getString(R.string.error_loading_habit_details)
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(habit = habit, isLoading = false) }
                collectHabitEntries(habit, selectedDate)
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed to load habit details")
                _uiState.update {
                    it.copy(isLoading = false, error = application.getString(R.string.error_loading_habit_details))
                }
            }
        }
    }

    private fun collectHabitEntries(habit: com.habittracker.domain.model.Habit, selectedDate: LocalDate) {
        entriesCollectionJob?.cancel()
        entriesCollectionJob = viewModelScope.launch {
            habitEntryRepository.getEntriesForUserAndHabit(currentUserId, habit.id)
                .catch { e ->
                    Timber.tag("SelfHabitDetailVM").e(e, "Error collecting habit entries")
                }
                .collect { entries ->
                    val selectedDayEntry = entries.firstOrNull { it.date == selectedDate }
                    val entriesUpToSelectedDate = entries.filter { it.date <= selectedDate }
                    val completedDays = entriesUpToSelectedDate.count { it.completed }
                    val activeDays = calculateActiveDays(habit, entriesUpToSelectedDate, selectedDate)
                    val completionRate = if (activeDays > 0) ((completedDays * 100f) / activeDays).toInt() else 0

                    _uiState.update { state ->
                        state.copy(
                            habitEntries = entries,
                            isCompletedToday = selectedDayEntry?.completed == true,
                            currentUserPhotoUrl = if (state.isUploadingPhoto) state.currentUserPhotoUrl else selectedDayEntry?.photoUrl,
                            currentUserNote = selectedDayEntry?.note,
                            currentStreak = calculateCurrentStreak(entries, selectedDate),
                            totalCompletedDays = completedDays,
                            completionRatePercent = completionRate
                        )
                    }
                }
        }
    }

    fun toggleHabitCompletion() {
        val habit = _uiState.value.habit ?: return
        val isCompleted = _uiState.value.isCompletedToday
        viewModelScope.launch {
            try {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                if (currentSelectedDate > today) {
                    _uiState.update {
                        it.copy(error = application.getString(R.string.error_cannot_mark_future_habit))
                    }
                    return@launch
                }
                _uiState.update { it.copy(isMarkingCompletion = true) }
                if (isCompleted) {
                    markHabitIncompleteUseCase(habit.id, currentUserId, currentSelectedDate)
                } else {
                    markHabitCompletedUseCase(habit.id, currentUserId, currentSelectedDate)
                }
                _uiState.update {
                    it.copy(
                        isMarkingCompletion = false,
                        isCompletedToday = !isCompleted,
                        showPhotoProofPrompt = !isCompleted,
                        successMessage = if (!isCompleted) {
                            application.getString(R.string.habit_marked_complete)
                        } else {
                            application.getString(R.string.habit_marked_incomplete)
                        }
                    )
                }
                TodayHabitsWidgetScheduler.enqueueRefresh(application)
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed to toggle completion")
                _uiState.update {
                    it.copy(
                        isMarkingCompletion = false,
                        error = application.getString(R.string.error_updating_habit_status)
                    )
                }
            }
        }
    }

    fun showPhotoSheet() {
        _uiState.update { it.copy(showPhotoSheet = true) }
    }

    fun hidePhotoSheet() {
        _uiState.update { it.copy(showPhotoSheet = false) }
    }

    fun dismissPhotoProofPrompt() {
        _uiState.update { it.copy(showPhotoProofPrompt = false) }
    }

    fun attachPhoto(uri: Uri) {
        val habit = _uiState.value.habit ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true) }
            try {
                val result = attachPhotoToEntryUseCase(
                    sourceUri = uri,
                    habitId = habit.id,
                    userId = currentUserId,
                    date = currentSelectedDate
                )
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isUploadingPhoto = false,
                            currentUserPhotoUrl = result.getOrNull(),
                            photoUploadSuccessMessage = application.getString(R.string.photo_proof_upload_success),
                            successMessage = application.getString(R.string.photo_proof_upload_success)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isUploadingPhoto = false, error = application.getString(R.string.photo_proof_upload_error))
                    }
                }
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed to attach photo")
                _uiState.update {
                    it.copy(isUploadingPhoto = false, error = application.getString(R.string.photo_proof_upload_error))
                }
            }
        }
    }

    fun deletePhoto() {
        val habit = _uiState.value.habit ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true) }
            try {
                habitEntryRepository.updatePhotoUrl(habit.id, currentUserId, currentSelectedDate, null)
                _uiState.update { it.copy(isUploadingPhoto = false, currentUserPhotoUrl = null) }
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed to delete photo")
                _uiState.update {
                    it.copy(isUploadingPhoto = false, error = application.getString(R.string.photo_proof_delete_error))
                }
            }
        }
    }

    fun updateHabitEntryNote(note: String) {
        val habitId = _uiState.value.habit?.id ?: return
        viewModelScope.launch {
            try {
                val entry = habitEntryRepository.getEntry(habitId, currentUserId, currentSelectedDate)
                if (entry != null) {
                    habitEntryRepository.updateEntry(entry.copy(note = note))
                } else {
                    habitEntryRepository.insertEntry(
                        HabitEntry(
                            habitId = habitId,
                            userId = currentUserId,
                            date = currentSelectedDate,
                            completed = false,
                            note = note
                        )
                    )
                }
                _uiState.update {
                    it.copy(photoUploadSuccessMessage = application.getString(R.string.photo_proof_note_success))
                }
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed to update note")
            }
        }
    }

    fun clearPhotoUploadSuccessMessage() {
        _uiState.update { it.copy(photoUploadSuccessMessage = null) }
    }

    fun resetPersonalHabitSeason() {
        val habitId = _uiState.value.habit?.id ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                resetProgressUseCase.resetPersonalHabitSeason(habitId, currentUserId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = application.getString(R.string.reset_personal_habit_season_success)
                    )
                }
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed season reset")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.reset_personal_habit_season_error)
                    )
                }
            }
        }
    }

    fun resetPersonalHabitFull() {
        val habitId = _uiState.value.habit?.id ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                resetProgressUseCase.resetPersonalHabitFull(habitId, currentUserId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = application.getString(R.string.reset_personal_habit_full_success)
                    )
                }
            } catch (e: Exception) {
                Timber.tag("SelfHabitDetailVM").e(e, "Failed full reset")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.reset_personal_habit_full_error)
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun calculateCurrentStreak(entries: List<HabitEntry>, selectedDate: LocalDate): Int {
        if (entries.isEmpty()) return 0
        val completedByDate = entries.filter { it.completed }.associateBy { it.date }
        var date = selectedDate
        var streak = 0

        if (!completedByDate.containsKey(date)) {
            date = date.minus(DatePeriod(days = 1))
        }

        while (completedByDate.containsKey(date)) {
            streak++
            date = date.minus(DatePeriod(days = 1))
            if (streak > 365) break
        }
        return streak
    }

    private fun calculateActiveDays(
        habit: com.habittracker.domain.model.Habit,
        entriesUpToSelectedDate: List<HabitEntry>,
        selectedDate: LocalDate
    ): Int {
        val restDaysByDate = entriesUpToSelectedDate
            .filter { it.isRestDay }
            .map { it.date }
            .toSet()

        val startDate = habit.startDate ?: habit.createdAt
        if (selectedDate < startDate) return 0

        var currentDate = startDate
        var activeDays = 0
        while (currentDate <= selectedDate) {
            val isActive = habit.isActiveOnDate(currentDate, habit.createdAt) &&
                habit.isActiveOnDayOfWeek(currentDate.dayOfWeek)
            if (isActive && currentDate !in restDaysByDate) {
                activeDays++
            }
            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
        }
        return activeDays
    }

    override fun onCleared() {
        super.onCleared()
        entriesCollectionJob?.cancel()
    }
}
