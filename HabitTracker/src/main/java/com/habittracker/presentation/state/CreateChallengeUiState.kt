package com.habittracker.presentation.state

import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class CreateChallengeUiState(
    val title: String = "",
    val description: String = "",
    val selectedHabit: Habit? = null,
    val availableHabits: List<Habit> = emptyList(),
    val selectedParticipants: List<User> = emptyList(),
    val availableFriends: List<User> = emptyList(),
    val startDate: LocalDate = Clock.System.todayIn(TimeZone.Companion.currentSystemDefault()),
    val endDate: LocalDate = Clock.System.todayIn(TimeZone.Companion.currentSystemDefault()).plus(7, DateTimeUnit.Companion.DAY),
    val challengeType: ChallengeType = ChallengeType.COMPLETION_RATE,
    val targetValue: String = "100",
    val allowMissedDays: String = "0",
    val requireConsecutive: Boolean = false,
    val reward: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showHabitSelector: Boolean = false,
    val showParticipantSelector: Boolean = false,
    val showDatePicker: Boolean = false,
    val datePickerType: DatePickerType = DatePickerType.START_DATE,
    val isCreating: Boolean = false
)

enum class DatePickerType {
    START_DATE,
    END_DATE
}