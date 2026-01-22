package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import java.util.Arrays

@RunWith(Parameterized::class)
class DeleteHabitGroupDecisionParamTest(
    private val description: String,
    private val habit: Habit,
    private val userId: String,
    private val expectedKind: Kind,
    private val expectedCreatedBy: String?,
    private val expectedParticipants: List<String>?
) {

    enum class Kind { DELETE_ALL, CONVERT_TO_PERSONAL, UPDATE_GROUP }

    private lateinit var useCase: DeleteHabitUseCase

    @Before
    fun setUp() {
        val repo: HabitRepository = mock()
        val syncManager: SyncManager = mock()
        val scheduler: SyncWorkScheduler = mock()
        val cancelHabitReminderUseCase: CancelHabitReminderUseCase = mock()
        val sendGroupHabitPushNotificationUseCase: com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase = mock()
        useCase = DeleteHabitUseCase(
            repo,
            syncManager,
            scheduler,
            cancelHabitReminderUseCase,
            sendGroupHabitPushNotificationUseCase
        )
    }

    @Test
    fun decision_cases() {
        val decision = useCase.decideGroupDeletion(habit, userId)
        val actualKind = when (decision) {
            is DeleteHabitUseCase.Decision.DeleteAll -> Kind.DELETE_ALL
            is DeleteHabitUseCase.Decision.ConvertToPersonal -> Kind.CONVERT_TO_PERSONAL
            is DeleteHabitUseCase.Decision.UpdateGroup -> Kind.UPDATE_GROUP
        }
        assertEquals("$description -> kind mismatch", expectedKind, actualKind)
        when (decision) {
            is DeleteHabitUseCase.Decision.DeleteAll -> {
                // nothing else to assert
            }
            is DeleteHabitUseCase.Decision.ConvertToPersonal -> {
                assertEquals(description, expectedCreatedBy, decision.newCreatedBy)
            }
            is DeleteHabitUseCase.Decision.UpdateGroup -> {
                assertEquals(description, expectedCreatedBy, decision.newCreatedBy)
                assertEquals(description, expectedParticipants, decision.participants)
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<Array<Any?>> {
            fun group(createdBy: String, participants: List<String>, id: String = "g"): Habit = Habit(
                id = id,
                title = "Test",
                description = "",
                icon = "🎯",
                color = "#2196F3",
                type = HabitType.GROUP,
                frequency = HabitFrequency.DAILY,
                createdBy = createdBy,
                participants = participants,
                maxParticipants = 10,
                isPublic = false,
                isActive = true,
                createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            )

            return Arrays.asList(
                arrayOf(
                    "creator leaves, no participants -> DeleteAll",
                    group(createdBy = "u1", participants = emptyList()),
                    "u1",
                    Kind.DELETE_ALL,
                    null,
                    null
                ),
                arrayOf(
                    "creator leaves, one participant -> ConvertToPersonal(new=u2)",
                    group(createdBy = "u1", participants = listOf("u2")),
                    "u1",
                    Kind.CONVERT_TO_PERSONAL,
                    "u2",
                    null
                ),
                arrayOf(
                    "creator leaves, two participants -> UpdateGroup(new=u2, participants=[u3])",
                    group(createdBy = "u1", participants = listOf("u2", "u3")),
                    "u1",
                    Kind.UPDATE_GROUP,
                    "u2",
                    listOf("u3")
                ),
                arrayOf(
                    "participant leaves, only creator remains -> ConvertToPersonal(new=u1)",
                    group(createdBy = "u1", participants = listOf("u2")),
                    "u2",
                    Kind.CONVERT_TO_PERSONAL,
                    "u1",
                    null
                ),
                arrayOf(
                    "participant leaves, two remain -> UpdateGroup(new=u1, participants=[u3])",
                    group(createdBy = "u1", participants = listOf("u2", "u3")),
                    "u2",
                    Kind.UPDATE_GROUP,
                    "u1",
                    listOf("u3")
                )
            )
        }
    }
}
