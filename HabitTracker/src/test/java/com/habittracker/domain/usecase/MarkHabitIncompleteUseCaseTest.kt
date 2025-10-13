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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    fun `should write local and enqueue outbox when success`() = runTest {
        // Act
        markHabitIncompleteUseCase(testHabitId, testUserId, testDate)

        // Assert local write
        assertTrue(habitEntryRepository.markHabitIncompleteCalled)

        // Assert outbox enqueue
        val entityTypeCaptor = argumentCaptor<String>()
        val entityIdCaptor = argumentCaptor<String>()
        val payloadCaptor = argumentCaptor<String>()
        val updatedAtCaptor = argumentCaptor<Long>()
        verify(syncManager).enqueueUpsert(
            entityType = entityTypeCaptor.capture(),
            entityId = entityIdCaptor.capture(),
            payloadJson = payloadCaptor.capture(),
            updatedAt = updatedAtCaptor.capture(),
            operationId = any()
        )
        assertTrue(entityTypeCaptor.firstValue == "habit_entry")
        assertTrue(entityIdCaptor.firstValue.startsWith("${testHabitId}_${testUserId}_"))
        val payload = payloadCaptor.firstValue
        assertTrue(payload.contains("\"habitId\":\"$testHabitId\""))
        assertTrue(payload.contains("\"userId\":\"$testUserId\""))
        assertTrue(payload.contains("\"date\":\"${testDate.toString()}\""))
        assertTrue(payload.contains("\"completed\":false"))
        assertTrue(payload.contains("\"completedAt\":null"))
        assertTrue(payload.contains("\"note\":null"))

        // one-time sync scheduled
        verify(syncWorkScheduler).scheduleOneTimeSync()
    }

    @Test
    fun `should save locally and still schedule sync when outbox enqueue fails`() = runTest {
        // Arrange: make enqueueUpsert throw
        runBlocking {
            org.mockito.kotlin.whenever(
                syncManager.enqueueUpsert(
                    entityType = any(),
                    entityId = any(),
                    payloadJson = any(),
                    updatedAt = any(),
                    operationId = any()
                )
            ).thenThrow(RuntimeException("Outbox error"))
        }
        
        // Act
        markHabitIncompleteUseCase(testHabitId, testUserId, testDate)
        advanceUntilIdle() // Дождаться завершения всех корутин

        // Assert local write still happened
        assertTrue(habitEntryRepository.markHabitIncompleteCalled)
        // scheduleOneTimeSync still invoked in finally block
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
    override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): List<HabitEntry> = emptyList()
    override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser> = emptyList()
    override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Float = 0f
}
