package com.habittracker.domain.usecase

import com.google.gson.JsonParser
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteHabitUseCaseTest {

    private lateinit var useCase: DeleteHabitUseCase
    private lateinit var repo: HabitRepository
    private lateinit var firestoreRepo: com.habittracker.domain.repository.FirestoreRepository
    private lateinit var syncManager: SyncManager
    private lateinit var scheduler: SyncWorkScheduler
    private lateinit var cancelHabitReminderUseCase: CancelHabitReminderUseCase
    private lateinit var updateHabitUseCase: UpdateHabitUseCase

    @Before
    fun setUp() {
        repo = mock()
        firestoreRepo = mock()
        syncManager = mock()
        scheduler = mock()
        cancelHabitReminderUseCase = mock()
        updateHabitUseCase = mock()
        useCase = DeleteHabitUseCase(
            repo,
            firestoreRepo,
            syncManager,
            scheduler,
            cancelHabitReminderUseCase,
            updateHabitUseCase,
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
        createdAt = 0L,
        updatedAt = 0L
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
    fun invoke_personalHabit_deletesLocally_andDeletesDirectlyFromFirestore() = runTest {
        val habit = personalHabit(id = "p1", createdBy = "u1")
        whenever(repo.getHabitById("p1")).thenReturn(habit)

        useCase.invoke("p1", userId = "u1")

        verify(repo).deleteHabit("p1")
        // Теперь для персональных привычек делаем ПРЯМОЕ удаление из Firestore
        verify(firestoreRepo).deleteHabit("p1")
        // SyncManager не должен вызываться при успешном прямом удалении
        verify(syncManager, never()).enqueueDelete(any(), any(), any(), any())
    }

    @Test
    fun invoke_group_creatorLeaves_noParticipants_deletes_andDeletesDirectlyFromFirestore() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = emptyList()).copy(id = "g1")
        whenever(repo.getHabitById("g1")).thenReturn(habit)

        useCase.invoke("g1", userId = "u1")

        verify(repo).deleteHabit("g1")
        // Теперь для пустых групп делаем ПРЯМОЕ удаление из Firestore
        verify(firestoreRepo).deleteHabit("g1")
        // SyncManager не должен вызываться при успешном прямом удалении
        verify(syncManager, never()).enqueueDelete(any(), any(), any(), any())
    }

    @Test
    fun invoke_group_creatorLeaves_withParticipants_updatesGroup_viaUpdateHabitUseCase() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3")).copy(id = "g2")
        whenever(repo.getHabitById("g2")).thenReturn(habit)

        useCase.invoke("g2", userId = "u1")

        // Создатель покидает группу - используем UpdateHabitUseCase для атомарного обновления
        val habitCaptor = argumentCaptor<Habit>()
        verify(updateHabitUseCase).invoke(habitCaptor.capture())
        val updatedHabit = habitCaptor.firstValue
        
        // Проверяем, что привычка обновлена корректно
        assertEquals("g2", updatedHabit.id)
        assertEquals("u2", updatedHabit.createdBy) // новый создатель
        assertEquals(listOf("u3"), updatedHabit.participants) // u1 удалён, u2 стал создателем
        
        // НЕ должно быть прямых вызовов repo.deleteHabit или repo.updateHabit
        verify(repo, never()).deleteHabit(any())
        verify(repo, never()).updateHabit(any())
        
        // НЕ должно быть вызова syncManager, т.к. UpdateHabitUseCase сам обновляет Firestore
        verify(syncManager, never()).enqueueUpsert(any(), any(), any(), any(), any())
        verify(scheduler, never()).scheduleOneTimeSync()
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
    fun invoke_group_participantLeaves_twoRemain_updatesGroup_viaUpdateHabitUseCase() = runTest {
        val habit = baseGroupHabit(createdBy = "u1", participants = listOf("u2", "u3")).copy(id = "g4")
        whenever(repo.getHabitById("g4")).thenReturn(habit)

        useCase.invoke("g4", userId = "u2")

        // Участник покидает группу - используем UpdateHabitUseCase
        val habitCaptor = argumentCaptor<Habit>()
        verify(updateHabitUseCase).invoke(habitCaptor.capture())
        val updatedHabit = habitCaptor.firstValue
        
        // Проверяем корректность обновления
        assertEquals("g4", updatedHabit.id)
        assertEquals("u1", updatedHabit.createdBy) // создатель не меняется
        assertEquals(listOf("u3"), updatedHabit.participants) // u2 удалён
        
        // НЕ должно быть прямых вызовов repo
        verify(repo, never()).deleteHabit(any())
        verify(repo, never()).updateHabit(any())
        
        // НЕ должно быть вызова syncManager
        verify(syncManager, never()).enqueueUpsert(any(), any(), any(), any(), any())
        verify(scheduler, never()).scheduleOneTimeSync()
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
    fun firestoreDelete_throws_fallsBackToSyncManager() = runTest {
        val habit = personalHabit(id = "p3", createdBy = "u1")
        whenever(repo.getHabitById("p3")).thenReturn(habit)
        // Force FirestoreRepository to throw during delete; should fallback to SyncManager
        whenever(firestoreRepo.deleteHabit("p3")).thenThrow(RuntimeException("Firestore delete failed"))

        // Should not throw
        useCase.invoke("p3", userId = "u1")

        // Should fallback to SyncManager enqueue
        verify(syncManager).enqueueDelete(eq("habit"), eq("p3"), any(), any())
        verify(scheduler, org.mockito.kotlin.atLeastOnce()).scheduleOneTimeSync()
    }

    @Test
    fun personalHabit_deletesDirectlyFromFirestore_withoutSyncManager() = runTest {
        // Проверяем, что для персональных привычек используется прямое удаление из Firestore
        val habit = personalHabit(id = "p7", createdBy = "u1")
        whenever(repo.getHabitById("p7")).thenReturn(habit)

        useCase.invoke("p7", userId = "u1")
        advanceUntilIdle()

        // Должен быть вызван прямой delete из Firestore
        verify(firestoreRepo).deleteHabit("p7")
        // SyncManager НЕ должен вызываться при успешном прямом удалении
        verify(syncManager, never()).enqueueDelete(any(), any(), any(), any())
        verify(syncManager, never()).drainPending()
    }

    @Test
    fun personalHabit_firestoreFails_usesSyncManagerFallback() = runTest {
        val habit = personalHabit(id = "p8", createdBy = "u1")
        whenever(repo.getHabitById("p8")).thenReturn(habit)
        // Firestore падает - должен использоваться fallback через SyncManager
        whenever(firestoreRepo.deleteHabit("p8")).thenThrow(RuntimeException("Firestore unavailable"))

        // Не должно упасть
        useCase.invoke("p8", userId = "u1")
        advanceUntilIdle()

        // Должен быть вызван fallback через SyncManager
        verify(syncManager).enqueueDelete(eq("habit"), eq("p8"), any(), any())
        verify(syncManager, org.mockito.kotlin.atLeastOnce()).drainPending()
    }
}
