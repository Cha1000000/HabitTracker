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
class AddHabitUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitRepository: HabitRepository

    private lateinit var addHabitUseCase: AddHabitUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Test Habit",
        description = "Test Description",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
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
        addHabitUseCase = AddHabitUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should save habit to Firestore and locally when Firestore succeeds`() = runTest {
        // Arrange
        Mockito.doNothing().`when`(firestoreRepository).saveHabit(testHabit)
        Mockito.doNothing().`when`(habitRepository).insertHabit(testHabit)

        // Act
        addHabitUseCase(testHabit)

        // Assert
        Mockito.verify(firestoreRepository).saveHabit(testHabit)
        Mockito.verify(habitRepository).insertHabit(testHabit)
    }

    @Test
    fun `invoke should save habit locally when Firestore fails`() = runTest {
        // Arrange
        val exception = IOException("Network error")
        Mockito.doThrow(exception).`when`(firestoreRepository).saveHabit(testHabit)
        Mockito.doNothing().`when`(habitRepository).insertHabit(testHabit)

        // Act & Assert
        try {
            addHabitUseCase(testHabit)
        } catch (e: Exception) {
            assertEquals(exception, e)
        }

        // Verify both repositories were called
        Mockito.verify(firestoreRepository).saveHabit(testHabit)
        Mockito.verify(habitRepository).insertHabit(testHabit)
    }

    @Test
    fun `invoke should throw exception when both Firestore and local storage fail`() = runTest {
        // Arrange
        val firestoreException = IOException("Network error")
        val localException = RuntimeException("Database error")
        Mockito.doThrow(firestoreException).`when`(firestoreRepository).saveHabit(testHabit)
        Mockito.doThrow(localException).`when`(habitRepository).insertHabit(testHabit)

        // Act & Assert
        try {
            addHabitUseCase(testHabit)
        } catch (e: Exception) {
            assertEquals(firestoreException, e)
        }

        Mockito.verify(firestoreRepository).saveHabit(testHabit)
        Mockito.verify(habitRepository).insertHabit(testHabit)
    }
}
