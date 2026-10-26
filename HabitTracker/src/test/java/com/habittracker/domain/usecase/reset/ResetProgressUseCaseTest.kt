package com.habittracker.domain.usecase.reset

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AchievementRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetProgressUseCaseTest {

    @Test
    fun `resetUserSeasonProgress resets points and level only`() = runTest {
        val userRepo = FakeUserRepository()
        val achievementRepo = FakeAchievementRepository()
        val useCase = ResetProgressUseCase(
            userRepository = userRepo,
            achievementRepository = achievementRepo,
            habitRepository = FakeHabitRepository(),
            habitEntryRepository = FakeHabitEntryRepository(),
            participantSettingsRepository = FakeParticipantSettingsRepository()
        )

        useCase.resetUserSeasonProgress("u1")

        val updated = userRepo.getUserById("u1")
        assertEquals(0, updated?.points)
        assertEquals(1, updated?.level)
        assertTrue(!achievementRepo.resetCalled)
    }

    @Test
    fun `resetUserFullProgress resets points and achievements`() = runTest {
        val userRepo = FakeUserRepository()
        val achievementRepo = FakeAchievementRepository()
        val useCase = ResetProgressUseCase(
            userRepository = userRepo,
            achievementRepository = achievementRepo,
            habitRepository = FakeHabitRepository(),
            habitEntryRepository = FakeHabitEntryRepository(),
            participantSettingsRepository = FakeParticipantSettingsRepository()
        )

        useCase.resetUserFullProgress("u1")

        val updated = userRepo.getUserById("u1")
        assertEquals(0, updated?.points)
        assertEquals(1, updated?.level)
        assertTrue(achievementRepo.resetCalled)
    }

    @Test
    fun `resetGroupHabitFullForAll deletes entries and updates season start for all participants`() = runTest {
        val habit = Habit(
            id = "h1",
            title = "Group",
            createdBy = "owner",
            type = HabitType.GROUP,
            participants = listOf("p1", "p2")
        )
        val habitRepo = FakeHabitRepository(habits = listOf(habit))
        val entryRepo = FakeHabitEntryRepository()
        val settingsRepo = FakeParticipantSettingsRepository()

        val useCase = ResetProgressUseCase(
            userRepository = FakeUserRepository(),
            achievementRepository = FakeAchievementRepository(),
            habitRepository = habitRepo,
            habitEntryRepository = entryRepo,
            participantSettingsRepository = settingsRepo
        )

        useCase.resetGroupHabitFullForAll("h1", "owner")

        assertTrue(entryRepo.deletedHabits.contains("h1"))
        val affectedUsers = settingsRepo.upserted.map { it.userId }.toSet()
        assertEquals(setOf("owner", "p1", "p2"), affectedUsers)
    }
}

private class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>(
        "u1" to User(id = "u1", name = "User", email = "u1@test.com")
    )

    override suspend fun insertUser(user: User) { users[user.id] = user }
    override suspend fun updateUser(user: User) { users[user.id] = user }
    override suspend fun deleteUser(userId: String) { users.remove(userId) }
    override suspend fun getUserById(userId: String): User? = users[userId]
    override suspend fun getUserByEmail(email: String): User? = users.values.firstOrNull { it.email == email }
    override fun getAllUsers(): Flow<List<User>> = flowOf(users.values.toList())
    override suspend fun searchUsersByName(query: String): List<User> = emptyList()
    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {}
    override suspend fun upsertUserRemoteAndLocal(user: User, fcmToken: String?) {}
    override fun observeUser(userId: String): Flow<User?> = flowOf(users[userId])
    override suspend fun backfillAvatarThumbnails(): Result<Int> = Result.success(0)
    override suspend fun updateLastActive(userId: String) {}
    override suspend fun getUserFromFirestore(userId: String): User? = users[userId]
}

private class FakeAchievementRepository : AchievementRepository {
    var resetCalled = false
    override fun getAllAchievements(): Flow<List<com.habittracker.domain.model.Achievement>> = flowOf(emptyList())
    override fun getAchievementsByCategory(category: String): Flow<List<com.habittracker.domain.model.Achievement>> = flowOf(emptyList())
    override suspend fun getAchievementById(achievementId: String): com.habittracker.domain.model.Achievement? = null
    override fun getUserAchievements(userId: String): Flow<List<com.habittracker.domain.model.UserAchievement>> = flowOf(emptyList())
    override fun getUnlockedAchievements(userId: String): Flow<List<com.habittracker.domain.model.UserAchievement>> = flowOf(emptyList())
    override fun getInProgressAchievements(userId: String): Flow<List<com.habittracker.domain.model.UserAchievement>> = flowOf(emptyList())
    override suspend fun unlockAchievement(userId: String, achievementId: String, progress: Int) {}
    override suspend fun updateAchievementProgress(userId: String, achievementId: String, progress: Int) {}
    override suspend fun getAchievementProgress(userId: String, achievementId: String): com.habittracker.domain.model.UserAchievement? = null
    override suspend fun initializeUserAchievements(userId: String) {}
    override suspend fun getUserAchievementStats(userId: String): com.habittracker.domain.repository.AchievementStats =
        com.habittracker.domain.repository.AchievementStats(0, 0, 0, 0f)
    override suspend fun resetUserAchievements(userId: String) { resetCalled = true }
}

