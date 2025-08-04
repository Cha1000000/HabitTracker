package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.IOException

@ExperimentalCoroutinesApi
class UpdateHabitUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitRepository: HabitRepository

    private lateinit var updateHabitUseCase: UpdateHabitUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Updated Habit",
        description = "Updated Description",
        icon = "🏃",
        color = "#4CAF50",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.WEEKDAYS,
        reminderTime = null,
        targetValue = 2,
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
        updateHabitUseCase = UpdateHabitUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should update habit in Firestore and locally when Firestore succeeds`() = runTest {
        // Arrange
        Mockito.doNothing().`when`(firestoreRepository).updateHabit(testHabit)
        Mockito.doNothing().`when`(habitRepository).updateHabit(testHabit)

        // Act
        updateHabitUseCase(testHabit)

        // Assert
        Mockito.verify(firestoreRepository).updateHabit(testHabit)
        Mockito.verify(habitRepository).updateHabit(testHabit)
    }

    @Test
    fun `invoke should update habit locally when Firestore fails`() = runTest {
        // Arrange
        val exception = IOException("Network error")
        Mockito.doThrow(exception).`when`(firestoreRepository).updateHabit(testHabit)
        Mockito.doNothing().`when`(habitRepository).updateHabit(testHabit)

        // Act & Assert
        try {
            updateHabitUseCase(testHabit)
        } catch (e: Exception) {
            assertEquals(exception, e)
        }

        // Verify both repositories were called
        Mockito.verify(firestoreRepository).updateHabit(testHabit)
        Mockito.verify(habitRepository).updateHabit(testHabit)
    }

    @Test
    fun `invoke should throw exception when both Firestore and local storage fail`() = runTest {
        // Arrange
        val firestoreException = IOException("Network error")
        val localException = RuntimeException("Database error")
        Mockito.doThrow(firestoreException).`when`(firestoreRepository).updateHabit(testHabit)
        Mockito.doThrow(localException).`when`(habitRepository).updateHabit(testHabit)

        // Act & Assert
        try {
            updateHabitUseCase(testHabit)
        } catch (e: Exception) {
            assertEquals(firestoreException, e)
        }

        Mockito.verify(firestoreRepository).updateHabit(testHabit)
        Mockito.verify(habitRepository).updateHabit(testHabit)
    }
}
