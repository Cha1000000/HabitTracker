package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UpdateHabitUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var updateHabitUseCase: UpdateHabitUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Updated Test Habit",
        description = "Updated Test Description",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
        targetValue = 1,
        unit = "раз",
        participants = emptyList(),
        isActive = true,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        createdBy = "test-user-id"
    )

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitRepository = FakeHabitRepository()
        updateHabitUseCase = UpdateHabitUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should update habit in both repositories when Firestore succeeds`() = runTest {
        // Act
        updateHabitUseCase(testHabit)

        // Assert
        assertTrue(firestoreRepository.updateHabitCalled)
        assertTrue(habitRepository.updateHabitCalled)
        assertEquals(testHabit, firestoreRepository.updatedHabit)
        assertEquals(testHabit, habitRepository.updatedHabit)
    }

    @Test
    fun `invoke should update habit locally when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true

        // Act & Assert
        try {
            updateHabitUseCase(testHabit)
        } catch (e: Exception) {
            // Exception should be propagated
            assertNotNull(e)
        }

        // Verify both repositories were called
        assertTrue(firestoreRepository.updateHabitCalled)
        assertTrue(habitRepository.updateHabitCalled)
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var updateHabitCalled = false
        var updatedHabit: Habit? = null
        var shouldThrowException = false

        override suspend fun updateHabit(habit: Habit) {
            updateHabitCalled = true
            updatedHabit = habit
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
        }

        override fun getUserHabits(userId: String) = throw NotImplementedError()
        override suspend fun saveHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String) = throw NotImplementedError()
        override suspend fun saveHabitEntry(habitEntry: HabitEntry) = throw NotImplementedError()
        override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override fun getHabitEntries(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun syncData() = throw NotImplementedError()
    }

    private class FakeHabitRepository : HabitRepository {
        var updateHabitCalled = false
        var updatedHabit: Habit? = null

        override suspend fun updateHabit(habit: Habit) {
            updateHabitCalled = true
            updatedHabit = habit
        }

        override suspend fun insertHabit(habit: Habit) = throw NotImplementedError()
        override fun getHabitsByUserId(userId: String) = throw NotImplementedError()
        override suspend fun getHabitById(id: String) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override fun getAllHabits() = throw NotImplementedError()
        override fun getPersonalHabits(userId: String) = throw NotImplementedError()
        override fun getGroupHabits(userId: String) = throw NotImplementedError()
        override suspend fun addParticipantToHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun removeParticipantFromHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun getHabitParticipants(habitId: String) = throw NotImplementedError()
        override suspend fun searchHabits(userId: String, query: String) = throw NotImplementedError()
    }
}
