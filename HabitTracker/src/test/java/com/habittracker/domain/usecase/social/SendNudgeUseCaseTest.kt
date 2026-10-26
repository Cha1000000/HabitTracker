package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.Nudge
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.NudgeRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SendNudgeUseCaseTest {

    private lateinit var nudgeRepository: FakeNudgeRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var socialRepository: FakeSocialRepository
    private lateinit var sendNudgeUseCase: SendNudgeUseCase

    private val testSenderId = "sender-user-id"
    private val testReceiverId = "receiver-user-id"
    private val testHabitId = "test-habit-id"

    @Before
    fun setUp() {
        nudgeRepository = FakeNudgeRepository()
        authRepository = FakeAuthRepository(testSenderId)
        userRepository = FakeUserRepository()
        habitRepository = FakeHabitRepository()
        socialRepository = FakeSocialRepository()

        sendNudgeUseCase = SendNudgeUseCase(
            nudgeRepository = nudgeRepository,
            authRepository = authRepository,
            userRepository = userRepository,
            habitRepository = habitRepository,
            socialRepository = socialRepository
        )
    }

    @Test
    fun `invoke returns Success when nudge is sent successfully`() = runTest {
        val result = sendNudgeUseCase(testHabitId, testReceiverId)

        assertTrue(result is SendNudgeUseCase.Result.Success)
        assertEquals(1, nudgeRepository.sentNudges.size)
        assertEquals(testHabitId, nudgeRepository.sentNudges.first().habitId)
        assertEquals(testSenderId, nudgeRepository.sentNudges.first().senderId)
        assertEquals(testReceiverId, nudgeRepository.sentNudges.first().receiverId)
    }

    @Test
    fun `invoke returns Error when user is not authenticated`() = runTest {
        authRepository.setCurrentUserId(null)

        val result = sendNudgeUseCase(testHabitId, testReceiverId)

        assertTrue(result is SendNudgeUseCase.Result.Error)
        assertEquals("User not authenticated", (result as SendNudgeUseCase.Result.Error).message)
    }

    @Test
    fun `invoke returns Error when trying to nudge yourself`() = runTest {
        val result = sendNudgeUseCase(testHabitId, testSenderId)

        assertTrue(result is SendNudgeUseCase.Result.Error)
        assertEquals("Cannot nudge yourself", (result as SendNudgeUseCase.Result.Error).message)
    }

    @Test
    fun `invoke returns OnCooldown when nudge was sent recently`() = runTest {
        nudgeRepository.setCanSendNudge(false)

        val result = sendNudgeUseCase(testHabitId, testReceiverId)

        assertTrue(result is SendNudgeUseCase.Result.OnCooldown)
    }

    @Test
    fun `invoke returns Error when repository fails`() = runTest {
        nudgeRepository.setShouldFail(true)

        val result = sendNudgeUseCase(testHabitId, testReceiverId)

        assertTrue(result is SendNudgeUseCase.Result.Error)
    }

    @Test
    fun `invoke sends push notification after successful nudge`() = runTest {
        val result = sendNudgeUseCase(testHabitId, testReceiverId)

        assertTrue(result is SendNudgeUseCase.Result.Success)
        assertEquals(1, socialRepository.nudgeNotificationsSent.size)
        assertEquals(testReceiverId, socialRepository.nudgeNotificationsSent.first().toUserId)
    }
}

// Fake implementations for testing

class FakeNudgeRepository : NudgeRepository {
    val sentNudges = mutableListOf<Nudge>()
    private var canSendNudge = true
    private var shouldFail = false

    fun setCanSendNudge(value: Boolean) {
        canSendNudge = value
    }

    fun setShouldFail(value: Boolean) {
        shouldFail = value
    }

    override suspend fun sendNudge(nudge: Nudge): Result<Unit> {
        return if (shouldFail) {
            Result.failure(Exception("Test error"))
        } else {
            sentNudges.add(nudge)
            Result.success(Unit)
        }
    }

    override suspend fun getLastNudge(habitId: String, senderId: String, receiverId: String): Nudge? {
        return sentNudges.lastOrNull { 
            it.habitId == habitId && it.senderId == senderId && it.receiverId == receiverId 
        }
    }

    override suspend fun canSendNudge(habitId: String, senderId: String, receiverId: String): Boolean {
        return canSendNudge
    }

    override fun getNudgedUserIdsFlow(habitId: String, senderId: String): Flow<Set<String>> {
        return flowOf(sentNudges.filter { it.habitId == habitId && it.senderId == senderId }
            .map { it.receiverId }.toSet())
    }

