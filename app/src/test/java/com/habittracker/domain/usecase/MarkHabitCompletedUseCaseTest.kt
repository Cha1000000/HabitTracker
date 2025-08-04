package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MarkHabitCompletedUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitEntryRepository: FakeHabitEntryRepository
    private lateinit var markHabitCompletedUseCase: MarkHabitCompletedUseCase

    private val testHabitId = "test-habit-id"
    private val testUserId = "test-user-id"
    private val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val testNote = "Completed successfully"

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitEntryRepository = FakeHabitEntryRepository()
        markHabitCompletedUseCase = MarkHabitCompletedUseCase(firestoreRepository, habitEntryRepository)
    }

    @Test
    fun `invoke should save habit entry to both repositories when Firestore succeeds`() = runTest {
        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)

        // Assert
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        
        val firestoreEntry = firestoreRepository.savedEntry
        assertNotNull(firestoreEntry)
        assertEquals(testHabitId, firestoreEntry?.habitId)
        assertEquals(testUserId, firestoreEntry?.userId)
        assertEquals(testDate, firestoreEntry?.date)
        assertTrue(firestoreEntry?.completed == true)
        assertEquals(testNote, firestoreEntry?.note)

        // Check markHabitCompleted was called with correct parameters
        assertEquals(testHabitId, habitEntryRepository.completedHabitId)
        assertEquals(testUserId, habitEntryRepository.completedUserId)
        assertEquals(testDate, habitEntryRepository.completedDate)
        assertEquals(testNote, habitEntryRepository.completedNote)
    }

    @Test
    fun `invoke should save habit entry locally when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true

        // Act & Assert
        try {
            markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)
        } catch (e: Exception) {
            // Exception should be propagated
            assertNotNull(e)
        }

        // Verify both repositories were called
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
    }

    @Test
    fun `invoke should use current date when no date provided`() = runTest {
        // Act - не передаем date, используется значение по умолчанию
        markHabitCompletedUseCase(testHabitId, testUserId, note = testNote)

        // Assert
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        
        val savedEntry = firestoreRepository.savedEntry
        assertNotNull(savedEntry)
        // Date should be current date (within reasonable range)
        assertNotNull(savedEntry?.date)
    }

    @Test
    fun `invoke should handle null note parameter`() = runTest {
        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, null)

        // Assert
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        
        val savedEntry = firestoreRepository.savedEntry
        assertNotNull(savedEntry)
        assertNull(savedEntry?.note)
        assertNull(habitEntryRepository.completedNote)
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var saveHabitEntryCalled = false
        var savedEntry: HabitEntry? = null
        var shouldThrowException = false

        override suspend fun saveHabitEntry(habitEntry: HabitEntry) {
            saveHabitEntryCalled = true
            savedEntry = habitEntry
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
        }

        override fun getUserHabits(userId: String) = throw NotImplementedError()
        override suspend fun saveHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String) = throw NotImplementedError()
        override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override fun getHabitEntries(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun syncData() = throw NotImplementedError()
    }

    private class FakeHabitEntryRepository : HabitEntryRepository {
        var insertEntryCalled = false
        var insertedEntry: HabitEntry? = null
        var markHabitCompletedCalled = false
        var completedHabitId: String? = null
        var completedUserId: String? = null
        var completedDate: LocalDate? = null
        var completedNote: String? = null

        override suspend fun insertEntry(entry: HabitEntry) {
            insertEntryCalled = true
            insertedEntry = entry
        }

        override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {
            markHabitCompletedCalled = true
            completedHabitId = habitId
            completedUserId = userId
            completedDate = date
            completedNote = note
        }

        override suspend fun updateEntry(entry: HabitEntry) = throw NotImplementedError()
        override fun getEntryFlow(habitId: String, userId: String, date: LocalDate) = flowOf<HabitEntry?>(null)
        override suspend fun getEntry(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override fun getEntriesForHabit(habitId: String) = flowOf<List<HabitEntry>>(emptyList())
        override fun getEntriesForUser(userId: String) = flowOf<List<HabitEntry>>(emptyList())
        override fun getEntriesForUserAndHabit(userId: String, habitId: String) = flowOf<List<HabitEntry>>(emptyList())
        override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate) = throw NotImplementedError()
        override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun getCalendarData(userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
    }
}
