package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.notification.SaveParticipantJoinedNotificationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class JoinHabitByCodeUseCaseTest {

    private val habitRepository: HabitRepository = mock()
    private val socialRepository: SocialRepository = mock()
    private val generateInviteCodeUseCase: GenerateHabitInviteCodeUseCase = mock()
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase = mock()
    private val participantSettingsRepository: ParticipantSettingsRepository = mock()
    private val saveParticipantJoinedNotificationUseCase: SaveParticipantJoinedNotificationUseCase = mock()

    private val stringProvider = object : StringProvider {
        override fun getString(resId: Int): String = "test_$resId"
        override fun getString(resId: Int, vararg formatArgs: Any): String = "test_$resId"
    }

    @Test
    fun invoke_whenCreatedByBlank_firstJoinBecomesOwner() = runTest {
        val inviteCode = "ABCDEF"
        val userId = "u1"

        whenever(generateInviteCodeUseCase.isValidInviteCodeFormat(inviteCode)).thenReturn(true)

        val habit = Habit(
            id = "h1",
            title = "Public",
            description = "",
            icon = "✅",
            color = "#00FFAA",
            type = HabitType.GROUP,
            createdBy = "",
            participants = emptyList(),
            inviteCode = inviteCode,
            isPublic = true,
            isActive = true,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )

        whenever(habitRepository.getHabitByInviteCode(inviteCode)).thenReturn(habit)
        whenever(socialRepository.getUserById(userId)).thenReturn(com.habittracker.domain.model.User(id = userId, name = "User"))

        val updatedCaptor = argumentCaptor<Habit>()
        whenever(habitRepository.updateHabit(updatedCaptor.capture())).thenAnswer { }
        whenever(habitRepository.getHabitById(habit.id)).thenReturn(null)

        val useCase = JoinHabitByCodeUseCase(
            habitRepository = habitRepository,
            socialRepository = socialRepository,
            generateInviteCodeUseCase = generateInviteCodeUseCase,
            scheduleHabitReminderUseCase = scheduleHabitReminderUseCase,
            participantSettingsRepository = participantSettingsRepository,
            saveParticipantJoinedNotificationUseCase = saveParticipantJoinedNotificationUseCase,
            stringProvider = stringProvider
        )

        val result = useCase(inviteCode, userId)
        assertTrue(result.isSuccess)

        val updatedHabit = updatedCaptor.firstValue
        assertEquals(userId, updatedHabit.createdBy)
        assertEquals(listOf(userId), updatedHabit.participants)

        verify(habitRepository).updateHabit(any())
    }
}
