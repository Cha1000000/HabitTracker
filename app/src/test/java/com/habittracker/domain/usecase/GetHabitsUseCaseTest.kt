package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class GetHabitsUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitRepository: HabitRepository

    private lateinit var getHabitsUseCase: GetHabitsUseCase

    private val testUserId = "test-user-id"
    private val testHabits = listOf(
        Habit(
            id = "habit-1",
            title = "Morning Run",
            description = "Daily morning exercise",
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
            createdBy = testUserId
        ),
        Habit(
            id = "habit-2",
            title = "Read Book",
            description = "Read for 30 minutes",
            icon = "📚",
            color = "#4CAF50",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.DAILY,
            reminderTime = null,
            targetValue = 30,
            unit = "мин",
            participants = emptyList(),
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = testUserId
        )
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        getHabitsUseCase = GetHabitsUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should return habits from Firestore when available`() = runTest {
        // Arrange
        val firestoreFlow = flowOf(testHabits)
        Mockito.`when`(firestoreRepository.getUserHabits(testUserId)).thenReturn(firestoreFlow)

        // Act
        val result = getHabitsUseCase(testUserId).first()

        // Assert
        assertEquals(testHabits, result)
        Mockito.verify(firestoreRepository).getUserHabits(testUserId)
        Mockito.verifyNoInteractions(habitRepository)
    }

    @Test
    fun `invoke should return habits from local repository when Firestore fails`() = runTest {
        // Arrange
        val localFlow = flowOf(testHabits)
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getUserHabits(testUserId)
        Mockito.doReturn(localFlow).`when`(habitRepository).getHabitsByUserId(testUserId)

        // Act
        val result = getHabitsUseCase(testUserId).first()

        // Assert
        assertEquals(testHabits, result)
        Mockito.verify(firestoreRepository).getUserHabits(testUserId)
        Mockito.verify(habitRepository).getHabitsByUserId(testUserId)
    }

    @Test
    fun `invoke should return empty list when both Firestore and local fail`() = runTest {
        // Arrange
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getUserHabits(testUserId)
        Mockito.doThrow(RuntimeException("Database error")).`when`(habitRepository).getHabitsByUserId(testUserId)

        // Act
        val result = getHabitsUseCase(testUserId).first()

        // Assert
        assertEquals(emptyList<Habit>(), result)
        Mockito.verify(firestoreRepository).getUserHabits(testUserId)
        Mockito.verify(habitRepository).getHabitsByUserId(testUserId)
    }
}
