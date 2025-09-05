package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import com.habittracker.MainDispatcherRule
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getHabitsUseCase: GetHabitsUseCase = mock()
    private val addHabitUseCase: AddHabitUseCase = mock()
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase = mock()
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase = mock()
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase = mock()
    private val authRepository: AuthRepository = mock()
    private val deleteHabitUseCase: DeleteHabitUseCase = mock()
    private val application: Application = mock()
    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUp() {
        // Мокаем статические вызовы android.util.Log, чтобы не требовать Robolectric
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

    private fun habit(id: String, createdBy: String = "user1") = Habit(
        id = id,
        title = "Habit $id",
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

    private suspend fun createViewModelWithHabits(initial: List<Habit>): HabitsViewModel {
        whenever(authRepository.getCurrentUserId()).thenReturn("user1")
        // Поток привычек
        val flow = MutableSharedFlow<List<Habit>>(replay = 1)
        flow.tryEmit(initial)
        whenever(getHabitsUseCase.invoke("user1")).thenReturn(flow)
        // Статус выполнения по умолчанию false
        whenever(getHabitCompletionStatusUseCase.invoke(any(), any(), any())).thenReturn(false)
        // Сообщение об ошибке удаления (подставляем текст из аргумента)
        whenever(application.getString(eq(R.string.error_delete_habit), any())).thenAnswer { inv ->
            "Не удалось удалить привычку: ${inv.getArgument<Any>(1)}"
        }
        return HabitsViewModel(
            getHabitsUseCase,
            addHabitUseCase,
            markHabitCompletedUseCase,
            markHabitIncompleteUseCase,
            getHabitCompletionStatusUseCase,
            authRepository,
            deleteHabitUseCase,
            application
        )
    }

    @Test
    fun requestDelete_and_cancelPending_restores_item() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        // До удаления
        val before = vm.uiState.value.habitsWithCompletion.map { it.habit.id }
        assertEquals(listOf("h1", "h2"), before)

        // Оптимистичное удаление h1
        vm.requestDeleteHabit("h1")
        val afterRequest = vm.uiState.value.habitsWithCompletion.map { it.habit.id }
        assertEquals(listOf("h2"), afterRequest)

        // Отмена (UNDO)
        vm.cancelPendingDeletion()
        val afterCancel = vm.uiState.value.habitsWithCompletion.map { it.habit.id }
        assertEquals(listOf("h1", "h2"), afterCancel)
    }

    @Test
    fun requestDelete_and_confirm_success_keeps_removed_and_calls_usecase() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        // Удаляем h1 (оптимистично)
        vm.requestDeleteHabit("h1")
        assertEquals(listOf("h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })

        // use case успех
        whenever(deleteHabitUseCase.invoke("h1", "user1")).thenReturn(Unit)

        vm.confirmPendingDeletion()
        advanceUntilIdle()

        // Элемент остался удалённым, use case вызван
        assertEquals(listOf("h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        verify(deleteHabitUseCase, times(1)).invoke("h1", "user1")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun requestDelete_and_confirm_failure_restores_and_sets_error() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        // Оптимистичное удаление h1
        vm.requestDeleteHabit("h1")
        assertEquals(listOf("h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })

        // use case бросает ошибку
        whenever(deleteHabitUseCase.invoke("h1", "user1")).thenAnswer { throw RuntimeException("boom") }

        vm.confirmPendingDeletion()
        advanceUntilIdle()

        // Элемент восстановлен и ошибка установлена
        assertEquals(listOf("h1", "h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        val error = vm.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("boom"))
    }

    @Test
    fun confirmPending_without_request_noop() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        vm.confirmPendingDeletion()
        advanceUntilIdle()

        assertEquals(listOf("h1", "h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        verify(deleteHabitUseCase, never()).invoke(any(), any())
    }

    @Test
    fun cancelPending_without_request_noop() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        vm.cancelPendingDeletion()

        assertEquals(listOf("h1", "h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        verify(deleteHabitUseCase, never()).invoke(any(), any())
    }

    @Test
    fun requestDelete_unknownId_noop() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        vm.requestDeleteHabit("unknown")

        assertEquals(listOf("h1", "h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
    }

    @Test
    fun requestDelete_twice_second_ignored() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        vm.requestDeleteHabit("h1")
        // pending существует, второй вызов игнорится
        vm.requestDeleteHabit("h2")

        // В списке должен остаться только h2 (h1 удалён оптимистично)
        assertEquals(listOf("h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        // Отмена вернет h1 назад в исходную позицию
        vm.cancelPendingDeletion()
        assertEquals(listOf("h1", "h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
    }

    @Test
    fun clearError_resets_error_to_null() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        // Сгенерируем ошибку через deleteHabit
        whenever(deleteHabitUseCase.invoke("h1", "user1")).thenAnswer { throw RuntimeException("boom") }
        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        vm.deleteHabit("h1")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun deleteHabit_success_removes_and_calls_usecase() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        // use case успех заранее
        whenever(deleteHabitUseCase.invoke("h1", "user1")).thenReturn(Unit)

        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        // Оптимистичное удаление
        vm.deleteHabit("h1")
        // сразу должен исчезнуть из UI
        assertEquals(listOf("h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })

        // дождаться корутин
        advanceUntilIdle()

        // итог: элемент не восстановлен, use case вызван, ошибки нет
        assertEquals(listOf("h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        verify(deleteHabitUseCase, times(1)).invoke("h1", "user1")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun deleteHabit_failure_restores_and_sets_error() = runTest {
        val h1 = habit("h1")
        val h2 = habit("h2")
        // use case бросает
        whenever(deleteHabitUseCase.invoke("h1", "user1")).thenAnswer { throw RuntimeException("boom") }

        val vm = createViewModelWithHabits(listOf(h1, h2))
        advanceUntilIdle()

        // Оптимистичное удаление (с UnconfinedTestDispatcher корутина может выполниться сразу)
        vm.deleteHabit("h1")
        // после выполнения корутины — восстановление и ошибка
        advanceUntilIdle()

        assertEquals(listOf("h1", "h2"), vm.uiState.value.habitsWithCompletion.map { it.habit.id })
        val error = vm.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("boom"))
    }
}
