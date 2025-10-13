package com.habittracker.presentation.ui.habits

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.EnsureMySettingsForHabitUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetHabitsForDateUseCase
import com.habittracker.domain.usecase.GetWeekCompletionDataUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.habit.JoinHabitByCodeUseCase
import com.habittracker.presentation.viewmodel.FriendsViewModel
import com.habittracker.presentation.viewmodel.HabitsViewModel
import com.habittracker.presentation.viewmodel.NotificationsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HabitsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var authRepository: AuthRepository
    private lateinit var getHabitsForDateUseCase: GetHabitsForDateUseCase
    private lateinit var markHabitCompletedUseCase: MarkHabitCompletedUseCase
    private lateinit var markHabitIncompleteUseCase: MarkHabitIncompleteUseCase
    private lateinit var getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase
    private lateinit var deleteHabitUseCase: DeleteHabitUseCase
    private lateinit var ensureMySettingsForHabitUseCase: EnsureMySettingsForHabitUseCase
    private lateinit var scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase
    private lateinit var getWeekCompletionDataUseCase: GetWeekCompletionDataUseCase

    private lateinit var notificationsViewModel: NotificationsViewModel
    private lateinit var friendsViewModel: FriendsViewModel
    private lateinit var habitsViewModel: HabitsViewModel

    private lateinit var habitsFlow: MutableSharedFlow<List<Habit>>

    // Slow mode controls (enable with -Pandroid.testInstrumentationRunnerArguments.slowMode=true)
    private val slowMode: Boolean by lazy {
        InstrumentationRegistry.getArguments().getString("slowMode")?.toBoolean() ?: false
    }
    private val slowMs: Long by lazy {
        InstrumentationRegistry.getArguments().getString("slowMs")?.toLongOrNull() ?: 700L
    }
    private fun slow(ms: Long = slowMs) {
        if (slowMode) {
            composeRule.waitForIdle()
            Thread.sleep(ms)
        }
    }

    // Convenience wrappers for slowing down interactions when slowMode is ON
    private fun SemanticsNodeInteraction.performTouchInputSlow(block: TouchInjectionScope.() -> Unit) {
        performTouchInput(block)
        slow()
    }
    private fun SemanticsNodeInteraction.performClickSlow() {
        performClick()
        slow()
    }

    @Before
    fun setup() {
        // --- Mocks for HabitsViewModel ---
        authRepository = mockk(relaxed = true)
        every { authRepository.getCurrentUserId() } returns "user1"

        getHabitsForDateUseCase = mockk()
        habitsFlow = MutableSharedFlow(replay = 1)
        every { getHabitsForDateUseCase.invoke("user1", any()) } returns habitsFlow

        markHabitCompletedUseCase = mockk(relaxed = true)
        markHabitIncompleteUseCase = mockk(relaxed = true)
        getHabitCompletionStatusUseCase = mockk()
        coEvery { getHabitCompletionStatusUseCase.invoke(any(), any(), any()) } returns false
        deleteHabitUseCase = mockk(relaxed = true)
        ensureMySettingsForHabitUseCase = mockk(relaxed = true)
        scheduleHabitReminderUseCase = mockk(relaxed = true)
        getWeekCompletionDataUseCase = mockk(relaxed = true)
        coEvery { getWeekCompletionDataUseCase.invoke(any(), any()) } returns emptyMap()
        val joinHabitByCodeUseCase = mockk<JoinHabitByCodeUseCase>(relaxed = true)

        // --- Real VMs constructed with mocks ---
        habitsViewModel = HabitsViewModel(
            getHabitsForDateUseCase,
            markHabitCompletedUseCase,
            markHabitIncompleteUseCase,
            getHabitCompletionStatusUseCase,
            authRepository,
            deleteHabitUseCase,
            ensureMySettingsForHabitUseCase,
            scheduleHabitReminderUseCase,
            joinHabitByCodeUseCase,
            getWeekCompletionDataUseCase,
            composeRule.activity.application
        )

        // NotificationsViewModel mocks and instance
        val groupHabitNotificationUseCase = mockk<com.habittracker.domain.usecase.notification.GroupHabitNotificationUseCase>()
        val getHabitInvitationsUseCase = mockk<com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase>()
        val acceptHabitInvitationUseCase = mockk<com.habittracker.domain.usecase.habit.AcceptHabitInvitationUseCase>()
        val declineHabitInvitationUseCase = mockk<com.habittracker.domain.usecase.habit.DeclineHabitInvitationUseCase>()
        val groupHabitNotificationRepository = mockk<GroupHabitNotificationRepository>(relaxed = true)
        every { groupHabitNotificationUseCase.getGroupHabitNotifications(any()) } returns flowOf(emptyList())
        every { getHabitInvitationsUseCase.getPendingInvitationsCount(any()) } returns flowOf(0)
        every { getHabitInvitationsUseCase.getIncomingInvitations(any()) } returns flowOf(emptyList())
        every { groupHabitNotificationRepository.getNotificationsForUser(any()) } returns flowOf(emptyList())

        notificationsViewModel = NotificationsViewModel(
            groupHabitNotificationUseCase,
            authRepository,
            getHabitInvitationsUseCase,
            acceptHabitInvitationUseCase,
            declineHabitInvitationUseCase,
            groupHabitNotificationRepository
        )

        // FriendsViewModel mocks and instance
        val socialRepository = mockk<com.habittracker.domain.repository.SocialRepository>()
        every { socialRepository.getFriends(any()) } returns flowOf(emptyList())
        every { socialRepository.getIncomingFriendRequests(any()) } returns flowOf(emptyList())
        every { socialRepository.getOutgoingFriendRequests(any()) } returns flowOf(emptyList())
        val authRepoForFriends = mockk<AuthRepository>()
        every { authRepoForFriends.getCurrentUser() } returns flowOf(
            com.habittracker.domain.model.User(
                id = "user1",
                name = "User 1",
                email = "u1@mail"
            )
        )

        friendsViewModel = FriendsViewModel(socialRepository, authRepoForFriends)

        // Set content with our VMs
        composeRule.setContent {
            val navController = rememberNavController()
            HabitsScreen(
                navController = navController,
                viewModel = habitsViewModel,
                notificationsViewModel = notificationsViewModel,
                friendsViewModel = friendsViewModel
            )
        }

        // Emit initial habits
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val habit1 = Habit(
            id = "h1",
            title = "Утренняя зарядка",
            description = "",
            icon = "💪",
            color = "#2196F3",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.DAILY,
            createdBy = "user1",
            participants = emptyList(),
            maxParticipants = 1,
            isPublic = false,
            isActive = true,
            createdAt = today,
            updatedAt = today
        )
        val habit2 = habit1.copy(id = "h2", title = "Чтение")
        habitsFlow.tryEmit(listOf(habit1, habit2))
        slow(1200)
    }

    @Test
    fun swipeDelete_thenUndo_restoresItem() {
        // Swipe left on first item to open confirm dialog
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()
        composeRule.onNodeWithTag("habit-h1").performTouchInputSlow { swipeLeft() }

        // Confirm deletion in dialog
        val deleteText = composeRule.activity.getString(R.string.delete)
        composeRule.onNodeWithText(deleteText).assertIsDisplayed().performClickSlow()

        // After optimistic removal, item should be gone
        composeRule.onNodeWithTag("habit-h1").assertDoesNotExist()

        // Snackbar appears; click Undo
        val undoText = composeRule.activity.getString(R.string.undo)
        composeRule.onNode(hasClickAction() and hasText(undoText)).assertIsDisplayed().performClickSlow()

        // Item should be restored
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()

        // Ensure DeleteHabitUseCase was NOT called due to undo
        coVerify(exactly = 0) { deleteHabitUseCase.invoke("h1", any()) }
    }

    @Test
    fun swipeDelete_thenConfirm_confirmsDeletion() {
        // Swipe left and confirm in dialog
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()
        composeRule.onNodeWithTag("habit-h1").performTouchInput { swipeLeft() }
        val deleteText = composeRule.activity.getString(R.string.delete)
        composeRule.onNodeWithText(deleteText).assertIsDisplayed().performClick()

        // Instead of waiting for snackbar timeout, directly confirm pending deletion
        habitsViewModel.confirmPendingDeletion()

        // Item should remain deleted
        composeRule.onNodeWithTag("habit-h1").assertDoesNotExist()

        // Verify use case invoked
        coVerify(exactly = 1) { deleteHabitUseCase.invoke("h1", "user1") }
    }

    @Test
    fun swipeDelete_confirm_failure_restores_and_shows_error() {
        // Arrange failure on use case
        coEvery { deleteHabitUseCase.invoke("h1", "user1") } throws RuntimeException("boom")

        // Request deletion directly via ViewModel to avoid initial 'habit deleted' snackbar
        habitsViewModel.requestDeleteHabit("h1")
        composeRule.waitForIdle()

        // Confirm programmatically to trigger use case and failure
        habitsViewModel.confirmPendingDeletion()
        composeRule.waitForIdle()

        // Expect restored item (wait until back in tree)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("habit-h1")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()

        // Expect error snackbar text (wait until visible)
        val expectedError = composeRule.activity.getString(R.string.error_delete_habit, "boom")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(expectedError)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(expectedError)).assertIsDisplayed()

        // Use case should have been called once
        coVerify(exactly = 1) { deleteHabitUseCase.invoke("h1", "user1") }
    }

    @Test
    fun requestDelete_twice_second_ignored_when_pending_exists() {
        // First deletion request for h1
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()
        composeRule.onNodeWithTag("habit-h1").performTouchInput { swipeLeft() }
        val deleteText = composeRule.activity.getString(R.string.delete)
        composeRule.onNodeWithText(deleteText).assertIsDisplayed().performClick()

        // Ensure h1 is optimistically removed
        composeRule.onNodeWithTag("habit-h1").assertDoesNotExist()

        // Try to delete h2 while pending deletion exists
        composeRule.onNodeWithTag("habit-h2").assertIsDisplayed()
        composeRule.onNodeWithTag("habit-h2").performTouchInputSlow { swipeLeft() }
        composeRule.onNodeWithText(deleteText).assertIsDisplayed().performClick()

        // h2 should remain visible because second request is ignored
        composeRule.onNodeWithTag("habit-h2").assertIsDisplayed()

        // Cleanup: cancel pending to restore h1 and close flow
        habitsViewModel.cancelPendingDeletion()
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()

        // Ensure use case not called yet (since nothing confirmed)
        coVerify(exactly = 0) { deleteHabitUseCase.invoke(any(), any()) }
    }

    @Test
    fun requestDelete_sameItem_twice_ignored() {
        // First deletion request via UI dialog to ensure snackbar shows
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()
        composeRule.onNodeWithTag("habit-h1").performTouchInput { swipeLeft() }
        val deleteText = composeRule.activity.getString(R.string.delete)
        composeRule.onNodeWithText(deleteText).assertIsDisplayed().performClick()

        // h1 removed optimistically
        composeRule.onNodeWithTag("habit-h1").assertDoesNotExist()

        // Try to request deletion of the same item again (should be ignored)
        habitsViewModel.requestDeleteHabit("h1")
        composeRule.waitForIdle()

        // Still absent (no duplicate pending changed state)
        composeRule.onNodeWithTag("habit-h1").assertDoesNotExist()

        // Cancel pending to restore
        habitsViewModel.cancelPendingDeletion()
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()

        // Ensure use case not called
        coVerify(exactly = 0) { deleteHabitUseCase.invoke(any(), any()) }
    }

    @Test
    fun swipeDelete_snackbar_no_action_confirms() {
        // Start via UI
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()
        composeRule.onNodeWithTag("habit-h1").performTouchInput { swipeLeft() }
        val deleteText = composeRule.activity.getString(R.string.delete)
        composeRule.onNodeWithText(deleteText).assertIsDisplayed().performClick()

        // Snackbar should appear with Undo
        val undoText = composeRule.activity.getString(R.string.undo)
        composeRule.onNode(hasText(undoText)).assertIsDisplayed()

        // Simulate timeout (no action) by confirming programmatically
        habitsViewModel.confirmPendingDeletion()

        // h1 stays deleted
        composeRule.onNodeWithTag("habit-h1").assertDoesNotExist()
        coVerify(exactly = 1) { deleteHabitUseCase.invoke("h1", "user1") }
    }

    @Test
    fun directDelete_failure_restores_and_shows_error() {
        // Arrange error path in use case
        coEvery { deleteHabitUseCase.invoke("h1", "user1") } throws RuntimeException("server down")

        // Trigger direct delete path
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()
        habitsViewModel.deleteHabit("h1")
        composeRule.waitForIdle()

        // Expect restored item
        composeRule.onNodeWithTag("habit-h1").assertIsDisplayed()

        // Error snackbar should be shown
        val expectedError = composeRule.activity.getString(R.string.error_delete_habit, "server down")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(expectedError)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(expectedError)).assertIsDisplayed()

        // Use case invoked once
        coVerify(exactly = 1) { deleteHabitUseCase.invoke("h1", "user1") }
    }
}