private class FakeHabitRepository(private val habits: List<Habit> = emptyList()) : HabitRepository {
    override suspend fun insertHabit(habit: Habit) {}
    override suspend fun updateHabit(habit: Habit) {}
    override suspend fun deleteHabit(habitId: String) {}
    override suspend fun getHabitById(habitId: String): Habit? = habits.firstOrNull { it.id == habitId }
    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
    override fun getHabitByIdFlow(habitId: String): Flow<Habit?> = flowOf(habits.firstOrNull { it.id == habitId })
    override fun getAllHabits(): Flow<List<Habit>> = flowOf(habits)
    override fun getHabitsByUserId(userId: String): Flow<List<Habit>> = flowOf(habits)
    override fun getPersonalHabits(userId: String): Flow<List<Habit>> = flowOf(habits.filter { it.type == HabitType.PERSONAL })
    override fun getGroupHabits(userId: String): Flow<List<Habit>> = flowOf(habits.filter { it.type == HabitType.GROUP })
    override suspend fun addParticipantToHabit(habitId: String, userId: String) {}
    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {}
    override suspend fun getHabitParticipants(habitId: String): List<String> = emptyList()
    override suspend fun searchHabits(userId: String, query: String): List<Habit> = emptyList()
    override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun getHabitParticipantSettings(habitId: String, userId: String): HabitParticipantSettings? = null
    override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) {}
    override fun observeHabitParticipantSettings(habitId: String, userId: String): Flow<HabitParticipantSettings?> = flowOf(null)
}

private class FakeHabitEntryRepository : HabitEntryRepository {
    val deletedHabits = mutableSetOf<String>()
    override suspend fun insertEntry(entry: com.habittracker.domain.model.HabitEntry) {}
    override suspend fun updateEntry(entry: com.habittracker.domain.model.HabitEntry) {}
    override fun getEntryFlow(habitId: String, userId: String, date: kotlinx.datetime.LocalDate): Flow<com.habittracker.domain.model.HabitEntry?> = flowOf(null)
    override suspend fun getEntry(habitId: String, userId: String, date: kotlinx.datetime.LocalDate): com.habittracker.domain.model.HabitEntry? = null
    override fun getEntriesForHabit(habitId: String): Flow<List<com.habittracker.domain.model.HabitEntry>> = flowOf(emptyList())
    override fun getEntriesForUser(userId: String): Flow<List<com.habittracker.domain.model.HabitEntry>> = flowOf(emptyList())
    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<com.habittracker.domain.model.HabitEntry>> = flowOf(emptyList())
    override suspend fun getEntriesForUserAndHabitOnce(userId: String, habitId: String): List<com.habittracker.domain.model.HabitEntry> = emptyList()
    override suspend fun deleteEntriesForUserAndHabit(userId: String, habitId: String) {}
    override suspend fun deleteEntriesForHabit(habitId: String) { deletedHabits.add(habitId) }
    override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): List<com.habittracker.domain.model.HabitEntry> = emptyList()
    override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: kotlinx.datetime.LocalDate): List<com.habittracker.domain.model.HabitEntryWithUser> = emptyList()
    override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): Float = 0f
    override suspend fun markHabitCompleted(habitId: String, userId: String, date: kotlinx.datetime.LocalDate, note: String?) {}
    override suspend fun markHabitIncomplete(habitId: String, userId: String, date: kotlinx.datetime.LocalDate) {}
    override suspend fun setRestDay(habitId: String, userId: String, date: kotlinx.datetime.LocalDate, isRestDay: Boolean) {}
    override fun getDailyCompletionCounts(userId: String): Flow<Map<kotlinx.datetime.LocalDate, Int>> = flowOf(emptyMap())
    override suspend fun updatePhotoUrl(habitId: String, userId: String, date: kotlinx.datetime.LocalDate, photoUrl: String?) {}
    override suspend fun getEntryOnce(habitId: String, userId: String, date: kotlinx.datetime.LocalDate): com.habittracker.domain.model.HabitEntry? = null
}

private class FakeParticipantSettingsRepository : ParticipantSettingsRepository {
    val upserted = mutableListOf<HabitParticipantSettings>()
    override suspend fun get(habitId: String, userId: String): HabitParticipantSettings? = null
    override fun observe(habitId: String, userId: String): Flow<HabitParticipantSettings?> = flowOf(null)
    override suspend fun upsert(settings: HabitParticipantSettings) { upserted.add(settings) }
    override suspend fun upsertDefaultsFromHabit(habit: Habit, userId: String): HabitParticipantSettings {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return HabitParticipantSettings(habitId = habit.id, userId = userId, seasonStartDate = today)
    }
    override suspend fun deleteForHabit(habitId: String) {}
    override suspend fun deleteForHabitAndUser(habitId: String, userId: String) {}
}
