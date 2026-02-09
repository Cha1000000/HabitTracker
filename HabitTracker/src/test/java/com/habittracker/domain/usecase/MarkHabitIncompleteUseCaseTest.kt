package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class MarkHabitIncompleteUseCaseTest {

    private lateinit var habitEntryRepository: FakeHabitEntryRepositoryIncomplete
    private lateinit var syncManager: SyncManager
    private lateinit var syncWorkScheduler: SyncWorkScheduler
    private lateinit var markHabitIncompleteUseCase: MarkHabitIncompleteUseCase

    private val testHabitId = "habit1"
    private val testUserId = "user1"
    private val testDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    @Before
    fun setUp() {
        habitEntryRepository = FakeHabitEntryRepositoryIncomplete()
        syncManager = mock()
        syncWorkScheduler = mock()
        val updateUserPointsUseCase = mock<com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase>()
        markHabitIncompleteUseCase = MarkHabitIncompleteUseCase(
            habitEntryRepository = habitEntryRepository,
            updateUserPointsUseCase = updateUserPointsUseCase,
            syncManager = syncManager,
            syncWorkScheduler = syncWorkScheduler,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `should write local and schedule sync when success`() = runTest {
        // Act
        markHabitIncompleteUseCase(testHabitId, testUserId, testDate)

        // Assert local write
        assertTrue(habitEntryRepository.markHabitIncompleteCalled)
        // one-time sync scheduled
        verify(syncWorkScheduler).scheduleOneTimeSync()
    }
}

class FakeHabitEntryRepositoryIncomplete : HabitEntryRepository {
    var markHabitIncompleteCalled = false

    override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {}
    override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {
        markHabitIncompleteCalled = true
    }

    override suspend fun insertEntry(entry: HabitEntry) {}
    override suspend fun updateEntry(entry: HabitEntry) {}
    override fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?> = flowOf(null)
    override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
    override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
    override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
    override suspend fun getEntriesForUserAndHabitOnce(userId: String, habitId: String): List<HabitEntry> = emptyList()
    override suspend fun deleteEntriesForUserAndHabit(userId: String, habitId: String) = Unit
    override suspend fun deleteEntriesForHabit(habitId: String) = Unit
    override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): List<HabitEntry> = emptyList()
    override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser> = emptyList()
    override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Float = 0f
    override suspend fun setRestDay(habitId: String, userId: String, date: LocalDate, isRestDay: Boolean) {}
    override fun getDailyCompletionCounts(userId: String): Flow<Map<LocalDate, Int>> = flowOf(emptyMap())
    override suspend fun updatePhotoUrl(habitId: String, userId: String, date: LocalDate, photoUrl: String?) {}
    override suspend fun getEntryOnce(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
}
