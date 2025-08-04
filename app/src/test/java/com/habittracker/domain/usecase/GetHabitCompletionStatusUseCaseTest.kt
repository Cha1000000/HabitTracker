package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.IOException

@ExperimentalCoroutinesApi
class GetHabitCompletionStatusUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitEntryRepository: HabitEntryRepository

    private lateinit var getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase

    private val testHabitId = "test-habit-id"
    private val testUserId = "test-user-id"
    private val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val completedEntry = HabitEntry(
        habitId = testHabitId,
        userId = testUserId,
        date = testDate,
        completed = true,
        completedAt = Clock.System.now(),
        note = "Test note",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private val incompleteEntry = HabitEntry(
        habitId = testHabitId,
        userId = testUserId,
        date = testDate,
        completed = false,
        completedAt = null,
        note = null,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        getHabitCompletionStatusUseCase = GetHabitCompletionStatusUseCase(firestoreRepository, habitEntryRepository)
    }

    @Test
    fun `invoke should return true when habit is completed in Firestore`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.getHabitEntry(testHabitId, testUserId, testDate))
            .thenReturn(completedEntry)

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertTrue(result)
        Mockito.verify(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.verifyNoInteractions(habitEntryRepository)
    }

    @Test
    fun `invoke should return false when habit is not completed in Firestore`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.getHabitEntry(testHabitId, testUserId, testDate))
            .thenReturn(incompleteEntry)

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertFalse(result)
        Mockito.verify(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.verifyNoInteractions(habitEntryRepository)
    }

    @Test
    fun `invoke should return false when no entry exists in Firestore`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.getHabitEntry(testHabitId, testUserId, testDate))
            .thenReturn(null)

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertFalse(result)
        Mockito.verify(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.verifyNoInteractions(habitEntryRepository)
    }

    @Test
    fun `invoke should fallback to local repository when Firestore fails`() = runTest {
        // Arrange
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.doReturn(completedEntry).`when`(habitEntryRepository).getEntry(testHabitId, testUserId, testDate)

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertTrue(result)
        Mockito.verify(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.verify(habitEntryRepository).getEntry(testHabitId, testUserId, testDate)
    }

    @Test
    fun `invoke should return false when both Firestore and local fail`() = runTest {
        // Arrange
        Mockito.doThrow(IOException("Network error")).`when`(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.doReturn(null).`when`(habitEntryRepository).getEntry(testHabitId, testUserId, testDate)

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertFalse(result)
        Mockito.verify(firestoreRepository).getHabitEntry(testHabitId, testUserId, testDate)
        Mockito.verify(habitEntryRepository).getEntry(testHabitId, testUserId, testDate)
    }

    @Test
    fun `invoke should use current date when no date provided`() = runTest {
        // Arrange
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        Mockito.`when`(firestoreRepository.getHabitEntry(testHabitId, testUserId, today))
            .thenReturn(completedEntry)

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId)

        // Assert
        assertTrue(result)
        Mockito.verify(firestoreRepository).getHabitEntry(testHabitId, testUserId, today)
    }
}
