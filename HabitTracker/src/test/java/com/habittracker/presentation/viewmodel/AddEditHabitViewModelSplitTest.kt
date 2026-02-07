package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.habittracker.MainDispatcherRule
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitValidation
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.ApplyReminderToAllParticipantsUseCase
import com.habittracker.domain.usecase.CancelHabitReminderUseCase
import com.habittracker.domain.usecase.EnsureMySettingsForHabitUseCase
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetMyHabitReminderSettingsUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.UpdateMyHabitReminderSettingsUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.SendHabitInvitationUseCase
import com.habittracker.domain.usecase.reset.ResetProgressUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditHabitViewModelSplitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = mock()
    private val addHabitUseCase: AddHabitUseCase = mock()
    private val updateHabitUseCase: UpdateHabitUseCase = mock()
    private val getHabitByIdUseCase: GetHabitByIdUseCase = mock()
    private val habitEntryRepository: HabitEntryRepository = mock()
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase = mock()
    private val cancelHabitReminderUseCase: CancelHabitReminderUseCase = mock()
    private val getMyHabitReminderSettingsUseCase: GetMyHabitReminderSettingsUseCase = mock()
    private val updateMyHabitReminderSettingsUseCase: UpdateMyHabitReminderSettingsUseCase = mock()
    private val sendHabitInvitationUseCase: SendHabitInvitationUseCase = mock()
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase = mock()
    private val getFriendsUseCase: GetFriendsUseCase = mock()
    private val applyReminderToAllParticipantsUseCase: ApplyReminderToAllParticipantsUseCase = mock()
    private val ensureMySettingsForHabitUseCase: EnsureMySettingsForHabitUseCase = mock()
    private val authRepository: AuthRepository = mock()
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase = mock()
    private val resetProgressUseCase: ResetProgressUseCase = mock()
    private val analyticsManager: AnalyticsManager = mock()
    private val errorLogger: ErrorLogger = mock()

    private val fakeStringProvider = object : StringProvider {
        override fun getString(resId: Int): String = "test_$resId"
        override fun getString(resId: Int, vararg formatArgs: Any): String = "test_$resId"
    }
    private val habitValidation = HabitValidation(fakeStringProvider)

    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
        logMock.`when`<Int> { Log.i(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun saveHabit_authorConvertsPublicGroupToPersonal_splitsAndMigratesEntries_andTransfersOwnerToNextParticipant() = runTest {
        val authorId = "author"
        val p1 = "p1"
        val p2 = "p2"

        whenever(authRepository.getCurrentUserId()).thenReturn(authorId)
        whenever(getHabitCompletionStatusUseCase.invoke(any(), any(), any())).thenReturn(false)

        val habitId = "group_habit"
        val groupHabit = Habit(
            id = habitId,
            title = "Title",
            description = "Desc",
            icon = "✅",
            color = "#00FFAA",
            type = HabitType.GROUP,
            goalType = HabitGoalType.SIMPLE,
            frequency = HabitFrequency.DAILY,
            reminderTime = null,
            reminderEnabled = false,
            createdBy = authorId,
            participants = listOf(authorId, p1, p2),
            inviteCode = "ABCDEF",
            isPublic = true,
            isActive = true,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )

        whenever(getHabitByIdUseCase.invoke(habitId)).thenReturn(groupHabit)
        whenever(getMyHabitReminderSettingsUseCase.getOnce(habitId)).thenReturn(null)

        val entries = listOf(
            HabitEntry(habitId = habitId, userId = authorId, date = LocalDate(2025, 1, 1), completed = true),
            HabitEntry(habitId = habitId, userId = authorId, date = LocalDate(2025, 1, 2), completed = false)
        )
        whenever(habitEntryRepository.getEntriesForUserAndHabitOnce(authorId, habitId)).thenReturn(entries)

        val vm = AddEditHabitViewModel(
            application = application,
            addHabitUseCase = addHabitUseCase,
            updateHabitUseCase = updateHabitUseCase,
            getHabitByIdUseCase = getHabitByIdUseCase,
            habitEntryRepository = habitEntryRepository,
            scheduleHabitReminderUseCase = scheduleHabitReminderUseCase,
            cancelHabitReminderUseCase = cancelHabitReminderUseCase,
            getMyHabitReminderSettingsUseCase = getMyHabitReminderSettingsUseCase,
            updateMyHabitReminderSettingsUseCase = updateMyHabitReminderSettingsUseCase,
            sendHabitInvitationUseCase = sendHabitInvitationUseCase,
            generateHabitInviteCodeUseCase = generateHabitInviteCodeUseCase,
            getFriendsUseCase = getFriendsUseCase,
            applyReminderToAllParticipantsUseCase = applyReminderToAllParticipantsUseCase,
            ensureMySettingsForHabitUseCase = ensureMySettingsForHabitUseCase,
            authRepository = authRepository,
            getHabitCompletionStatusUseCase = getHabitCompletionStatusUseCase,
            resetProgressUseCase = resetProgressUseCase,
            analyticsManager = analyticsManager,
            errorLogger = errorLogger,
            habitValidation = habitValidation,
            savedStateHandle = SavedStateHandle(mapOf("habitId" to habitId))
        )

        advanceUntilIdle()

        vm.onTypeChange(HabitType.PERSONAL)
        vm.saveHabit()
        advanceUntilIdle()

        val addCaptor = argumentCaptor<Habit>()
        verify(addHabitUseCase).invoke(addCaptor.capture())
        val personalHabit = addCaptor.firstValue

        assertEquals(HabitType.PERSONAL, personalHabit.type)
        assertFalse(personalHabit.isPublic)
        assertEquals(authorId, personalHabit.createdBy)
        assertTrue(personalHabit.participants.isEmpty())
        assertEquals(null, personalHabit.inviteCode)

        val updateCaptor = argumentCaptor<Habit>()
        verify(updateHabitUseCase).invoke(updateCaptor.capture())
        val publicHabit = updateCaptor.firstValue

        assertEquals(habitId, publicHabit.id)
        assertEquals(HabitType.GROUP, publicHabit.type)
        assertTrue(publicHabit.isPublic)
        assertEquals(listOf(p1, p2), publicHabit.participants)
        assertEquals(p1, publicHabit.createdBy)

        verify(habitEntryRepository).deleteEntriesForUserAndHabit(authorId, habitId)
        val entryCaptor = argumentCaptor<HabitEntry>()
        verify(habitEntryRepository, Mockito.times(entries.size)).insertEntry(entryCaptor.capture())
        entryCaptor.allValues.forEachIndexed { idx, inserted ->
            assertEquals(personalHabit.id, inserted.habitId)
            assertEquals(entries[idx].userId, inserted.userId)
            assertEquals(entries[idx].date, inserted.date)
        }

        assertEquals(personalHabit.id, vm.uiState.value.habitId)
        assertTrue(vm.uiState.value.isHabitSaved)
    }
}
