package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.DayCompletionStatus
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GetHabitStatisticsUseCaseTest {

    private lateinit var habitRepository: HabitRepository
    private lateinit var habitEntryRepository: HabitEntryRepository
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var userRepository: UserRepository
    private lateinit var useCase: GetHabitStatisticsUseCase

    private val testUserId = "test-user-id"
    private val testHabitId = "test-habit-id"
    private val startDate = LocalDate(2024, 1, 1)
    private val endDate = LocalDate(2024, 1, 7)

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
        createdAt = startDate,
        updatedAt = startDate,
        createdBy = testUserId,
        goalType = HabitGoalType.SIMPLE,
        reminderDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    )

    private val groupHabit = testHabit.copy(
        id = "group-habit-id",
        type = HabitType.GROUP,
        participants = listOf("participant-1", "participant-2")
    )

    @Before
    fun setUp() {
        habitRepository = mock()
        habitEntryRepository = mock()
        firestoreRepository = mock()
        userRepository = mock()
        useCase = GetHabitStatisticsUseCase(
            habitRepository,
            habitEntryRepository,
            firestoreRepository,
            userRepository
        )
    }

    @Test
    fun `getPersonalStatistics returns null when habit not found`() = runTest {
        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(null)
        whenever(firestoreRepository.getHabitById(testHabitId)).thenReturn(null)

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNull(result)
    }

    @Test
    fun `getPersonalStatistics returns statistics for existing habit`() = runTest {
        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(testHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(testHabitId, testUserId, startDate, endDate))
            .thenReturn(emptyList())

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNotNull(result)
        assertEquals(testHabit, result?.habit)
        assertEquals(startDate, result?.startDate)
        assertEquals(endDate, result?.endDate)
    }

    @Test
    fun `getPersonalStatistics calculates correct completion rate`() = runTest {
        val entries = listOf(
            HabitEntry(
                habitId = testHabitId,
                userId = testUserId,
                date = LocalDate(2024, 1, 1),
                completed = true,
                value = 1,
                isRestDay = false
            ),
            HabitEntry(
                habitId = testHabitId,
                userId = testUserId,
                date = LocalDate(2024, 1, 2),
                completed = true,
                value = 1,
                isRestDay = false
            ),
            HabitEntry(
                habitId = testHabitId,
                userId = testUserId,
                date = LocalDate(2024, 1, 3),
                completed = false,
                value = 0,
                isRestDay = false
            )
        )

        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(testHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(testHabitId, testUserId, startDate, endDate))
            .thenReturn(entries)

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNotNull(result)
        assertEquals(2, result?.summary?.completedDays)
    }

    @Test
    fun `getPersonalStatistics handles rest days correctly`() = runTest {
        val entries = listOf(
            HabitEntry(
                habitId = testHabitId,
                userId = testUserId,
                date = LocalDate(2024, 1, 1),
                completed = false,
                value = 0,
                isRestDay = true
            )
        )

        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(testHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(testHabitId, testUserId, startDate, endDate))
            .thenReturn(entries)

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNotNull(result)
        val restDayStat = result?.dailyStatistics?.find { it.date == LocalDate(2024, 1, 1) }
        assertEquals(DayCompletionStatus.REST_DAY, restDayStat?.status)
    }

    @Test
    fun `getPersonalStatistics calculates streaks correctly`() = runTest {
        val entries = listOf(
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 1), completed = true, value = 1, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 2), completed = true, value = 1, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 3), completed = true, value = 1, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 4), completed = false, value = 0, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 5), completed = true, value = 1, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 6), completed = true, value = 1, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 7), completed = true, value = 1, isRestDay = false)
        )

        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(testHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(testHabitId, testUserId, startDate, endDate))
            .thenReturn(entries)

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNotNull(result)
        assertEquals(3, result?.summary?.bestStreak)
        assertEquals(3, result?.summary?.currentStreak)
    }

    @Test
    fun `getGroupStatistics returns null for non-group habit`() = runTest {
        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(testHabit)

        val result = useCase.getGroupStatistics(testHabitId, testUserId, startDate, endDate)

        assertNull(result)
    }

    @Test
    fun `getGroupStatistics returns statistics for group habit`() = runTest {
        val participant1 = User(
            id = "participant-1",
            name = "Participant 1",
            email = "p1@test.com",
            avatarUrl = null
        )
        val participant2 = User(
            id = "participant-2",
            name = "Participant 2",
            email = "p2@test.com",
            avatarUrl = null
        )
        val creator = User(
            id = testUserId,
            name = "Creator",
            email = "creator@test.com",
            avatarUrl = null
        )

        whenever(habitRepository.getHabitById(groupHabit.id)).thenReturn(groupHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(userRepository.getUserById("participant-1")).thenReturn(participant1)
        whenever(userRepository.getUserById("participant-2")).thenReturn(participant2)
        whenever(userRepository.getUserById(testUserId)).thenReturn(creator)

        val result = useCase.getGroupStatistics(groupHabit.id, testUserId, startDate, endDate)

        assertNotNull(result)
        assertEquals(groupHabit, result?.habit)
        assertNotNull(result?.currentUserStatistics)
        assertEquals(2, result?.participantsStatistics?.size)
        assertTrue(result?.participantsInfo?.isNotEmpty() == true)
    }

    @Test
    fun `getPersonalStatistics handles measurable habit correctly`() = runTest {
        val measurableHabit = testHabit.copy(
            goalType = HabitGoalType.MEASURABLE,
            targetValue = 10,
            unit = "стаканов"
        )
        val entries = listOf(
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 1), completed = true, value = 10, isRestDay = false),
            HabitEntry(habitId = testHabitId, userId = testUserId, date = LocalDate(2024, 1, 2), completed = false, value = 5, isRestDay = false)
        )

        whenever(habitRepository.getHabitById(testHabitId)).thenReturn(measurableHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(testHabitId, testUserId, startDate, endDate))
            .thenReturn(entries)

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNotNull(result)
        assertEquals(15, result?.summary?.totalValue)
        
        val partialDay = result?.dailyStatistics?.find { it.date == LocalDate(2024, 1, 2) }
        assertEquals(DayCompletionStatus.PARTIAL, partialDay?.status)
    }

    @Test
    fun `getPersonalStatistics falls back to firestore when local repo fails`() = runTest {
        whenever(habitRepository.getHabitById(testHabitId)).thenThrow(RuntimeException("Local error"))
        whenever(firestoreRepository.getHabitById(testHabitId)).thenReturn(testHabit)
        whenever(habitEntryRepository.getEntriesForUserAndPeriod(testHabitId, testUserId, startDate, endDate))
            .thenReturn(emptyList())

        val result = useCase.getPersonalStatistics(testHabitId, testUserId, startDate, endDate)

        assertNotNull(result)
        assertEquals(testHabit, result?.habit)
    }
}
