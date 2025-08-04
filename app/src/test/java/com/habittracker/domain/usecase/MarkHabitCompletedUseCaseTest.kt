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
class MarkHabitCompletedUseCaseTest {

    @Mock
    private lateinit var firestoreRepository: FirestoreRepository

    @Mock
    private lateinit var habitEntryRepository: HabitEntryRepository

    private lateinit var markHabitCompletedUseCase: MarkHabitCompletedUseCase

    private val testHabitId = "test-habit-id"
    private val testUserId = "test-user-id"
    private val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val testNote = "Completed successfully"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        markHabitCompletedUseCase = MarkHabitCompletedUseCase(firestoreRepository, habitEntryRepository)
    }

    @Test
    fun `invoke should save habit entry to Firestore and locally when Firestore succeeds`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenReturn(Unit)
        Mockito.`when`(habitEntryRepository.markHabitCompleted(testHabitId, testUserId, testDate, testNote))
            .thenReturn(Unit)

        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)

        // Assert
        val habitEntryCaptor = ArgumentCaptor.forClass(HabitEntry::class.java)
        Mockito.verify(firestoreRepository).saveHabitEntry(habitEntryCaptor.capture())
        Mockito.verify(habitEntryRepository).markHabitCompleted(testHabitId, testUserId, testDate, testNote)

        val capturedEntry = habitEntryCaptor.value
        assertEquals(testHabitId, capturedEntry.habitId)
        assertEquals(testUserId, capturedEntry.userId)
        assertEquals(testDate, capturedEntry.date)
        assertTrue(capturedEntry.completed)
        assertEquals(testNote, capturedEntry.note)
        assertNotNull(capturedEntry.completedAt)
        assertNotNull(capturedEntry.createdAt)
        assertNotNull(capturedEntry.updatedAt)
    }

    @Test
    fun `invoke should save habit entry locally when Firestore fails`() = runTest {
        // Arrange
        val exception = IOException("Network error")
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenThrow(exception)
        Mockito.`when`(habitEntryRepository.markHabitCompleted(testHabitId, testUserId, testDate, testNote))
            .thenReturn(Unit)

        // Act & Assert
        try {
            markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)
        } catch (e: Exception) {
            assertEquals(exception, e)
        }

        // Verify both repositories were called
        Mockito.verify(firestoreRepository).saveHabitEntry(Mockito.any())
        Mockito.verify(habitEntryRepository).markHabitCompleted(testHabitId, testUserId, testDate, testNote)
    }

    @Test
    fun `invoke should use current date when no date provided`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenReturn(Unit)
        Mockito.`when`(habitEntryRepository.markHabitCompleted(
            Mockito.eq(testHabitId), 
            Mockito.eq(testUserId), 
            Mockito.any(), 
            Mockito.eq(testNote)
        )).thenReturn(Unit)

        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, note = testNote)

        // Assert
        val habitEntryCaptor = ArgumentCaptor.forClass(HabitEntry::class.java)
        Mockito.verify(firestoreRepository).saveHabitEntry(habitEntryCaptor.capture())

        val capturedEntry = habitEntryCaptor.value
        assertEquals(testHabitId, capturedEntry.habitId)
        assertEquals(testUserId, capturedEntry.userId)
        assertTrue(capturedEntry.completed)
        assertEquals(testNote, capturedEntry.note)
        // Date should be today (within reasonable margin)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        assertEquals(today, capturedEntry.date)
    }

    @Test
    fun `invoke should handle null note parameter`() = runTest {
        // Arrange
        Mockito.`when`(firestoreRepository.saveHabitEntry(Mockito.any())).thenReturn(Unit)
        Mockito.`when`(habitEntryRepository.markHabitCompleted(testHabitId, testUserId, testDate, null))
            .thenReturn(Unit)

        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, null)

        // Assert
        val habitEntryCaptor = ArgumentCaptor.forClass(HabitEntry::class.java)
        Mockito.verify(firestoreRepository).saveHabitEntry(habitEntryCaptor.capture())
        Mockito.verify(habitEntryRepository).markHabitCompleted(testHabitId, testUserId, testDate, null)

        val capturedEntry = habitEntryCaptor.value
        assertNull(capturedEntry.note)
    }
}
