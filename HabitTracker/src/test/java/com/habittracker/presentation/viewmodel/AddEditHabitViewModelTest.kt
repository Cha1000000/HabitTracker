package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.habittracker.MainDispatcherRule
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
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
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.model.HabitValidation
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditHabitViewModelTest {

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
    
    // Mock StringProvider for tests
    private val fakeStringProvider = object : StringProvider {
        override fun getString(resId: Int): String = "test_string_$resId"
        override fun getString(resId: Int, vararg formatArgs: Any): String = "test_string_$resId"
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
    fun loadHabit_whenTimeNotNullButEnabledFalse_doesNotAutoEnableReminder() = runTest {
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        whenever(getHabitCompletionStatusUseCase.invoke(any(), any(), any())).thenReturn(false)

        val habitId = "h1"
        val habit = Habit(
            id = habitId,
            title = "Test",
            description = "",
            icon = "✅",
            color = "#00FFAA",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.DAILY,
            reminderTime = kotlinx.datetime.LocalTime(9, 0),
            reminderEnabled = false,
            createdBy = "user1",
            participants = emptyList(),
            isPublic = false,
            isActive = true,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )

        whenever(getHabitByIdUseCase.invoke(habitId)).thenReturn(habit)
        whenever(getMyHabitReminderSettingsUseCase.getOnce(habitId)).thenReturn(null)

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

        assertEquals(false, vm.uiState.value.reminderEnabled)
        assertEquals(kotlinx.datetime.LocalTime(9, 0), vm.uiState.value.reminderTime)
    }
}
