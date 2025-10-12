package com.habittracker.presentation.viewmodel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import com.habittracker.MainDispatcherRule
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.habit.AcceptHabitInvitationUseCase
import com.habittracker.domain.usecase.habit.DeclineHabitInvitationUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.domain.usecase.notification.GroupHabitNotificationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groupHabitNotificationUseCase: GroupHabitNotificationUseCase = mock()
    private val authRepository: AuthRepository = mock()
    private val getHabitInvitationsUseCase: GetHabitInvitationsUseCase = mock()
    private val acceptHabitInvitationUseCase: AcceptHabitInvitationUseCase = mock()
    private val declineHabitInvitationUseCase: DeclineHabitInvitationUseCase = mock()

    @Test
    fun `loads notifications and computes unread count`() = runTest {
        // Arrange
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        val storedNotifications = listOf(
            com.habittracker.domain.model.GroupHabitNotificationModel(
                id = "n1",
                userId = "user1",
                habitId = "h1",
                type = "PARTICIPANT_COMPLETED",
                participantId = "p1",
                participantName = "Alex",
                habitTitle = "Run",
                message = "Alex completed",
                isRead = false,
                readAt = null,
                createdAt = Clock.System.now().toEpochMilliseconds()
            ),
            com.habittracker.domain.model.GroupHabitNotificationModel(
                id = "n2",
                userId = "user1",
                habitId = "h2",
                type = "GROUP_ACHIEVEMENT",
                participantId = "",
                participantName = "",
                habitTitle = "Pushups",
                message = "All done",
                isRead = true,
                readAt = Clock.System.now().toEpochMilliseconds(),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
        
        // Mock groupHabitNotificationRepository instead of old usecase
        val mockRepo = mock<com.habittracker.domain.repository.GroupHabitNotificationRepository>()
        whenever(mockRepo.getNotificationsForUser("user1")).thenReturn(flowOf(storedNotifications))
        
        // Invitations streams
        whenever(getHabitInvitationsUseCase.getPendingInvitationsCount("user1")).thenReturn(flowOf(0))
        whenever(getHabitInvitationsUseCase.getIncomingInvitations("user1")).thenReturn(flowOf(emptyList()))

        // Act
        val vm = NotificationsViewModel(
            groupHabitNotificationUseCase,
            authRepository,
            getHabitInvitationsUseCase,
            acceptHabitInvitationUseCase,
            declineHabitInvitationUseCase,
            mockRepo
        )
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertEquals(2, state.storedNotifications.size)
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun `loads invitations list`() = runTest {
        // Arrange
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        val mockRepo = mock<com.habittracker.domain.repository.GroupHabitNotificationRepository>()
        whenever(mockRepo.getNotificationsForUser("user1")).thenReturn(flowOf(emptyList()))
        whenever(getHabitInvitationsUseCase.getPendingInvitationsCount("user1")).thenReturn(flowOf(2))
        val inv1 = HabitInvitation(
            id = "i1", habitId = "h1", habitTitle = "Run", fromUserId = "u2", fromUserName = "Bob",
            toUserId = "user1", message = null, status = HabitInvitationStatus.PENDING,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()), respondedAt = null, expiresAt = null
        )
        whenever(getHabitInvitationsUseCase.getIncomingInvitations("user1")).thenReturn(flowOf(listOf(inv1)))

        // Act
        val vm = NotificationsViewModel(
            groupHabitNotificationUseCase,
            authRepository,
            getHabitInvitationsUseCase,
            acceptHabitInvitationUseCase,
            declineHabitInvitationUseCase,
            mockRepo
        )
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertEquals(1, state.invitations.size)
    }

    @Test
    fun `accept invitation success updates successResId and clears processing`() = runTest {
        // Arrange
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        val mockRepo = mock<com.habittracker.domain.repository.GroupHabitNotificationRepository>()
        whenever(mockRepo.getNotificationsForUser("user1")).thenReturn(flowOf(emptyList()))
        whenever(getHabitInvitationsUseCase.getPendingInvitationsCount("user1")).thenReturn(flowOf(0))
        whenever(getHabitInvitationsUseCase.getIncomingInvitations("user1")).thenReturn(flowOf(emptyList()))
        whenever(acceptHabitInvitationUseCase.invoke("i1", "user1")).thenReturn(Result.success(mock()))

        // Act
        val vm = NotificationsViewModel(
            groupHabitNotificationUseCase,
            authRepository,
            getHabitInvitationsUseCase,
            acceptHabitInvitationUseCase,
            declineHabitInvitationUseCase,
            mockRepo
        )
        advanceUntilIdle()
        vm.acceptInvitation("i1")
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertNotNull(state.successResId)
        assertEquals(false, state.processingInvitationIds.contains("i1"))
    }

    @Test
    fun `decline invitation success updates successResId and clears processing`() = runTest {
        // Arrange
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        val mockRepo = mock<com.habittracker.domain.repository.GroupHabitNotificationRepository>()
        whenever(mockRepo.getNotificationsForUser("user1")).thenReturn(flowOf(emptyList()))
        whenever(getHabitInvitationsUseCase.getPendingInvitationsCount("user1")).thenReturn(flowOf(0))
        whenever(getHabitInvitationsUseCase.getIncomingInvitations("user1")).thenReturn(flowOf(emptyList()))
        whenever(declineHabitInvitationUseCase.invoke("i1", "user1")).thenReturn(Result.success(mock()))

        // Act
        val vm = NotificationsViewModel(
            groupHabitNotificationUseCase,
            authRepository,
            getHabitInvitationsUseCase,
            acceptHabitInvitationUseCase,
            declineHabitInvitationUseCase,
            mockRepo
        )
        advanceUntilIdle()
        vm.declineInvitation("i1")
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertNotNull(state.successResId)
        assertEquals(false, state.processingInvitationIds.contains("i1"))
    }

    @Test
    fun `markAllAsRead sets unreadCount to 0 and successResId`() = runTest {
        // Arrange
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        // Start with one unread notification emitted
        val flow = MutableSharedFlow<List<com.habittracker.domain.model.GroupHabitNotificationModel>>(replay = 1)
        flow.tryEmit(listOf(
            com.habittracker.domain.model.GroupHabitNotificationModel(
                id = "n1",
                userId = "user1",
                habitId = "h1",
                type = "PARTICIPANT_COMPLETED",
                participantId = "p1",
                participantName = "Alex",
                habitTitle = "Run",
                message = "Alex completed",
                isRead = false,
                readAt = null,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        ))
        
        val mockRepo = mock<com.habittracker.domain.repository.GroupHabitNotificationRepository>()
        whenever(mockRepo.getNotificationsForUser("user1")).thenReturn(flow)
        whenever(groupHabitNotificationUseCase.markAllNotificationsAsRead("user1")).thenReturn(Result.success(Unit))
        whenever(getHabitInvitationsUseCase.getPendingInvitationsCount("user1")).thenReturn(flowOf(0))
        whenever(getHabitInvitationsUseCase.getIncomingInvitations("user1")).thenReturn(flowOf(emptyList()))

        // Act
        val vm = NotificationsViewModel(
            groupHabitNotificationUseCase,
            authRepository,
            getHabitInvitationsUseCase,
            acceptHabitInvitationUseCase,
            declineHabitInvitationUseCase,
            mockRepo
        )
        advanceUntilIdle()
        vm.markAllAsRead()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertEquals(0, state.unreadCount)
        assertNotNull(state.successResId)
    }
}
