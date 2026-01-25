package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GetHabitCompletionStatusUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitEntryRepository: FakeHabitEntryRepository
    private lateinit var getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase

    private val testHabitId = "test-habit-id"
    private val testUserId = "test-user-id"
    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private val testDate = now.date
    private val completedEntry = HabitEntry(
        habitId = testHabitId,
        userId = testUserId,
        date = testDate,
        completed = true,
        completedAt = now,
        note = null,
        createdAt = testDate,
        updatedAt = testDate
    )

    private val incompleteEntry = HabitEntry(
        habitId = testHabitId,
        userId = testUserId,
        date = testDate,
        completed = false,
        completedAt = null,
        note = null,
        createdAt = testDate,
        updatedAt = testDate
    )

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitEntryRepository = FakeHabitEntryRepository()
        getHabitCompletionStatusUseCase = GetHabitCompletionStatusUseCase(firestoreRepository, habitEntryRepository)
    }

    @Test
    fun `invoke should return true when habit is completed in Firestore`() = runTest {
        // Arrange
        habitEntryRepository.entryToReturn = null
        firestoreRepository.entryToReturn = completedEntry

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertTrue(result)
        assertTrue(firestoreRepository.getHabitEntryCalled)
        assertTrue(habitEntryRepository.getEntryCalled)
    }

    @Test
    fun `invoke should return false when habit is not completed in Firestore`() = runTest {
        // Arrange
        habitEntryRepository.entryToReturn = null
        firestoreRepository.entryToReturn = incompleteEntry

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertFalse(result)
        assertTrue(firestoreRepository.getHabitEntryCalled)
        assertTrue(habitEntryRepository.getEntryCalled)
    }

    @Test
    fun `invoke should fallback to local repository when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true
        habitEntryRepository.entryToReturn = completedEntry

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertTrue(result)
        // Локальные данные приоритетны, поэтому при наличии локальной записи Firestore не вызывается
        assertFalse(firestoreRepository.getHabitEntryCalled)
        assertTrue(habitEntryRepository.getEntryCalled)
    }

    @Test
    fun `invoke should return false when both Firestore and local fail`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true
        habitEntryRepository.entryToReturn = null

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertFalse(result)
        assertTrue(firestoreRepository.getHabitEntryCalled)
        assertTrue(habitEntryRepository.getEntryCalled)
    }

    @Test
    fun `invoke should return false when no entry exists in both repositories`() = runTest {
        // Arrange - оба репозитория возвращают null
        firestoreRepository.entryToReturn = null
        habitEntryRepository.entryToReturn = null

        // Act
        val result = getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate)

        // Assert
        assertFalse(result)
        assertTrue(firestoreRepository.getHabitEntryCalled)
        // Теперь локальный репозиторий вызывается первым, независимо от результата Firestore
        assertTrue(habitEntryRepository.getEntryCalled)
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var getHabitEntryCalled = false
        var entryToReturn: HabitEntry? = null
        var shouldThrowException = false

        override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? {
            getHabitEntryCalled = true
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
            return entryToReturn
        }

        override fun getUserHabits(userId: String) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String) = throw NotImplementedError()
        override fun getHabitEntries(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun syncData() = throw NotImplementedError()
        override suspend fun getParticipantSettings(habitId: String, userId: String) = null
        override fun getAllPublicHabits(excludeUserId: String?) = flowOf(emptyList<Habit>())
        override suspend fun getHabitByInviteCode(inviteCode: String) = throw NotImplementedError()
    }

    private class FakeHabitEntryRepository : HabitEntryRepository {
        var getEntryCalled = false
        var entryToReturn: HabitEntry? = null

        override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? {
            getEntryCalled = true
            return entryToReturn
        }

        override suspend fun insertEntry(entry: HabitEntry) = throw NotImplementedError()
        override suspend fun updateEntry(entry: HabitEntry) = throw NotImplementedError()
        override fun getEntryFlow(habitId: String, userId: String, date: LocalDate) = flowOf<HabitEntry?>(null)
        override fun getEntriesForHabit(habitId: String) = flowOf<List<HabitEntry>>(emptyList())
        override fun getEntriesForUser(userId: String) = flowOf<List<HabitEntry>>(emptyList())
        override fun getEntriesForUserAndHabit(userId: String, habitId: String) = flowOf<List<HabitEntry>>(emptyList())
        override suspend fun getEntriesForUserAndHabitOnce(userId: String, habitId: String): List<HabitEntry> = emptyList()
        override suspend fun deleteEntriesForUserAndHabit(userId: String, habitId: String) = Unit
        override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate) = throw NotImplementedError()
        override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) = throw NotImplementedError()
        override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override suspend fun setRestDay(habitId: String, userId: String, date: LocalDate, isRestDay: Boolean) = throw NotImplementedError()
    }
}
