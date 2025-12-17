package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Lightweight unit tests for challenge-related use cases.
 * Используются простые fake-реализации репозиториев, строго соответствующие интерфейсам.
 */
class ChallengeUseCasesTest {

    private class FakeChallengeRepository : ChallengeRepository {
        val items = LinkedHashMap<String, Challenge>()
        private val allFlow = MutableStateFlow<List<Challenge>>(emptyList())

        private fun emit() { allFlow.value = items.values.toList() }

        override suspend fun insertChallenge(challenge: Challenge) {
            items[challenge.id] = challenge
            emit()
        }

        override suspend fun updateChallenge(challenge: Challenge) {
            items[challenge.id] = challenge
            emit()
        }

        override suspend fun deleteChallenge(challengeId: String) {
            items.remove(challengeId)
            emit()
        }

        override suspend fun getChallengeById(challengeId: String): Challenge? = items[challengeId]

        override fun getAllChallenges(): Flow<List<Challenge>> = allFlow.asStateFlow()

        override fun getChallengesForUser(userId: String): Flow<List<Challenge>> =
            allFlow.asStateFlow()

        override fun getActiveChallenges(): Flow<List<Challenge>> =
            allFlow.asStateFlow()

        override fun getCompletedChallenges(): Flow<List<Challenge>> =
            allFlow.asStateFlow()

        override suspend fun getChallengesForHabit(habitId: String): List<Challenge> =
            items.values.filter { it.habitId == habitId }

        override suspend fun joinChallenge(challengeId: String, userId: String) { /* not used */ }
        override suspend fun leaveChallenge(challengeId: String, userId: String) { /* not used */ }
        override suspend fun getChallengeParticipants(challengeId: String): List<String> =
            items[challengeId]?.participants ?: emptyList()

        override suspend fun updateChallengeStatus(challengeId: String, status: String) { /* not used */ }
        override suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>> = emptyList()
        override suspend fun endChallenge(challengeId: String, winnerId: String?) { /* not used */ }
        
        override fun observeChallengesForUser(userId: String): Flow<List<Challenge>> = allFlow.asStateFlow()
        
        override suspend fun getCompletedDaysForUserInChallenge(
            challengeId: String,
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        
        override suspend fun getCurrentStreakForUserInChallenge(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        
        override suspend fun getCompletedDaysFromFirestore(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        
        override suspend fun acceptChallengeInvitation(challengeId: String, userId: String) {}
        override suspend fun declineChallengeInvitation(challengeId: String, userId: String) {}
        override suspend fun cancelChallenge(challengeId: String) {}
        override suspend fun addParticipantsToChallenge(challengeId: String, participantIds: List<String>) {}
    }

    private class FakeHabitRepository : HabitRepository {
        private val habits = LinkedHashMap<String, Habit>()
        override suspend fun insertHabit(habit: Habit) { habits[habit.id] = habit }
        override suspend fun updateHabit(habit: Habit) { habits[habit.id] = habit }
        override suspend fun deleteHabit(habitId: String) { habits.remove(habitId) }
        override suspend fun getHabitById(habitId: String): Habit? = habits[habitId]
        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
        override fun getHabitByIdFlow(habitId: String): Flow<Habit?> = MutableStateFlow(habits[habitId])
        override fun getAllHabits(): Flow<List<Habit>> = MutableStateFlow(habits.values.toList())
        override fun getHabitsByUserId(userId: String): Flow<List<Habit>> = MutableStateFlow(habits.values.filter { it.createdBy == userId })
        override fun getPersonalHabits(userId: String): Flow<List<Habit>> = MutableStateFlow(emptyList())
        override fun getGroupHabits(userId: String): Flow<List<Habit>> = MutableStateFlow(emptyList())
        override suspend fun addParticipantToHabit(habitId: String, userId: String) {}
        override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {}
        override suspend fun getHabitParticipants(habitId: String): List<String> = emptyList()
        override suspend fun searchHabits(userId: String, query: String): List<Habit> = emptyList()
        override fun getPublicHabits(): Flow<List<Habit>> = MutableStateFlow(emptyList())
        override suspend fun getHabitParticipantSettings(habitId: String, userId: String) = null
        override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) = throw NotImplementedError()
        override fun observeHabitParticipantSettings(habitId: String, userId: String) = MutableStateFlow(null)
    }

    private class FakeHabitEntryRepository : HabitEntryRepository {
        override suspend fun insertEntry(entry: HabitEntry) {}
        override suspend fun updateEntry(entry: HabitEntry) {}
        override fun getEntryFlow(habitId: String, userId: String, date: LocalDate) = MutableStateFlow<HabitEntry?>(null)
        override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
        override fun getEntriesForHabit(habitId: String) = MutableStateFlow<List<HabitEntry>>(emptyList())
        override fun getEntriesForUser(userId: String) = MutableStateFlow<List<HabitEntry>>(emptyList())
        override fun getEntriesForUserAndHabit(userId: String, habitId: String) = MutableStateFlow<List<HabitEntry>>(emptyList())
        override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): List<HabitEntry> = emptyList()
        override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate) = emptyList<HabitEntryWithUser>()
        override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Float = 0f
        override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {}
        override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {}
    }

