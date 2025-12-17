package com.habittracker.presentation.viewmodel

import android.util.Log
import com.habittracker.MainDispatcherRule
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.challenge.JoinChallengeUseCase
import com.habittracker.domain.usecase.challenge.LeaveChallengeUseCase
import com.habittracker.presentation.state.ChallengeTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var logMock: MockedStatic<Log>
    private val getCurrentUserUseCase: GetCurrentUserUseCase = mock()
    private val joinChallengeUseCase: JoinChallengeUseCase = mock()
    private val leaveChallengeUseCase: LeaveChallengeUseCase = mock()

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

    private fun createChallenge(
        id: String,
        status: ChallengeStatus = ChallengeStatus.ACTIVE,
        daysRemaining: Int = 10,
        participants: List<String> = listOf("user1")
    ): Challenge {
        val endDate = today.plus(daysRemaining, DateTimeUnit.DAY)
        return Challenge(
            id = id,
            habitId = "habit1",
            title = "Challenge $id",
            description = "Description",
            createdBy = "user1",
            participants = participants,
            startDate = today,
            endDate = endDate,
            rules = ChallengeRules(type = ChallengeType.COMPLETION_RATE, targetValue = 100),
            reward = "Prize",
            status = status,
            winnerId = null,
            createdAt = today,
            updatedAt = today
        )
    }

    private class FakeChallengeRepository(
        private val challengesFlow: MutableStateFlow<List<Challenge>> = MutableStateFlow(emptyList())
    ) : ChallengeRepository {
        
        override fun observeChallengesForUser(userId: String): Flow<List<Challenge>> = challengesFlow

        override suspend fun insertChallenge(challenge: Challenge) {}
        override suspend fun updateChallenge(challenge: Challenge) {}
        override suspend fun deleteChallenge(challengeId: String) {}
        override suspend fun getChallengeById(challengeId: String): Challenge? = null
        override fun getAllChallenges(): Flow<List<Challenge>> = flowOf(emptyList())
        override fun getChallengesForUser(userId: String): Flow<List<Challenge>> = flowOf(emptyList())
        override fun getActiveChallenges(): Flow<List<Challenge>> = flowOf(emptyList())
        override fun getCompletedChallenges(): Flow<List<Challenge>> = flowOf(emptyList())
        override suspend fun getChallengesForHabit(habitId: String): List<Challenge> = emptyList()
        override suspend fun joinChallenge(challengeId: String, userId: String) {}
        override suspend fun leaveChallenge(challengeId: String, userId: String) {}
        override suspend fun getChallengeParticipants(challengeId: String): List<String> = emptyList()
        override suspend fun updateChallengeStatus(challengeId: String, status: String) {}
        override suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>> = emptyList()
        override suspend fun endChallenge(challengeId: String, winnerId: String?) {}
        override suspend fun getCompletedDaysForUserInChallenge(
            challengeId: String,
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        override suspend fun getCurrentStreakForUserInChallenge(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        override suspend fun getCompletedDaysFromFirestore(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        override suspend fun acceptChallengeInvitation(challengeId: String, userId: String) {}
        override suspend fun declineChallengeInvitation(challengeId: String, userId: String) {}
        override suspend fun cancelChallenge(challengeId: String) {}
        override suspend fun addParticipantsToChallenge(challengeId: String, participantIds: List<String>) {}
    }

    private fun createViewModel(
        challenges: List<Challenge> = emptyList(),
        currentUser: User? = User(id = "user1", name = "Test User")
    ): ChallengesViewModel {
        val challengesFlow = MutableStateFlow(challenges)
        val repository = FakeChallengeRepository(challengesFlow)
        
        whenever(getCurrentUserUseCase.invoke()).thenReturn(flowOf(currentUser))
        
        return ChallengesViewModel(
            getCurrentUserUseCase = getCurrentUserUseCase,
            challengeRepository = repository,
            joinChallengeUseCase = joinChallengeUseCase,
            leaveChallengeUseCase = leaveChallengeUseCase
        )
    }

    @Test
    fun `initial state has empty challenges and loading false after init`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.activeChallenges.isEmpty())
        assertTrue(state.endingSoonChallenges.isEmpty())
        assertTrue(state.completedChallenges.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `challenges are filtered by status correctly`() = runTest {
        val activeChallenge = createChallenge("ch1", ChallengeStatus.ACTIVE, daysRemaining = 10)
        val completedChallenge = createChallenge("ch2", ChallengeStatus.COMPLETED)
        val cancelledChallenge = createChallenge("ch3", ChallengeStatus.CANCELLED)

        val vm = createViewModel(listOf(activeChallenge, completedChallenge, cancelledChallenge))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.activeChallenges.size)
        assertEquals("ch1", state.activeChallenges[0].id)
        assertEquals(1, state.completedChallenges.size)
        assertEquals("ch2", state.completedChallenges[0].id)
    }

    @Test
    fun `ending soon challenges are filtered correctly`() = runTest {
        val endingSoon = createChallenge("ch1", ChallengeStatus.ACTIVE, daysRemaining = 2)
        val notEndingSoon = createChallenge("ch2", ChallengeStatus.ACTIVE, daysRemaining = 10)

        val vm = createViewModel(listOf(endingSoon, notEndingSoon))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.activeChallenges.size)
        assertEquals(1, state.endingSoonChallenges.size)
        assertEquals("ch1", state.endingSoonChallenges[0].id)
    }

    @Test
    fun `onTabSelected updates selected tab`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(ChallengeTab.ACTIVE, vm.uiState.value.selectedTab)

        vm.onTabSelected(ChallengeTab.COMPLETED)
        assertEquals(ChallengeTab.COMPLETED, vm.uiState.value.selectedTab)

        vm.onTabSelected(ChallengeTab.ENDING_SOON)
        assertEquals(ChallengeTab.ENDING_SOON, vm.uiState.value.selectedTab)
    }

    @Test
    fun `onJoinChallengeClick shows join dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showJoinDialog)
        assertNull(vm.uiState.value.joinChallengeId)

        vm.onJoinChallengeClick("ch1")

        assertTrue(vm.uiState.value.showJoinDialog)
        assertEquals("ch1", vm.uiState.value.joinChallengeId)
    }

    @Test
    fun `onJoinChallengeCancel hides join dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onJoinChallengeClick("ch1")
        assertTrue(vm.uiState.value.showJoinDialog)

        vm.onJoinChallengeCancel()

        assertFalse(vm.uiState.value.showJoinDialog)
        assertNull(vm.uiState.value.joinChallengeId)
    }

    @Test
    fun `onJoinChallengeConfirm success clears dialog`() = runTest {
        val challenge = createChallenge("ch1")
        val vm = createViewModel(listOf(challenge))
        advanceUntilIdle()

        whenever(joinChallengeUseCase.invoke(any(), any())).thenReturn(Result.success(challenge))

        vm.onJoinChallengeClick("ch1")
        vm.onJoinChallengeConfirm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showJoinDialog)
        assertNull(vm.uiState.value.joinChallengeId)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `onJoinChallengeConfirm failure sets error message`() = runTest {
        val challenge = createChallenge("ch1")
        val vm = createViewModel(listOf(challenge))
        advanceUntilIdle()

        whenever(joinChallengeUseCase.invoke(any(), any()))
            .thenReturn(Result.failure(Exception("Join failed")))

        vm.onJoinChallengeClick("ch1")
        vm.onJoinChallengeConfirm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showJoinDialog)
        assertEquals("Join failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onLeaveChallengeClick success does not set error`() = runTest {
        val challenge = createChallenge("ch1")
        val vm = createViewModel(listOf(challenge))
        advanceUntilIdle()

        whenever(leaveChallengeUseCase.invoke(any(), any())).thenReturn(Result.success(challenge))

        vm.onLeaveChallengeClick("ch1")
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `onLeaveChallengeClick failure sets error message`() = runTest {
        val challenge = createChallenge("ch1")
        val vm = createViewModel(listOf(challenge))
        advanceUntilIdle()

        whenever(leaveChallengeUseCase.invoke(any(), any()))
            .thenReturn(Result.failure(Exception("Leave failed")))

        vm.onLeaveChallengeClick("ch1")
        advanceUntilIdle()

        assertEquals("Leave failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `clearError resets error message`() = runTest {
        val challenge = createChallenge("ch1")
        val vm = createViewModel(listOf(challenge))
        advanceUntilIdle()

        whenever(leaveChallengeUseCase.invoke(any(), any()))
            .thenReturn(Result.failure(Exception("Error")))

        vm.onLeaveChallengeClick("ch1")
        advanceUntilIdle()
        assertEquals("Error", vm.uiState.value.errorMessage)

        vm.clearError()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `currentUserId is set in uiState`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("user1", vm.uiState.value.currentUserId)
    }
}
