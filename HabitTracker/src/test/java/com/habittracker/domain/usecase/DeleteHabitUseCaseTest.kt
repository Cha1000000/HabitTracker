package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteHabitUseCaseTest {

    private lateinit var useCase: DeleteHabitUseCase
    private lateinit var repo: HabitRepository
    private lateinit var syncManager: SyncManager
    private lateinit var scheduler: SyncWorkScheduler
    private lateinit var cancelHabitReminderUseCase: CancelHabitReminderUseCase
    private lateinit var sendGroupHabitPushNotificationUseCase: com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase

    @Before
    fun setUp() {
        repo = mock()
        syncManager = mock()
        scheduler = mock()
        cancelHabitReminderUseCase = mock()
        sendGroupHabitPushNotificationUseCase = mock()
        useCase = DeleteHabitUseCase(
            repo,
            syncManager,
            scheduler,
            cancelHabitReminderUseCase,
            sendGroupHabitPushNotificationUseCase
        )
    }

    @Suppress("SameParameterValue")
    private fun baseGroupHabit(
        createdBy: String,
        participants: List<String>
    ): Habit = Habit(
        id = "h1",
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

    @Suppress("SameParameterValue")
    private fun personalHabit(id: String = "p1", createdBy: String = "u1"): Habit = Habit(
        id = id,
        title = "Personal",
        description = "",
        icon = "✅",
        color = "#00FFAA",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        createdBy = createdBy,
        participants = emptyList(),
        maxParticipants = 1,
        isPublic = false,
        isActive = true,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )

    @Test
    fun creatorLeaves_noParticipants_deleteAll() {
        val habit = baseGroupHabit(createdBy = "u1", participants = emptyList())
        val decision = useCase.decideGroupDeletion(habit, userId = "u1")
        assertTrue(decision is DeleteHabitUseCase.Decision.DeleteAll)
    }

    @Test
    fun creatorLeaves_participantsRemain_updateGroup_reassignFirst_dropFromParticipants() {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3"))
        val decision = useCase.decideGroupDeletion(habit, userId = "u1")
        assertTrue(decision is DeleteHabitUseCase.Decision.UpdateGroup)
        val update = decision as DeleteHabitUseCase.Decision.UpdateGroup
        assertEquals("u2", update.newCreatedBy)
        assertEquals(listOf("u3"), update.participants)
    }

    @Test
    fun participantLeaves_onlyCreatorRemains_convertToPersonal() {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2"))
        val decision = useCase.decideGroupDeletion(habit, userId = "u2")
        assertTrue(decision is DeleteHabitUseCase.Decision.ConvertToPersonal)
        val convert = decision as DeleteHabitUseCase.Decision.ConvertToPersonal
        assertEquals("u1", convert.newCreatedBy)
    }

    @Test
    fun participantLeaves_twoOrMoreRemain_updateGroup_creatorUnchanged_participantsFiltered() {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3"))
        val decision = useCase.decideGroupDeletion(habit, userId = "u2")
        assertTrue(decision is DeleteHabitUseCase.Decision.UpdateGroup)
        val update = decision as DeleteHabitUseCase.Decision.UpdateGroup
        assertEquals("u1", update.newCreatedBy)
        assertEquals(listOf("u3"), update.participants)
    }

    // --- Interaction tests ---

    @Test
    fun invoke_personalHabit_deletesLocally_andDeletesDirectlyFromFirestore() = runTest {
        val habit = personalHabit(id = "p1", createdBy = "u1")
        whenever(repo.getHabitById("p1")).thenReturn(habit)

        useCase.invoke("p1", userId = "u1")

        verify(repo).deleteHabit("p1")
    }

    @Test
    fun invoke_group_creatorLeaves_noParticipants_deletes_andDeletesDirectlyFromFirestore() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = emptyList()).copy(id = "g1")
        whenever(repo.getHabitById("g1")).thenReturn(habit)

        useCase.invoke("g1", userId = "u1")

        verify(repo).deleteHabit("g1")
    }

    @Test
    fun invoke_group_creatorLeaves_withParticipants_updatesGroup_viaRepository() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3")).copy(id = "g2")
        whenever(repo.getHabitById("g2")).thenReturn(habit)

        useCase.invoke("g2", userId = "u1")

        // Создатель покидает группу - используем habitRepository.updateHabit
        val habitCaptor = argumentCaptor<Habit>()
        verify(repo).updateHabit(habitCaptor.capture())
        val updatedHabit = habitCaptor.firstValue
        
        // Проверяем, что привычка обновлена корректно
        assertEquals("g2", updatedHabit.id)
        assertEquals("u2", updatedHabit.createdBy) // новый создатель
        assertEquals(listOf("u3"), updatedHabit.participants) // u1 удалён, u2 стал создателем
        
        // НЕ должно быть вызова deleteHabit
        verify(repo, never()).deleteHabit(any())
    }

    @Test
    fun invoke_group_participantLeaves_onlyCreatorRemains_convertsToPersonal() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2")).copy(id = "g3")
        whenever(repo.getHabitById("g3")).thenReturn(habit)

        useCase.invoke("g3", userId = "u2")

        val captor = argumentCaptor<Habit>()
        verify(repo).updateHabit(captor.capture())
        val updated = captor.firstValue
        assertEquals(HabitType.PERSONAL, updated.type)
        assertEquals("u1", updated.createdBy)
        assertEquals(emptyList<String>(), updated.participants)
    }

    @Test
    fun invoke_group_participantLeaves_twoRemain_updatesGroup_viaRepository() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3")).copy(id = "g4")
        whenever(repo.getHabitById("g4")).thenReturn(habit)

        useCase.invoke("g4", userId = "u2")

        // Участник покидает группу - используем habitRepository.updateHabit
        val habitCaptor = argumentCaptor<Habit>()
        verify(repo).updateHabit(habitCaptor.capture())
        val updatedHabit = habitCaptor.firstValue
        
        // Проверяем корректность обновления
        assertEquals("g4", updatedHabit.id)
        assertEquals("u1", updatedHabit.createdBy) // создатель не меняется
        assertEquals(listOf("u3"), updatedHabit.participants) // u2 удалён
        
        // НЕ должно быть вызова deleteHabit
        verify(repo, never()).deleteHabit(any())
    }

    @Test
    fun invoke_habitNotFound_noOps() = runTest {
        whenever(repo.getHabitById("missing")).thenReturn(null)

        useCase.invoke("missing", userId = "u1")

        // No interactions with repo beyond get
        verify(repo).getHabitById("missing")
        verifyNoMoreInteractions(repo)
        verifyNoMoreInteractions(syncManager)
        verifyNoMoreInteractions(scheduler)
    }

    // --- Negative cases ---

    @Test
    fun personal_delete_roomError_propagates_andNoSync() = runTest {
        val habit = personalHabit(id = "p2", createdBy = "u1")
        whenever(repo.getHabitById("p2")).thenReturn(habit)
        whenever(repo.deleteHabit("p2")).thenThrow(RuntimeException("db failure"))

        var thrown: Throwable? = null
        try {
            useCase.invoke("p2", userId = "u1")
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
        // ensure no sync actions when room fails
        verify(scheduler, never()).scheduleOneTimeSync()
        verifyNoMoreInteractions(syncManager)
    }

    @Test
    fun group_update_roomError_propagates_andNoSync() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2")).copy(id = "g5")
        whenever(repo.getHabitById("g5")).thenReturn(habit)
        // participant leaves -> ConvertToPersonal -> updateHabit called
        whenever(repo.updateHabit(any())).thenThrow(RuntimeException("update failed"))

        var thrown: Throwable? = null
        try {
            useCase.invoke("g5", userId = "u2")
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
        verify(scheduler, never()).scheduleOneTimeSync()
        verifyNoMoreInteractions(syncManager)
    }
}
