package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@ExperimentalCoroutinesApi
class SimpleAddHabitUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var updateUserPointsUseCase: FakeUpdateUserPointsUseCase
    private lateinit var checkAchievementsUseCase: CheckAchievementsUseCase
    private lateinit var addHabitUseCase: AddHabitUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Test Habit",
        description = "Test Description",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
        targetValue = 1,
        unit = "раз",
        participants = emptyList(),
        isActive = true,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        createdBy = "test-user-id"
    )

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitRepository = FakeHabitRepository()
        updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        addHabitUseCase = AddHabitUseCase(firestoreRepository, habitRepository, updateUserPointsUseCase, checkAchievementsUseCase)
    }

    @Test
    fun `invoke should save habit to both repositories when Firestore succeeds`() = runTest {
        // Act
        addHabitUseCase(testHabit)

        // Assert
        assertTrue(firestoreRepository.saveHabitCalled)
        assertTrue(habitRepository.insertHabitCalled)
        assertEquals(testHabit, firestoreRepository.savedHabit)
        assertEquals(testHabit, habitRepository.insertedHabit)
    }

    @Test
    fun `invoke should save habit locally when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true

        // Act & Assert
        try {
            addHabitUseCase(testHabit)
        } catch (e: Exception) {
            // Exception should be propagated
            assertNotNull(e)
        }

        // Verify both repositories were called
        assertTrue(firestoreRepository.saveHabitCalled)
        assertTrue(habitRepository.insertHabitCalled)
    }
    
    // Fake UpdateUserPointsUseCase
    private class FakeUpdateUserPointsUseCase : UpdateUserPointsUseCase(
        mock(UserRepository::class.java),
        mock(SocialRepository::class.java)
    ) {
        override suspend fun invoke(userId: String, event: GamificationEvent): Result<Unit> {
            return Result.success(Unit)
        }
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var saveHabitCalled = false
        var savedHabit: Habit? = null
        var shouldThrowException = false

        override suspend fun saveHabit(habit: Habit) {
            saveHabitCalled = true
            savedHabit = habit
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
        }

        override fun getUserHabits(userId: String) = flowOf(emptyList<Habit>())
        override suspend fun updateHabit(habit: Habit) {}
        override suspend fun deleteHabit(habitId: String) {}
        override suspend fun getHabitById(habitId: String): Habit? = null
        override suspend fun saveHabitEntry(habitEntry: HabitEntry) {}
        override suspend fun getHabitEntry(habitId: String, userId: String, date: kotlinx.datetime.LocalDate): HabitEntry? = null
        override fun getHabitEntries(habitId: String, userId: String) = flowOf(emptyList<HabitEntry>())
        override suspend fun syncData() {}
        override suspend fun getParticipantSettings(habitId: String, userId: String) = null
        override suspend fun setParticipantSettings(settings: HabitParticipantSettings) {}
        override fun getAllPublicHabits(excludeUserId: String?) = flowOf(emptyList<Habit>())
        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
    }

    private class FakeHabitRepository : HabitRepository {
        var insertHabitCalled = false
        var insertedHabit: Habit? = null
        var habits = listOf<Habit>()

        override suspend fun insertHabit(habit: Habit) {
            insertHabitCalled = true
            insertedHabit = habit
            habits = habits + habit
        }

        override fun getHabitsByUserId(userId: String) = flowOf(emptyList<Habit>())
        override suspend fun getHabitById(habitId: String): Habit? {
            return habits.find { it.id == habitId }
        }
        
        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
            return habits.find { it.inviteCode == inviteCode && it.isActive && it.isPublic }
        }

        override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
            return flowOf(habits.find { it.id == habitId })
        }

        override suspend fun updateHabit(habit: Habit) {}
        override suspend fun deleteHabit(habitId: String) {}
        override fun getAllHabits() = flowOf(habits)
        override fun getPersonalHabits(userId: String) = flowOf(emptyList<Habit>())
        override fun getGroupHabits(userId: String) = flowOf(emptyList<Habit>())
        override suspend fun addParticipantToHabit(habitId: String, userId: String) {}
        override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {}
        override suspend fun getHabitParticipants(habitId: String) = emptyList<String>()
        override suspend fun searchHabits(userId: String, query: String) = emptyList<Habit>()
        override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
        override suspend fun getHabitParticipantSettings(habitId: String, userId: String) = null
        override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) {}
        override fun observeHabitParticipantSettings(habitId: String, userId: String) = flowOf(null)
    }
}
