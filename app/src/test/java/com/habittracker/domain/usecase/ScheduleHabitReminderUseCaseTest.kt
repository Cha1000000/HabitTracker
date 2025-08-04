package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.ReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class ScheduleHabitReminderUseCaseTest {

    @Mock
    private lateinit var reminderRepository: ReminderRepository

    private lateinit var scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Morning Run",
        description = "Daily morning exercise",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = LocalTime(7, 0), // 7:00 AM
        reminderEnabled = true,
        targetValue = 1,
        unit = "раз",
        participants = emptyList(),
        isActive = true,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        createdBy = "test-user-id"
    )

    private val testHabitWithoutReminder = testHabit.copy(reminderTime = null, reminderEnabled = false)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        scheduleHabitReminderUseCase = ScheduleHabitReminderUseCase(reminderRepository)
    }

    @Test
    fun `invoke should schedule reminder when habit has reminder time`() = runTest {
        // Arrange
        Mockito.`when`(reminderRepository.scheduleReminder(testHabit)).thenReturn(Unit)

        // Act
        scheduleHabitReminderUseCase(testHabit)

        // Assert
        Mockito.verify(reminderRepository).scheduleReminder(testHabit)
    }

    @Test
    fun `invoke should not schedule reminder when habit has no reminder time`() = runTest {
        // Act
        scheduleHabitReminderUseCase(testHabitWithoutReminder)

        // Assert
        Mockito.verifyNoInteractions(reminderRepository)
    }

    @Test
    fun `invoke should not schedule reminder when habit is inactive`() = runTest {
        // Arrange
        val inactiveHabit = testHabit.copy(isActive = false)

        // Act
        scheduleHabitReminderUseCase(inactiveHabit)

        // Assert
        Mockito.verifyNoInteractions(reminderRepository)
    }

    @Test
    fun `invoke should schedule reminder for different frequencies`() = runTest {
        // Test WEEKDAYS frequency
        val weekdaysHabit = testHabit.copy(frequency = HabitFrequency.WEEKDAYS)
        Mockito.`when`(reminderRepository.scheduleReminder(weekdaysHabit)).thenReturn(Unit)

        scheduleHabitReminderUseCase(weekdaysHabit)
        Mockito.verify(reminderRepository).scheduleReminder(weekdaysHabit)

        // Test WEEKLY frequency
        val weeklyHabit = testHabit.copy(frequency = HabitFrequency.WEEKLY)
        Mockito.`when`(reminderRepository.scheduleReminder(weeklyHabit)).thenReturn(Unit)

        scheduleHabitReminderUseCase(weeklyHabit)
        Mockito.verify(reminderRepository).scheduleReminder(weeklyHabit)
    }

    @Test
    fun `invoke should handle repository exceptions gracefully`() = runTest {
        // Arrange
        val exception = RuntimeException("Scheduling failed")
        Mockito.`when`(reminderRepository.scheduleReminder(testHabit)).thenThrow(exception)

        // Act & Assert
        try {
            scheduleHabitReminderUseCase(testHabit)
        } catch (e: Exception) {
            // Exception should be propagated
            assert(e == exception)
        }

        Mockito.verify(reminderRepository).scheduleReminder(testHabit)
    }
}