    override suspend fun getNudgedUserIds(habitId: String, senderId: String): Set<String> {
        return sentNudges.filter { it.habitId == habitId && it.senderId == senderId }
            .map { it.receiverId }.toSet()
    }
}

class FakeAuthRepository(private var currentUserId: String?) : AuthRepository {
    fun setCurrentUserId(userId: String?) {
        currentUserId = userId
    }

    override fun getCurrentUserId(): String? = currentUserId
    override fun getCurrentUser(): Flow<User?> = flowOf(currentUserId?.let { 
        User(id = it, name = "Test User", email = "test@test.com", 
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            lastActiveAt = Clock.System.now().toEpochMilliseconds())
    })
    override suspend fun login(email: String, password: String): Flow<Result<Unit>> = flowOf(Result.failure(NotImplementedError()))
    override suspend fun register(email: String, password: String): Flow<Result<Unit>> = flowOf(Result.failure(NotImplementedError()))
    override suspend fun signInWithGoogle(activityContext: android.content.Context): Flow<Result<android.content.Intent?>> = flowOf(Result.failure(NotImplementedError()))
    override suspend fun signOut(): Flow<Result<Unit>> = flowOf(Result.success(Unit))
    override suspend fun sendPasswordResetEmail(email: String): Flow<Result<Unit>> = flowOf(Result.success(Unit))
}

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()

    init {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        users["sender-user-id"] = User(
            id = "sender-user-id",
            name = "Sender User",
            email = "sender@test.com",
            createdAt = today,
            lastActiveAt = Clock.System.now().toEpochMilliseconds()
        )
        users["receiver-user-id"] = User(
            id = "receiver-user-id",
            name = "Receiver User",
            email = "receiver@test.com",
            createdAt = today,
            lastActiveAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    override suspend fun insertUser(user: User) { users[user.id] = user }
    override suspend fun updateUser(user: User) { users[user.id] = user }
    override suspend fun deleteUser(userId: String) { users.remove(userId) }
    override suspend fun getUserById(userId: String): User? = users[userId]
    override suspend fun getUserByEmail(email: String): User? = users.values.find { it.email == email }
    override fun getAllUsers(): Flow<List<User>> = flowOf(users.values.toList())
    override suspend fun searchUsersByName(query: String): List<User> = emptyList()
    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {}
    override suspend fun upsertUserRemoteAndLocal(user: User, fcmToken: String?) {}
    override fun observeUser(userId: String): Flow<User?> = flowOf(users[userId])
    override suspend fun backfillAvatarThumbnails(): Result<Int> = Result.success(0)
    override suspend fun updateLastActive(userId: String) {}
    override suspend fun getUserFromFirestore(userId: String): User? = users[userId]
}

class FakeHabitRepository : HabitRepository {
    private val habits = mutableMapOf<String, Habit>()

    init {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        habits["test-habit-id"] = Habit(
            id = "test-habit-id",
            title = "Test Habit",
            createdBy = "sender-user-id",
            createdAt = today,
            updatedAt = today,
            color = "#FFFFFF",
            type = HabitType.GROUP,
            participants = listOf("receiver-user-id")
        )
    }

    override suspend fun getHabitById(habitId: String): Habit? = habits[habitId]
    override suspend fun insertHabit(habit: Habit) { habits[habit.id] = habit }
    override suspend fun updateHabit(habit: Habit) { habits[habit.id] = habit }
    override suspend fun deleteHabit(habitId: String) { habits.remove(habitId) }
    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
    override fun getHabitByIdFlow(habitId: String): Flow<Habit?> = flowOf(habits[habitId])
    override fun getAllHabits(): Flow<List<Habit>> = flowOf(habits.values.toList())
    override fun getHabitsByUserId(userId: String): Flow<List<Habit>> = flowOf(emptyList())
    override fun getPersonalHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList())
    override fun getGroupHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun addParticipantToHabit(habitId: String, userId: String) {}
    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {}
    override suspend fun getHabitParticipants(habitId: String): List<String> = emptyList()
    override suspend fun searchHabits(userId: String, query: String): List<Habit> = emptyList()
    override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun getHabitParticipantSettings(habitId: String, userId: String) = null
    override suspend fun updateHabitParticipantSettings(settings: com.habittracker.domain.model.HabitParticipantSettings) {}
    override fun observeHabitParticipantSettings(habitId: String, userId: String) = flowOf(null)
}

data class NudgeNotificationData(
    val toUserId: String,
    val fromUserName: String,
    val habitTitle: String,
    val habitId: String
)

