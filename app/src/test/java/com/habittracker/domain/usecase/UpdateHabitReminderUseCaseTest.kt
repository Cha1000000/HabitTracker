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
class UpdateHabitReminderUseCaseTest {

    @Mock
    private lateinit var reminderRepository: ReminderRepository

    private lateinit var updateHabitReminderUseCase: UpdateHabitReminderUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Morning Run",
        description = "Daily morning exercise",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = LocalTime(8, 0), // 8:00 AM
        reminderEnabled = true,
        targetValue = 1,
        unit = "раз",
        participants = emptyList(),
        isActive = true,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        createdBy = "test-user-id"
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        updateHabitReminderUseCase = UpdateHabitReminderUseCase(reminderRepository)
    }

    @Test
    fun `invoke should update habit reminder successfully`() = runTest {
        // Arrange
        Mockito.`when`(reminderRepository.scheduleReminder(testHabit)).thenReturn(Unit)

        // Act
        updateHabitReminderUseCase(testHabit)

        // Assert
        Mockito.verify(reminderRepository).scheduleReminder(testHabit)
    }

    @Test
    fun `invoke should handle repository exceptions gracefully`() = runTest {
        // Arrange
        val exception = RuntimeException("Update failed")
        Mockito.`when`(reminderRepository.scheduleReminder(testHabit)).thenThrow(exception)

        // Act & Assert
        try {
            updateHabitReminderUseCase(testHabit)
        } catch (e: Exception) {
            assert(e == exception)
        }

        Mockito.verify(reminderRepository).scheduleReminder(testHabit)
    }

    @Test
    fun `invoke should work with habit without reminder time`() = runTest {
        // Arrange
        val habitWithoutReminder = testHabit.copy(reminderTime = null)
        Mockito.`when`(reminderRepository.scheduleReminder(habitWithoutReminder)).thenReturn(Unit)

        // Act
        updateHabitReminderUseCase(habitWithoutReminder)

        // Assert
        Mockito.verify(reminderRepository).scheduleReminder(habitWithoutReminder)
    }
}
