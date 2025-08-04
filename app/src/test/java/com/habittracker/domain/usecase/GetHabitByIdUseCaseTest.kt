package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.IOException

@ExperimentalCoroutinesApi
class GetHabitByIdUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitRepository: HabitRepository

    private lateinit var getHabitByIdUseCase: GetHabitByIdUseCase

    private val testHabitId = "test-habit-id"
    private val testHabit = Habit(
        id = testHabitId,
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
        getHabitByIdUseCase = GetHabitByIdUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should return habit from Firestore when available`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.getHabitById(testHabitId)).thenReturn(testHabit)

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertEquals(testHabit, result)
        Mockito.verify(firestoreRepository).getHabitById(testHabitId)
        Mockito.verifyNoInteractions(habitRepository)
    }

    @Test
    fun `invoke should return null when habit not found in Firestore`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.getHabitById(testHabitId)).thenReturn(null)

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertNull(result)
        Mockito.verify(firestoreRepository).getHabitById(testHabitId)
        Mockito.verifyNoInteractions(habitRepository)
    }

    @Test
    fun `invoke should fallback to local repository when Firestore fails`() = runTest {
        // Arrange
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getHabitById(testHabitId)
        Mockito.doReturn(testHabit).`when`(habitRepository).getHabitById(testHabitId)

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertEquals(testHabit, result)
        Mockito.verify(firestoreRepository).getHabitById(testHabitId)
        Mockito.verify(habitRepository).getHabitById(testHabitId)
    }

    @Test
    fun `invoke should return null when both Firestore and local fail`() = runTest {
        // Arrange
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getHabitById(testHabitId)
        Mockito.doReturn(null).`when`(habitRepository).getHabitById(testHabitId)

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertNull(result)
        Mockito.verify(firestoreRepository).getHabitById(testHabitId)
        Mockito.verify(habitRepository).getHabitById(testHabitId)
    }

    @Test
    fun `invoke should return null when both Firestore and local throw exceptions`() = runTest {
        // Arrange
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getHabitById(testHabitId)
        Mockito.doThrow(RuntimeException("Database error")).`when`(habitRepository).getHabitById(testHabitId)

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertNull(result)
        Mockito.verify(firestoreRepository).getHabitById(testHabitId)
        Mockito.verify(habitRepository).getHabitById(testHabitId)
    }
}