class FakeSocialRepository : SocialRepository {
    val nudgeNotificationsSent = mutableListOf<NudgeNotificationData>()

    override suspend fun sendNudgeNotification(toUserId: String, fromUserName: String, habitTitle: String, habitId: String) {
        nudgeNotificationsSent.add(NudgeNotificationData(toUserId, fromUserName, habitTitle, habitId))
    }

    // Stub implementations for other methods
    override suspend fun getUserById(userId: String): User? = null
    override suspend fun getUserByIdFromFirestore(userId: String): User? = null
    override suspend fun searchUsers(query: String, currentUserId: String): List<User> = emptyList()
    override suspend fun updateUserFcmToken(userId: String, token: String) {}
    override suspend fun updateFriendsCount(userId: String, count: Int) {}
    override suspend fun recalculateFriendsCount(userId: String) {}
    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?): Result<com.habittracker.domain.model.FriendRequest> = Result.failure(NotImplementedError())
    override suspend fun acceptFriendRequest(requestId: String): Result<com.habittracker.domain.model.Friendship> = Result.failure(NotImplementedError())
    override suspend fun declineFriendRequest(requestId: String): Result<com.habittracker.domain.model.FriendRequest> = Result.failure(NotImplementedError())
    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getFriendRequestById(requestId: String): com.habittracker.domain.model.FriendRequest? = null
    override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String): com.habittracker.domain.model.FriendRequest? = null
    override fun getIncomingFriendRequests(userId: String): Flow<List<com.habittracker.domain.model.FriendRequestWithUsers>> = flowOf(emptyList())
    override fun getOutgoingFriendRequests(userId: String): Flow<List<com.habittracker.domain.model.FriendRequestWithUsers>> = flowOf(emptyList())
    override fun getIncomingRequestsCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String): com.habittracker.domain.model.Friendship? = null
    override suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> = Result.success(Unit)
    override suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<com.habittracker.domain.model.Friendship> = Result.failure(NotImplementedError())
    override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<com.habittracker.domain.model.Friendship> = Result.failure(NotImplementedError())
    override fun getFriends(userId: String): Flow<List<com.habittracker.domain.model.FriendshipWithUsers>> = flowOf(emptyList())
    override fun getFriendsCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun sendHabitInvitation(habitId: String, habitTitle: String, fromUserId: String, fromUserName: String, toUserId: String, message: String?): Result<com.habittracker.domain.model.HabitInvitation> = Result.failure(NotImplementedError())
    override suspend fun acceptHabitInvitation(invitationId: String): Result<com.habittracker.domain.model.HabitInvitation> = Result.failure(NotImplementedError())
    override suspend fun declineHabitInvitation(invitationId: String): Result<com.habittracker.domain.model.HabitInvitation> = Result.failure(NotImplementedError())
    override suspend fun cancelHabitInvitation(invitationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getHabitInvitationById(invitationId: String): com.habittracker.domain.model.HabitInvitation? = null
    override suspend fun getPendingHabitInvitation(habitId: String, userId: String): com.habittracker.domain.model.HabitInvitation? = null
    override fun getIncomingHabitInvitations(userId: String): Flow<List<com.habittracker.domain.model.HabitInvitation>> = flowOf(emptyList())
    override fun getOutgoingHabitInvitations(userId: String): Flow<List<com.habittracker.domain.model.HabitInvitation>> = flowOf(emptyList())
    override fun getHabitInvitations(habitId: String): Flow<List<com.habittracker.domain.model.HabitInvitation>> = flowOf(emptyList())
    override fun getPendingHabitInvitationsCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun getActiveOutgoingInvitationsCount(userId: String): Int = 0
    override suspend fun expireOldHabitInvitations() {}
    override suspend fun areFriends(userId1: String, userId2: String): Boolean = false
    override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String): Boolean = false
    override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String): Boolean = false
    override suspend fun expireOldFriendRequests() {}
    override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) {}
    override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) {}
    override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) {}
    override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String, habitId: String) {}
    override suspend fun sendParticipantJoinedNotification(toUserId: String, joinedUserName: String, habitTitle: String, habitId: String) {}
    override suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String>) {}
    override suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeId: String, challengeTitle: String, habitTitle: String) {}
    override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeId: String, challengeTitle: String) {}
    override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeId: String, challengeTitle: String) {}
    override suspend fun sendChallengeCompletedNotification(toUserId: String, challengeId: String, challengeTitle: String, message: String, isWinner: Boolean) {}
    override suspend fun sendChallengeCancelledNotification(toUserId: String, challengeId: String, challengeTitle: String, reason: String) {}
}
