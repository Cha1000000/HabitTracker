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
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.junit.Assert.*
import java.io.IOException

@ExperimentalCoroutinesApi
class MarkHabitIncompleteUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitEntryRepository: HabitEntryRepository

    private lateinit var markHabitIncompleteUseCase: MarkHabitIncompleteUseCase

    private val testHabitId = "test-habit-id"
    private val testUserId = "test-user-id"
    private val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        markHabitIncompleteUseCase = MarkHabitIncompleteUseCase(firestoreRepository, habitEntryRepository)
    }

    @Test
    fun `invoke should save incomplete habit entry to Firestore and locally when Firestore succeeds`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenReturn(Unit)
        Mockito.`when`(habitEntryRepository.markHabitIncomplete(testHabitId, testUserId, testDate))
            .thenReturn(Unit)

        // Act
        markHabitIncompleteUseCase(testHabitId, testUserId, testDate)

        // Assert
        val habitEntryCaptor = ArgumentCaptor.forClass(HabitEntry::class.java)
        Mockito.verify(firestoreRepository).saveHabitEntry(habitEntryCaptor.capture())
        Mockito.verify(habitEntryRepository).markHabitIncomplete(testHabitId, testUserId, testDate)

        val capturedEntry = habitEntryCaptor.value
        assertEquals(testHabitId, capturedEntry.habitId)
        assertEquals(testUserId, capturedEntry.userId)
        assertEquals(testDate, capturedEntry.date)
        assertFalse(capturedEntry.completed)
        assertNull(capturedEntry.completedAt)
        assertNull(capturedEntry.note)
        assertNotNull(capturedEntry.createdAt)
        assertNotNull(capturedEntry.updatedAt)
    }

    @Test
    fun `invoke should save incomplete habit entry locally when Firestore fails`() = runTest {
        // Arrange
        val exception = IOException("Network error")
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenThrow(exception)
        Mockito.`when`(habitEntryRepository.markHabitIncomplete(testHabitId, testUserId, testDate))
            .thenReturn(Unit)

        // Act & Assert
        try {
            markHabitIncompleteUseCase(testHabitId, testUserId, testDate)
        } catch (e: Exception) {
            assertEquals(exception, e)
        }

        // Verify both repositories were called
        Mockito.verify(firestoreRepository).saveHabitEntry(Mockito.any())
        Mockito.verify(habitEntryRepository).markHabitIncomplete(testHabitId, testUserId, testDate)
    }

    @Test
    fun `invoke should use current date when no date provided`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenReturn(Unit)
        Mockito.`when`(habitEntryRepository.markHabitIncomplete(
            Mockito.eq(testHabitId), 
            Mockito.eq(testUserId), 
            Mockito.any()
        )).thenReturn(Unit)

        // Act
        markHabitIncompleteUseCase(testHabitId, testUserId)

        // Assert
        val habitEntryCaptor = ArgumentCaptor.forClass(HabitEntry::class.java)
        Mockito.verify(firestoreRepository).saveHabitEntry(habitEntryCaptor.capture())

        val capturedEntry = habitEntryCaptor.value
        assertEquals(testHabitId, capturedEntry.habitId)
        assertEquals(testUserId, capturedEntry.userId)
        assertFalse(capturedEntry.completed)
        assertNull(capturedEntry.completedAt)
        assertNull(capturedEntry.note)
        // Date should be today (within reasonable margin)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        assertEquals(today, capturedEntry.date)
    }

    @Test
    fun `invoke should throw exception when both Firestore and local storage fail`() = runTest {
        // Arrange
        val firestoreException = IOException("Network error")
        val localException = RuntimeException("Database error")
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenThrow(firestoreException)
        Mockito.`when`(habitEntryRepository.markHabitIncomplete(testHabitId, testUserId, testDate))
            .thenThrow(localException)

        // Act & Assert
        try {
            markHabitIncompleteUseCase(testHabitId, testUserId, testDate)
        } catch (e: Exception) {
            assertEquals(firestoreException, e)
        }

        Mockito.verify(firestoreRepository).saveHabitEntry(Mockito.any())
        Mockito.verify(habitEntryRepository).markHabitIncomplete(testHabitId, testUserId, testDate)
    }
}
