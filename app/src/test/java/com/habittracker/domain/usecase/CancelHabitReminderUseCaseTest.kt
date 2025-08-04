package com.habittracker.domain.usecase

import com.habittracker.domain.repository.ReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class CancelHabitReminderUseCaseTest {

    @Mock
    private lateinit var reminderRepository: ReminderRepository

    private lateinit var cancelHabitReminderUseCase: CancelHabitReminderUseCase

    private val testHabitId = "test-habit-id"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        cancelHabitReminderUseCase = CancelHabitReminderUseCase(reminderRepository)
    }

    @Test
    fun `invoke should cancel habit reminder successfully`() = runTest {
        // Arrange
        Mockito.`when`(reminderRepository.cancelReminder(testHabitId)).thenReturn(Unit)

        // Act
        cancelHabitReminderUseCase(testHabitId)

        // Assert
        Mockito.verify(reminderRepository).cancelReminder(testHabitId)
    }

    @Test
    fun `invoke should handle repository exceptions gracefully`() = runTest {
        // Arrange
        val exception = RuntimeException("Cancellation failed")
        Mockito.`when`(reminderRepository.cancelReminder(testHabitId)).thenThrow(exception)

        // Act & Assert
        try {
            cancelHabitReminderUseCase(testHabitId)
        } catch (e: Exception) {
            // Exception should be propagated
            assert(e == exception)
        }

        Mockito.verify(reminderRepository).cancelReminder(testHabitId)
    }

    @Test
    fun `invoke should work with empty habit ID`() = runTest {
        // Arrange
        val emptyHabitId = ""
        Mockito.`when`(reminderRepository.cancelReminder(emptyHabitId)).thenReturn(Unit)

        // Act
        cancelHabitReminderUseCase(emptyHabitId)

        // Assert
        Mockito.verify(reminderRepository).cancelReminder(emptyHabitId)
    }
}
