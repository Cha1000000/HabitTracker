package com.habittracker.domain.usecase

import com.google.gson.JsonParser
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class DeleteHabitUseCaseTest {

    private lateinit var useCase: DeleteHabitUseCase
    private lateinit var repo: HabitRepository
    private lateinit var syncManager: SyncManager
    private lateinit var scheduler: SyncWorkScheduler

    @Before
    fun setUp() {
        repo = mock()
        syncManager = mock()
        scheduler = mock()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        useCase = DeleteHabitUseCase(repo, syncManager, scheduler, scope)
    }

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
        createdAt = 0L,
        updatedAt = 0L
    )

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
        createdAt = 0L,
        updatedAt = 0L
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
    fun invoke_personalHabit_deletesLocally_andEnqueuesSoftDeleteUpsert_andSchedulesSync() = runTest {
        val habit = personalHabit(id = "p1", createdBy = "u1")
        whenever(repo.getHabitById("p1")).thenReturn(habit)

        useCase.invoke("p1", userId = "u1")

        verify(repo).deleteHabit("p1")
        val payloadCaptor = argumentCaptor<String>()
        verify(syncManager).enqueueUpsert(eq("habit"), eq("p1"), payloadCaptor.capture(), any(), any())
        val json = JsonParser.parseString(payloadCaptor.firstValue).asJsonObject
        assertTrue(json.has("updatedAt"))
        // Soft delete flag
        assertTrue(json.has("isActive"))
        assertTrue(!json.get("isActive").asBoolean)
        verify(scheduler).scheduleOneTimeSync()
    }

    @Test
    fun invoke_group_creatorLeaves_noParticipants_deletes_andEnqueuesSoftDeleteUpsert_andSchedulesSync() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = emptyList()).copy(id = "g1")
        whenever(repo.getHabitById("g1")).thenReturn(habit)

        useCase.invoke("g1", userId = "u1")

        verify(repo).deleteHabit("g1")
        val payloadCaptor = argumentCaptor<String>()
        verify(syncManager).enqueueUpsert(eq("habit"), eq("g1"), payloadCaptor.capture(), any(), any())
        val json = JsonParser.parseString(payloadCaptor.firstValue).asJsonObject
        assertTrue(json.has("updatedAt"))
        assertTrue(json.has("isActive"))
        assertTrue(!json.get("isActive").asBoolean)
        verify(scheduler).scheduleOneTimeSync()
    }

    @Test
    fun invoke_group_creatorLeaves_withParticipants_updatesGroup_andEnqueuesUpsert() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3")).copy(id = "g2")
        whenever(repo.getHabitById("g2")).thenReturn(habit)

        useCase.invoke("g2", userId = "u1")

        val captor = argumentCaptor<Habit>()
        verify(repo).updateHabit(captor.capture())
        val updated = captor.firstValue
        assertEquals(HabitType.GROUP, updated.type)
        assertEquals("u2", updated.createdBy)
        assertEquals(listOf("u3"), updated.participants)

        // Capture payload and validate JSON content
        val payloadCaptor = argumentCaptor<String>()
        verify(syncManager).enqueueUpsert(eq("habit"), eq("g2"), payloadCaptor.capture(), any(), any())
        val json = JsonParser.parseString(payloadCaptor.firstValue).asJsonObject
        // updatedAt всегда присутствует
        assertTrue(json.has("updatedAt"))
        // participants обновлены
        val participantsArr = json.getAsJsonArray("participants").map { it.asString }
        assertEquals(listOf("u3"), participantsArr)
        // type для UpdateGroup не должен отправляться (остается GROUP)
        assertTrue(!json.has("type"))
        // createdBy должен поменяться
        assertEquals("u2", json.get("createdBy").asString)
        verify(scheduler).scheduleOneTimeSync()
    }

    @Test
    fun invoke_group_participantLeaves_onlyCreatorRemains_convertsToPersonal_andEnqueuesUpsert() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2")).copy(id = "g3")
        whenever(repo.getHabitById("g3")).thenReturn(habit)

        useCase.invoke("g3", userId = "u2")

        val captor = argumentCaptor<Habit>()
        verify(repo).updateHabit(captor.capture())
        val updated = captor.firstValue
        assertEquals(HabitType.PERSONAL, updated.type)
        assertEquals("u1", updated.createdBy)
        assertEquals(emptyList<String>(), updated.participants)

        // Capture payload and validate JSON content for ConvertToPersonal
        val payloadCaptor = argumentCaptor<String>()
        verify(syncManager).enqueueUpsert(eq("habit"), eq("g3"), payloadCaptor.capture(), any(), any())
        val json = JsonParser.parseString(payloadCaptor.firstValue).asJsonObject
        assertTrue(json.has("updatedAt"))
        assertEquals("PERSONAL", json.get("type").asString)
        assertEquals("u1", json.get("createdBy").asString)
        val participants = json.getAsJsonArray("participants").map { it.asString }
        assertEquals(emptyList<String>(), participants)
        verify(scheduler).scheduleOneTimeSync()
    }

    @Test
    fun invoke_group_participantLeaves_twoRemain_updatesGroup_andEnqueuesUpsert() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3")).copy(id = "g4")
        whenever(repo.getHabitById("g4")).thenReturn(habit)

        useCase.invoke("g4", userId = "u2")

        val captor = argumentCaptor<Habit>()
        verify(repo).updateHabit(captor.capture())
        val updated = captor.firstValue
        assertEquals(HabitType.GROUP, updated.type)
        assertEquals("u1", updated.createdBy)
        assertEquals(listOf("u3"), updated.participants)

        verify(syncManager).enqueueUpsert(eq("habit"), eq("g4"), any(), any(), any())
        verify(scheduler).scheduleOneTimeSync()
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

    @Test
    fun syncManager_throws_swallowed_andSyncScheduled() = runTest {
        val habit = personalHabit(id = "p3", createdBy = "u1")
        whenever(repo.getHabitById("p3")).thenReturn(habit)
        // Force SyncManager to throw during enqueueDelete; should be swallowed
        whenever(syncManager.enqueueDelete(any(), any(), any(), any())).thenThrow(RuntimeException("outbox insert failed"))

        // Should not throw
        useCase.invoke("p3", userId = "u1")

        // Sync should still be scheduled in finally
        verify(scheduler).scheduleOneTimeSync()
    }

    @Test
    fun kickSync_calls_drainPending_on_successful_enqueue() = runTest {
        // Создаем локальный use case с тестовым scope, чтобы контролировать запуск launch {}
        val localUseCase = DeleteHabitUseCase(repo, syncManager, scheduler, this)
        val habit = personalHabit(id = "p7", createdBy = "u1")
        whenever(repo.getHabitById("p7")).thenReturn(habit)

        localUseCase.invoke("p7", userId = "u1")
        advanceUntilIdle()

        verify(syncManager).drainPending()
    }

    @Test
    fun kickSync_calls_drainPending_even_when_enqueue_throws() = runTest {
        val localUseCase = DeleteHabitUseCase(repo, syncManager, scheduler, this)
        val habit = personalHabit(id = "p8", createdBy = "u1")
        whenever(repo.getHabitById("p8")).thenReturn(habit)
        whenever(syncManager.enqueueDelete(any(), any(), any(), any())).thenThrow(RuntimeException("outbox insert failed"))

        // Не должно упасть
        localUseCase.invoke("p8", userId = "u1")
        advanceUntilIdle()

        verify(syncManager).drainPending()
    }
}
