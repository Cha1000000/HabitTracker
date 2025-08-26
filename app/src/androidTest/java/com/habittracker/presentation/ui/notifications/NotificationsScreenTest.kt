package com.habittracker.presentation.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.R
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.habit.AcceptHabitInvitationUseCase
import com.habittracker.domain.usecase.habit.DeclineHabitInvitationUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.domain.usecase.notification.GroupHabitNotification
import com.habittracker.domain.usecase.notification.GroupHabitNotificationUseCase
import com.habittracker.domain.usecase.notification.NotificationType
import com.habittracker.presentation.viewmodel.NotificationsViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    init {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @MockK(relaxed = true)
    lateinit var authRepository: AuthRepository

    @MockK(relaxed = true)
    lateinit var groupUC: GroupHabitNotificationUseCase

    @MockK(relaxed = true)
    lateinit var invitationsUC: GetHabitInvitationsUseCase

    @MockK(relaxed = true)
    lateinit var acceptUC: AcceptHabitInvitationUseCase

    @MockK(relaxed = true)
    lateinit var declineUC: DeclineHabitInvitationUseCase

    @Test
    fun markAllAsRead_success_shows_snackbar_and_hides_action() {
        val notifications = listOf(
            GroupHabitNotification(
                id = "n1",
                type = NotificationType.PARTICIPANT_COMPLETED,
                habitId = "h1",
                habitTitle = "Run",
                participantName = "Alex",
                message = "Alex completed",
                timestamp = Clock.System.now(),
                isRead = false
            )
        )
        every { authRepository.getCurrentUserId() } returns "u1"
        every { groupUC.getGroupHabitNotifications("u1") } returns flowOf(notifications)
        coEvery { groupUC.markAllNotificationsAsRead("u1") } returns Result.success(Unit)
        every { invitationsUC.getIncomingInvitations("u1") } returns flowOf(emptyList())
        every { invitationsUC.getPendingInvitationsCount("u1") } returns flowOf(0)

        val vm = NotificationsViewModel(groupUC, authRepository, invitationsUC, acceptUC, declineUC)

        composeRule.setContent {
            val navController = rememberNavController()
            NotificationsScreen(navController = navController, viewModel = vm)
        }

        // До клика кнопка есть
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.mark_all_as_read)).assertIsDisplayed()

        // Клик
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.mark_all_as_read)).performClick()

        // После клика появляется snackbar об успехе
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.success_all_marked_read)).assertIsDisplayed()
        // Иконка действия исчезает (unreadCount = 0)
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.mark_all_as_read)).assertDoesNotExist()
    }

    @Test
    fun markAllAsRead_error_shows_error_snackbar() {
        val notifications = listOf(
            GroupHabitNotification(
                id = "n1",
                type = NotificationType.PARTICIPANT_COMPLETED,
                habitId = "h1",
                habitTitle = "Run",
                participantName = "Alex",
                message = "Alex completed",
                timestamp = Clock.System.now(),
                isRead = false
            )
        )
        every { authRepository.getCurrentUserId() } returns "u1"
        every { groupUC.getGroupHabitNotifications("u1") } returns flowOf(notifications)
        coEvery { groupUC.markAllNotificationsAsRead("u1") } returns Result.failure(IllegalStateException("fail"))
        every { invitationsUC.getIncomingInvitations("u1") } returns flowOf(emptyList())
        every { invitationsUC.getPendingInvitationsCount("u1") } returns flowOf(0)

        val vm = NotificationsViewModel(groupUC, authRepository, invitationsUC, acceptUC, declineUC)

        composeRule.setContent {
            val navController = rememberNavController()
            NotificationsScreen(navController = navController, viewModel = vm)
        }

        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.mark_all_as_read)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.error_mark_all_notifications)).assertIsDisplayed()
    }

    @Test
    fun invitations_section_visible_with_invitation() {
        val invitation = HabitInvitation(
            id = "i1",
            habitId = "h1",
            habitTitle = "Run",
            fromUserId = "u2",
            fromUserName = "Bob",
            toUserId = "u1",
            message = null,
            status = HabitInvitationStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            respondedAt = null,
            expiresAt = null
        )
        every { authRepository.getCurrentUserId() } returns "u1"
        every { groupUC.getGroupHabitNotifications("u1") } returns flowOf(emptyList())
        every { invitationsUC.getIncomingInvitations("u1") } returns flowOf(listOf(invitation))
        every { invitationsUC.getPendingInvitationsCount("u1") } returns flowOf(1)
        // Для accept/decline достаточно any успешного результата, ViewModel смотрит только onSuccess
        coEvery { acceptUC.invoke(any(), any()) } returns Result.success(invitation)
        coEvery { declineUC.invoke(any(), any()) } returns Result.success(invitation)

        val vm = NotificationsViewModel(groupUC, authRepository, invitationsUC, acceptUC, declineUC)

        composeRule.setContent {
            val navController = rememberNavController()
            NotificationsScreen(navController = navController, viewModel = vm)
        }

        // Заголовок секции приглашений
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.invitations_title)).assertIsDisplayed()
        // Текст приглашения (название привычки)
        composeRule.onNodeWithText("Run").assertIsDisplayed()
    }

    @Test
    fun accept_invitation_error_shows_error_snackbar() {
        val invitation = HabitInvitation(
            id = "i1",
            habitId = "h1",
            habitTitle = "Run",
            fromUserId = "u2",
            fromUserName = "Bob",
            toUserId = "u1",
            message = null,
            status = HabitInvitationStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            respondedAt = null,
            expiresAt = null
        )

        every { authRepository.getCurrentUserId() } returns "u1"
        every { groupUC.getGroupHabitNotifications("u1") } returns flowOf(emptyList())
        every { invitationsUC.getIncomingInvitations("u1") } returns flowOf(listOf(invitation))
        every { invitationsUC.getPendingInvitationsCount("u1") } returns flowOf(1)
        coEvery { acceptUC.invoke("i1", "u1") } returns Result.failure(IllegalStateException("accept failed"))
        coEvery { declineUC.invoke(any(), any()) } returns Result.success(invitation)

        val vm = NotificationsViewModel(groupUC, authRepository, invitationsUC, acceptUC, declineUC)

        composeRule.setContent {
            val navController = rememberNavController()
            NotificationsScreen(navController = navController, viewModel = vm)
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.accept_invitation)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.error_accept_invitation)).assertIsDisplayed()
    }

    @Test
    fun decline_invitation_error_shows_error_snackbar() {
        val invitation = HabitInvitation(
            id = "i2",
            habitId = "h2",
            habitTitle = "Swim",
            fromUserId = "u3",
            fromUserName = "Alice",
            toUserId = "u1",
            message = null,
            status = HabitInvitationStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            respondedAt = null,
            expiresAt = null
        )

        every { authRepository.getCurrentUserId() } returns "u1"
        every { groupUC.getGroupHabitNotifications("u1") } returns flowOf(emptyList())
        every { invitationsUC.getIncomingInvitations("u1") } returns flowOf(listOf(invitation))
        every { invitationsUC.getPendingInvitationsCount("u1") } returns flowOf(1)
        coEvery { acceptUC.invoke(any(), any()) } returns Result.success(invitation)
        coEvery { declineUC.invoke("i2", "u1") } returns Result.failure(IllegalStateException("decline failed"))

        val vm = NotificationsViewModel(groupUC, authRepository, invitationsUC, acceptUC, declineUC)

        composeRule.setContent {
            val navController = rememberNavController()
            NotificationsScreen(navController = navController, viewModel = vm)
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.decline_invitation)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.error_decline_invitation)).assertIsDisplayed()
    }

    @Test
    fun multiple_invitations_are_displayed() {
        val invitations = listOf(
            HabitInvitation(
                id = "i1",
                habitId = "h1",
                habitTitle = "Run",
                fromUserId = "u2",
                fromUserName = "Bob",
                toUserId = "u1",
                message = null,
                status = HabitInvitationStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                respondedAt = null,
                expiresAt = null
            ),
            HabitInvitation(
                id = "i2",
                habitId = "h2",
                habitTitle = "Swim",
                fromUserId = "u3",
                fromUserName = "Alice",
                toUserId = "u1",
                message = null,
                status = HabitInvitationStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                respondedAt = null,
                expiresAt = null
            ),
            HabitInvitation(
                id = "i3",
                habitId = "h3",
                habitTitle = "Yoga",
                fromUserId = "u4",
                fromUserName = "Mike",
                toUserId = "u1",
                message = null,
                status = HabitInvitationStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                respondedAt = null,
                expiresAt = null
            )
        )

        every { authRepository.getCurrentUserId() } returns "u1"
        every { groupUC.getGroupHabitNotifications("u1") } returns flowOf(emptyList())
        every { invitationsUC.getIncomingInvitations("u1") } returns flowOf(invitations)
        every { invitationsUC.getPendingInvitationsCount("u1") } returns flowOf(invitations.size)
        coEvery { acceptUC.invoke(any(), any()) } returns Result.success(invitations.first())
        coEvery { declineUC.invoke(any(), any()) } returns Result.success(invitations.first())

        val vm = NotificationsViewModel(groupUC, authRepository, invitationsUC, acceptUC, declineUC)

        composeRule.setContent {
            val navController = rememberNavController()
            NotificationsScreen(navController = navController, viewModel = vm)
        }

        composeRule.onNodeWithText("Run").assertIsDisplayed()
        composeRule.onNodeWithText("Swim").assertIsDisplayed()
    }
}