    private class FakeSocialRepository : SocialRepository {
        private val friends = mutableSetOf<Pair<String, String>>()

        // User search and management
        override suspend fun searchUsers(query: String, currentUserId: String) = emptyList<User>()
        override suspend fun getUserById(userId: String): User? = User(id = userId, name = "User$userId")
        override suspend fun getUserByIdFromFirestore(userId: String): User? = getUserById(userId)
        override suspend fun updateUserFcmToken(userId: String, token: String) {}
        override suspend fun updateFriendsCount(userId: String, count: Int) {}

        // Friend requests
        override suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?) = Result.failure<FriendRequest>(Exception("not used"))
        override suspend fun acceptFriendRequest(requestId: String) = Result.failure<Friendship>(Exception("not used"))
        override suspend fun declineFriendRequest(requestId: String) = Result.failure<FriendRequest>(Exception("not used"))
        override suspend fun cancelFriendRequest(requestId: String) = Result.success(Unit)
        override suspend fun getFriendRequestById(requestId: String) = null
        override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String) = null
        override fun getIncomingFriendRequests(userId: String) = MutableStateFlow<List<FriendRequestWithUsers>>(emptyList())
        override fun getOutgoingFriendRequests(userId: String) = MutableStateFlow<List<FriendRequestWithUsers>>(emptyList())
        override fun getIncomingRequestsCount(userId: String) = MutableStateFlow(0)

        // Friendships
        override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String) = null
        override suspend fun removeFriend(currentUserId: String, friendId: String) = Result.success(Unit)
        override suspend fun blockUser(currentUserId: String, userIdToBlock: String) = Result.failure<Friendship>(Exception("not used"))
        override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String) = Result.failure<Friendship>(Exception("not used"))
        override fun getFriends(userId: String) = MutableStateFlow<List<FriendshipWithUsers>>(emptyList())
        override fun getFriendsCount(userId: String) = MutableStateFlow(0)

        // Habit invitations
        override suspend fun sendHabitInvitation(habitId: String, habitTitle: String, fromUserId: String, fromUserName: String, toUserId: String, message: String?) = Result.failure<HabitInvitation>(Exception("not used"))
        override suspend fun acceptHabitInvitation(invitationId: String) = Result.failure<HabitInvitation>(Exception("not used"))
        override suspend fun declineHabitInvitation(invitationId: String) = Result.failure<HabitInvitation>(Exception("not used"))
        override suspend fun cancelHabitInvitation(invitationId: String) = Result.success(Unit)
        override suspend fun getHabitInvitationById(invitationId: String) = null
        override suspend fun getPendingHabitInvitation(habitId: String, userId: String) = null
        override fun getIncomingHabitInvitations(userId: String) = MutableStateFlow<List<HabitInvitation>>(emptyList())
        override fun getOutgoingHabitInvitations(userId: String) = MutableStateFlow<List<HabitInvitation>>(emptyList())
        override fun getHabitInvitations(habitId: String) = MutableStateFlow<List<HabitInvitation>>(emptyList())
        override fun getPendingHabitInvitationsCount(userId: String) = MutableStateFlow(0)
        override suspend fun getActiveOutgoingInvitationsCount(userId: String) = 0
        override suspend fun expireOldHabitInvitations() {}

        // Utility
        override suspend fun areFriends(userId1: String, userId2: String): Boolean =
            friends.contains(userId1 to userId2) || friends.contains(userId2 to userId1)
        override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String) = true
        override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String) = true
        override suspend fun expireOldFriendRequests() {}

        // FCM
        override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) {}
        override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) {}
        override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) {}
        override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String, habitId: String) {}
        override suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeTitle: String, habitTitle: String) {}
        override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeTitle: String) {}
        override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeTitle: String) {}
        override suspend fun sendChallengeCompletedNotification(toUserId: String, challengeTitle: String, message: String, isWinner: Boolean) {}
        override suspend fun sendParticipantJoinedNotification(toUserId: String, joinedUserName: String, habitTitle: String, habitId: String) {}
        override suspend fun sendChallengeCancelledNotification(toUserId: String, challengeTitle: String, reason: String) {}
        override suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String>) {}

        fun addFriendship(a: String, b: String) { friends.add(a to b) }
    }

    // Удалён фейковый наследник финального use case — будем использовать реальный с фейк-репами

    @Test
    fun createChallenge_success() = runBlocking {
        val challengeRepo = FakeChallengeRepository()
        val habitRepo = FakeHabitRepository()
        val socialRepo = FakeSocialRepository().apply { addFriendship("creator", "p2") }

        val habit = Habit(
            id = "h1",
            title = "Workout",
            description = "",
            icon = "",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.DAILY,
            reminderTime = null,
            createdBy = "creator",
            participants = emptyList(),
            isActive = true,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
        habitRepo.insertHabit(habit)

        val useCase = CreateChallengeUseCase(challengeRepo, habitRepo, socialRepo)
        val start = LocalDate(2025, 1, 1)
        val end = LocalDate(2025, 12, 31)
        val result = useCase(
            habitId = habit.id,
            title = "Challenge",
            description = "",
            createdBy = "creator",
            participantIds = listOf("p2"),
            startDate = start,
            endDate = end,
            rules = ChallengeRules(),
            reward = ""
        )

        assertTrue(result.isSuccess)
        val ch = result.getOrNull()!!
        assertEquals("h1", ch.habitId)
        assertNotNull(ch.createdAt)
        assertNotNull(ch.updatedAt)
    }

    @Test
    fun joinChallenge_addsParticipant_andUpdatesTime() = runBlocking {
        val challengeRepo = FakeChallengeRepository()
        val socialRepo = FakeSocialRepository().apply { addFriendship("u2", "creator") }

        val ch = Challenge(
            id = UUID.randomUUID().toString(),
            habitId = "h1",
            title = "Ch",
            description = "",
            createdBy = "creator",
            participants = listOf("creator"),
            startDate = LocalDate(2025,1,1),
            endDate = LocalDate(2025,12,31),
            rules = ChallengeRules(),
            reward = "",
            status = ChallengeStatus.ACTIVE,
            winnerId = null,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
        challengeRepo.insertChallenge(ch)

        val useCase = JoinChallengeUseCase(challengeRepo, socialRepo)
        val res = useCase(ch.id, "u2")
        assertTrue(res.isSuccess)
        val updated = res.getOrNull()!!
        assertTrue(updated.participants.contains("u2"))
        assertTrue(updated.updatedAt >= ch.updatedAt)
    }

    @Test
    fun leaveChallenge_cancelIfOnlyCreatorLeft() = runBlocking {
        val challengeRepo = FakeChallengeRepository()
        val socialRepo = FakeSocialRepository()

        val ch = Challenge(
            id = UUID.randomUUID().toString(),
            habitId = "h1",
            title = "Ch",
            description = "",
            createdBy = "creator",
            participants = listOf("creator", "u2"),
            startDate = LocalDate(2025,1,1),
            endDate = LocalDate(2025,12,31),
            rules = ChallengeRules(),
            reward = "",
            status = ChallengeStatus.ACTIVE,
            winnerId = null,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
        challengeRepo.insertChallenge(ch)

        val useCase = LeaveChallengeUseCase(challengeRepo, socialRepo)
        val res = useCase(ch.id, "u2")
        assertTrue(res.isSuccess)
        val updated = res.getOrNull()!!
        assertEquals(1, updated.participants.size)
        assertEquals(ChallengeStatus.CANCELLED, updated.status)
    }

    @Test
    fun updateChallengeProgress_finishesWhenEndDatePassed() = runBlocking {
        val challengeRepo = FakeChallengeRepository()
        val habitEntryRepo = FakeHabitEntryRepository()
        val socialRepo = FakeSocialRepository()
        val resultsUseCase = GetChallengeResultsUseCase(challengeRepo, habitEntryRepo, socialRepo)

        // закончился вчера
        val yesterday = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.minus(1, DateTimeUnit.DAY)
        val ch = Challenge(
            id = UUID.randomUUID().toString(),
            habitId = "h1",
            title = "Ch",
            description = "",
            createdBy = "creator",
            participants = listOf("creator"),
            startDate = LocalDate(2025,1,1),
            endDate = yesterday,
            rules = ChallengeRules(type = ChallengeType.COMPLETION_RATE),
            reward = "",
            status = ChallengeStatus.ACTIVE,
            winnerId = null,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
        challengeRepo.insertChallenge(ch)

        val useCase = UpdateChallengeProgressUseCase(challengeRepo, habitEntryRepo, socialRepo, resultsUseCase)
        val res = useCase("h1", "creator")
        assertTrue(res.isSuccess)
        val updated = challengeRepo.getChallengeById(ch.id)!!
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertNotNull(updated.updatedAt)
    }
}
