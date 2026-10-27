package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GetHabitCompletionStatusUseCaseTest {

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

    @Before
    fun setUp() {
        habitEntryRepository = FakeHabitEntryRepository()
        getHabitCompletionStatusUseCase = GetHabitCompletionStatusUseCase(habitEntryRepository)
    }

    @Test
    fun `invoke returns true for completed local entry`() = runTest {
        habitEntryRepository.entryToReturn = completedEntry

        assertTrue(getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate))
        assertTrue(habitEntryRepository.getEntryCalled)
    }

    @Test
    fun `invoke returns false for incomplete local entry`() = runTest {
        habitEntryRepository.entryToReturn = completedEntry.copy(completed = false, completedAt = null)

        assertFalse(getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate))
    }

    @Test
    fun `missing local entry means not completed without going to network`() = runTest {
        // Use case зависит только от Room-репозитория: сетевого фоллбэка больше нет
        habitEntryRepository.entryToReturn = null

        assertFalse(getHabitCompletionStatusUseCase(testHabitId, testUserId, testDate))
        assertNull(getHabitCompletionStatusUseCase.getEntry(testHabitId, testUserId, testDate))
    }

    // Fake implementations for testing
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
        override suspend fun deleteEntriesForHabit(habitId: String) = Unit
        override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate) = throw NotImplementedError()
        override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
        override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) = throw NotImplementedError()
        override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override suspend fun setRestDay(habitId: String, userId: String, date: LocalDate, isRestDay: Boolean) = throw NotImplementedError()
        override fun getDailyCompletionCounts(userId: String): kotlinx.coroutines.flow.Flow<Map<LocalDate, Int>> = flowOf(emptyMap())
        override suspend fun updatePhotoUrl(habitId: String, userId: String, date: LocalDate, photoUrl: String?) = Unit
        override suspend fun getEntryOnce(habitId: String, userId: String, date: LocalDate): HabitEntry? = entryToReturn
    }
}
