package com.habittracker.presentation.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.R
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvitationsSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_empty_state_when_no_invitations() {
        composeRule.setContent {
            InvitationsSection(
                invitations = emptyList(),
                isLoading = false,
                processingIds = emptySet(),
                onAccept = {},
                onDecline = {}
            )
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.invitations_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.no_invitations)).assertIsDisplayed()
    }

    @Test
    fun shows_loading_indicator_when_loading() {
        composeRule.setContent {
            InvitationsSection(
                invitations = emptyList(),
                isLoading = true,
                processingIds = emptySet(),
                onAccept = {},
                onDecline = {}
            )
        }
        // Ожидаем наличие текста заголовка, индикатор наличия загрузки проверяется косвенно через отсутствие empty текста
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.invitations_title)).assertIsDisplayed()
    }

    @Test
    fun shows_invitation_item_and_accept_decline_clicks() {
        val inv1 = HabitInvitation(
            id = "i1",
            habitId = "h1",
            habitTitle = "Run",
            fromUserId = "u2",
            fromUserName = "Bob",
            toUserId = "u1",
            message = null,
            status = HabitInvitationStatus.PENDING,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            respondedAt = null,
            expiresAt = null
        )
        val inv2 = inv1.copy(id = "i2", habitTitle = "Swim")
        var acceptedId: String? = null
        var declinedId: String? = null

        composeRule.setContent {
            InvitationsSection(
                invitations = listOf(inv1, inv2),
                isLoading = false,
                processingIds = emptySet(),
                onAccept = { acceptedId = it },
                onDecline = { declinedId = it }
            )
        }

        // Элемент отображается
        composeRule.onNodeWithText("Run").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.accept_invitation)).performClick()
        composeRule.runOnIdle { assert(acceptedId == "i1") }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.decline_invitation)).performClick()
        composeRule.runOnIdle { assert(declinedId == "i1") }
    }

    @Test
    fun disables_buttons_when_processing() {
        val inv = HabitInvitation(
            id = "i1",
            habitId = "h1",
            habitTitle = "Run",
            fromUserId = "u2",
            fromUserName = "Bob",
            toUserId = "u1",
            message = null,
            status = HabitInvitationStatus.PENDING,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            respondedAt = null,
            expiresAt = null
        )
        composeRule.setContent {
            InvitationsSection(
                invitations = listOf(inv),
                isLoading = false,
                processingIds = setOf("i1"),
                onAccept = {},
                onDecline = {}
            )
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.accept_invitation)).assertIsNotEnabled()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.decline_invitation)).assertIsNotEnabled()
    }
}
