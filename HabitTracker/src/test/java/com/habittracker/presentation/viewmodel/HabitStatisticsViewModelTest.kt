package com.habittracker.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.habittracker.MainDispatcherRule
import com.habittracker.domain.model.DayCompletionStatus
import com.habittracker.domain.model.DayStatistics
import com.habittracker.domain.model.GroupHabitStatisticsData
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.HabitStatisticsData
import com.habittracker.domain.model.HabitStatisticsSummary
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.StatisticsPeriod
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.habit.GetHabitStatisticsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HabitStatisticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getHabitStatisticsUseCase: GetHabitStatisticsUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var savedStateHandle: SavedStateHandle

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

    private val testSummary = HabitStatisticsSummary(
        totalDays = 7,
        activeDays = 7,
        completedDays = 5,
        missedDays = 2,
        restDays = 0,
        partialDays = 0,
        completionRate = 0.71f,
        currentStreak = 3,
        bestStreak = 3,
        totalValue = 5
    )

    private val testDailyStatistics = listOf(
        DayStatistics(LocalDate(2024, 1, 1), DayCompletionStatus.COMPLETED, 1, 1),
        DayStatistics(LocalDate(2024, 1, 2), DayCompletionStatus.COMPLETED, 1, 1),
        DayStatistics(LocalDate(2024, 1, 3), DayCompletionStatus.MISSED, 0, 1),
        DayStatistics(LocalDate(2024, 1, 4), DayCompletionStatus.COMPLETED, 1, 1),
        DayStatistics(LocalDate(2024, 1, 5), DayCompletionStatus.COMPLETED, 1, 1),
        DayStatistics(LocalDate(2024, 1, 6), DayCompletionStatus.MISSED, 0, 1),
        DayStatistics(LocalDate(2024, 1, 7), DayCompletionStatus.COMPLETED, 1, 1)
    )

    private val testStatistics = HabitStatisticsData(
        habit = testHabit,
        startDate = startDate,
        endDate = endDate,
        dailyStatistics = testDailyStatistics,
        summary = testSummary
    )

    @Before
    fun setUp() {
        getHabitStatisticsUseCase = mock()
        authRepository = mock()
    }

    private fun createViewModel(habitId: String = testHabitId, isGroup: Boolean = false): HabitStatisticsViewModel {
        savedStateHandle = SavedStateHandle(
            mapOf(
                "habitId" to habitId,
                "isGroup" to isGroup
            )
        )
        return HabitStatisticsViewModel(
            getHabitStatisticsUseCase,
            authRepository,
            savedStateHandle
        )
    }

    @Test
    fun `initial state is loading for self habit`() = runTest {
        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getPersonalStatistics(any(), any(), any(), any()))
            .thenReturn(testStatistics)

        val viewModel = createViewModel()

        assertTrue(viewModel.selfStatUiState.value.isLoading || viewModel.selfStatUiState.value.statistics != null)
    }

    @Test
    fun `loadStatistics loads self statistics for personal habit`() = runTest {
        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getPersonalStatistics(any(), any(), any(), any()))
            .thenReturn(testStatistics)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.selfStatUiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.statistics)
        assertEquals(testHabit, state.statistics?.habit)
    }

    @Test
    fun `loadStatistics sets error when statistics not found`() = runTest {
        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getPersonalStatistics(any(), any(), any(), any()))
            .thenReturn(null)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.selfStatUiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `onSelfPeriodChanged updates period and reloads statistics`() = runTest {
        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getPersonalStatistics(any(), any(), any(), any()))
            .thenReturn(testStatistics)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSelfPeriodChanged(StatisticsPeriod.MONTH)
        advanceUntilIdle()

        assertEquals(StatisticsPeriod.MONTH, viewModel.selfStatUiState.value.selectedPeriod)
    }

    @Test
    fun `onSelfCustomDateRangeChanged updates dates and reloads statistics`() = runTest {
        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getPersonalStatistics(any(), any(), any(), any()))
            .thenReturn(testStatistics)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val customStart = LocalDate(2024, 2, 1)
        val customEnd = LocalDate(2024, 2, 15)
        viewModel.onSelfCustomDateRangeChanged(customStart, customEnd)
        advanceUntilIdle()

        val state = viewModel.selfStatUiState.value
        assertEquals(StatisticsPeriod.CUSTOM, state.selectedPeriod)
        assertEquals(customStart, state.customStartDate)
        assertEquals(customEnd, state.customEndDate)
    }

    @Test
    fun `loadStatistics loads group statistics for group habit`() = runTest {
        val groupHabit = testHabit.copy(
            type = HabitType.GROUP,
            participants = listOf("participant-1")
        )
        val participant = User(
            id = "participant-1",
            name = "Participant",
            email = "p@test.com",
            avatarUrl = null
        )
        val groupStatistics = GroupHabitStatisticsData(
            habit = groupHabit,
            startDate = startDate,
            endDate = endDate,
            currentUserStatistics = testStatistics.copy(habit = groupHabit),
            participantsStatistics = mapOf("participant-1" to testStatistics.copy(habit = groupHabit)),
            participantsInfo = listOf(participant)
        )

        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getGroupStatistics(any(), any(), any(), any()))
            .thenReturn(groupStatistics)

        val viewModel = createViewModel(isGroup = true)
        advanceUntilIdle()

        val state = viewModel.groupStatUiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.statistics)
        assertEquals(groupHabit, state.statistics?.habit)
    }

    @Test
    fun `onToggleParticipantsComparison toggles comparison mode`() = runTest {
        val groupHabit = testHabit.copy(
            type = HabitType.GROUP,
            participants = listOf("participant-1")
        )
        val groupStatistics = GroupHabitStatisticsData(
            habit = groupHabit,
            startDate = startDate,
            endDate = endDate,
            currentUserStatistics = testStatistics.copy(habit = groupHabit),
            participantsStatistics = emptyMap(),
            participantsInfo = emptyList()
        )

        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getGroupStatistics(any(), any(), any(), any()))
            .thenReturn(groupStatistics)

        val viewModel = createViewModel(isGroup = true)
        advanceUntilIdle()

        assertFalse(viewModel.groupStatUiState.value.showParticipantsComparison)

        viewModel.onToggleParticipantsComparison()
        assertTrue(viewModel.groupStatUiState.value.showParticipantsComparison)

        viewModel.onToggleParticipantsComparison()
        assertFalse(viewModel.groupStatUiState.value.showParticipantsComparison)
    }

    @Test
    fun `onToggleParticipantSelection updates selected participants`() = runTest {
        val groupHabit = testHabit.copy(
            type = HabitType.GROUP,
            participants = listOf("participant-1", "participant-2")
        )
        val participants = listOf(
            User("participant-1", "User 1"),
            User("participant-2", "User 2")
        )
        val groupStatistics = GroupHabitStatisticsData(
            habit = groupHabit,
            startDate = startDate,
            endDate = endDate,
            currentUserStatistics = testStatistics.copy(habit = groupHabit),
            participantsStatistics = emptyMap(),
            participantsInfo = participants
        )

        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getGroupStatistics(any(), any(), any(), any()))
            .thenReturn(groupStatistics)

        val viewModel = createViewModel(isGroup = true)
        advanceUntilIdle()

        // Изначально выбраны все (пустой список)
        assertTrue(viewModel.groupStatUiState.value.selectedParticipantIds.isEmpty())

        // Кликаем по participant-1, он должен быть исключен из списка выбранных
        // Значит в списке должен остаться только participant-2
        viewModel.onToggleParticipantSelection("participant-1")
        assertFalse(viewModel.groupStatUiState.value.selectedParticipantIds.contains("participant-1"))
        assertTrue(viewModel.groupStatUiState.value.selectedParticipantIds.contains("participant-2"))

        // Кликаем снова по participant-1, он должен вернуться в список
        // Теперь выбраны все, значит список должен снова стать пустым
        viewModel.onToggleParticipantSelection("participant-1")
        assertTrue(viewModel.groupStatUiState.value.selectedParticipantIds.isEmpty())
    }

    @Test
    fun `onClearParticipantFilter clears all selected participants`() = runTest {
        val groupHabit = testHabit.copy(
            type = HabitType.GROUP,
            participants = listOf("participant-1", "participant-2")
        )
        val participants = listOf(
            User("participant-1", "User 1"),
            User("participant-2", "User 2")
        )
        val groupStatistics = GroupHabitStatisticsData(
            habit = groupHabit,
            startDate = startDate,
            endDate = endDate,
            currentUserStatistics = testStatistics.copy(habit = groupHabit),
            participantsStatistics = emptyMap(),
            participantsInfo = participants
        )

        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getGroupStatistics(any(), any(), any(), any()))
            .thenReturn(groupStatistics)

        val viewModel = createViewModel(isGroup = true)
        advanceUntilIdle()

        // Изначально фильтр пуст (выбраны все)
        assertTrue(viewModel.groupStatUiState.value.selectedParticipantIds.isEmpty())

        // Кликаем по participant-1 -> он исключается, в выбранных остается только participant-2
        viewModel.onToggleParticipantSelection("participant-1")
        assertEquals(1, viewModel.groupStatUiState.value.selectedParticipantIds.size)
        assertTrue(viewModel.groupStatUiState.value.selectedParticipantIds.contains("participant-2"))

        // Сбрасываем фильтр -> должны быть выбраны все (пустой список)
        viewModel.onClearParticipantFilter()
        assertTrue(viewModel.groupStatUiState.value.selectedParticipantIds.isEmpty())
    }

    @Test
    fun `loadGroupStatistics excludes current user from participants info`() = runTest {
        val groupHabit = testHabit.copy(
            type = HabitType.GROUP,
            participants = listOf(testUserId, "participant-1")
        )
        val participants = listOf(
            User(testUserId, "Current User"),
            User("participant-1", "Other User")
        )
        val groupStatistics = GroupHabitStatisticsData(
            habit = groupHabit,
            startDate = startDate,
            endDate = endDate,
            currentUserStatistics = testStatistics.copy(habit = groupHabit),
            participantsStatistics = emptyMap(),
            participantsInfo = participants
        )

        whenever(authRepository.getCurrentUserId()).thenReturn(testUserId)
        whenever(getHabitStatisticsUseCase.getGroupStatistics(any(), any(), any(), any()))
            .thenReturn(groupStatistics)

        val viewModel = createViewModel(isGroup = true)
        advanceUntilIdle()

        val uiState = viewModel.groupStatUiState.value
        assertFalse(uiState.isLoading)
        assertNotNull(uiState.statistics)
        
        // Проверяем, что в списке участников нет текущего пользователя
        assertEquals(1, uiState.participantsInfo.size)
        assertEquals("participant-1", uiState.participantsInfo.first().id)
        assertFalse(uiState.participantsInfo.any { it.id == testUserId })
    }

    @Test
    fun `empty habitId does not load statistics`() = runTest {
        savedStateHandle = SavedStateHandle(
            mapOf(
                "habitId" to "",
                "isGroup" to false
            )
        )
        val viewModel = HabitStatisticsViewModel(
            getHabitStatisticsUseCase,
            authRepository,
            savedStateHandle
        )
        advanceUntilIdle()

        assertTrue(viewModel.selfStatUiState.value.isLoading)
        assertNull(viewModel.selfStatUiState.value.statistics)
    }
}
