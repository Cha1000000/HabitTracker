package com.habittracker.presentation.viewmodel

import android.util.Log
import com.habittracker.MainDispatcherRule
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.challenge.CreateChallengeUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.domain.util.AnalyticsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CreateChallengeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var logMock: MockedStatic<Log>

    private val challengeRepository: ChallengeRepository = mock()
    private val habitRepository: HabitRepository = mock()
    private val socialRepository: SocialRepository = mock()
    private val getCurrentUserUseCase: GetCurrentUserUseCase = mock()
    private val getHabitsUseCase: GetHabitsUseCase = mock()
    private val getFriendsUseCase: GetFriendsUseCase = mock()
    private val createChallengeUseCase: CreateChallengeUseCase = mock()
    private val analyticsManager: AnalyticsManager = mock()

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    private fun groupHabit(
        id: String = "habit1",
        participants: List<String>,
        maxParticipants: Int
    ) = Habit(
        id = id,
        title = "Habit $id",
        description = "",
        icon = "✅",
        color = "#00FFAA",
        type = HabitType.GROUP,
        frequency = HabitFrequency.DAILY,
        createdBy = "creator",
        participants = participants,
        maxParticipants = maxParticipants,
        isPublic = false,
        isActive = true,
        createdAt = today,
        updatedAt = today
    )

    @Test
    fun `createChallenge skips non-habit participants when group is full`() = runTest {
        val me = User(id = "creator", name = "Creator")
        val inHabit = User(id = "u2", name = "InHabit")
        val notInHabit = User(id = "u3", name = "OutOfHabit")

        val habit = groupHabit(
            participants = listOf(me.id, inHabit.id),
            maxParticipants = 2
        )

        whenever(getCurrentUserUseCase.invoke()).thenReturn(flowOf(me))
        whenever(getHabitsUseCase.invoke(eq(me.id))).thenReturn(flowOf(listOf(habit)))
        whenever(getFriendsUseCase.invoke(eq(me.id))).thenReturn(flowOf(emptyList()))

        val created = Challenge(
            id = "ch1",
            habitId = habit.id,
            title = "Title",
            description = "",
            createdBy = me.id,
            participants = listOf(me.id, inHabit.id),
            acceptedParticipants = emptyList(),
            startDate = today,
            endDate = today,
            rules = ChallengeRules(type = ChallengeType.COMPLETION_RATE, targetValue = 100),
            reward = "",
            status = ChallengeStatus.ACTIVE,
            winnerId = null,
            createdAt = today,
            updatedAt = today
        )

        whenever(
            createChallengeUseCase.invoke(
                habitId = any(),
                title = any(),
                description = any(),
                createdBy = any(),
                participantIds = any(),
                startDate = any(),
                endDate = any(),
                rules = any(),
                reward = any()
            )
        ).thenReturn(Result.success(created))

        val vm = CreateChallengeViewModel(
            challengeRepository = challengeRepository,
            habitRepository = habitRepository,
            socialRepository = socialRepository,
            getCurrentUserUseCase = getCurrentUserUseCase,
            getHabitsUseCase = getHabitsUseCase,
            getFriendsUseCase = getFriendsUseCase,
            createChallengeUseCase = createChallengeUseCase,
            analyticsManager = analyticsManager
        )
        advanceUntilIdle()

        vm.onTitleChange("Title")
        vm.onHabitSelected(habit)
        vm.onParticipantToggle(inHabit)
        vm.onParticipantToggle(notInHabit)

        vm.createChallenge()
        advanceUntilIdle()

        val participantCaptor = argumentCaptor<List<String>>()
        verify(createChallengeUseCase).invoke(
            habitId = eq(habit.id),
            title = eq("Title"),
            description = any(),
            createdBy = eq(me.id),
            participantIds = participantCaptor.capture(),
            startDate = any(),
            endDate = any(),
            rules = any(),
            reward = any()
        )

        assertEquals(listOf(inHabit.id), participantCaptor.firstValue)
        assertEquals(listOf(notInHabit.name), vm.uiState.value.skippedInviteeNames)
        assertEquals(habit.maxParticipants, vm.uiState.value.skippedInviteMaxParticipants)
        assertEquals(created.id, vm.uiState.value.createdChallengeId)
        assertFalse(vm.uiState.value.isCreating)
        assertNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.selectedParticipants.any { it.id == notInHabit.id })
    }
}
