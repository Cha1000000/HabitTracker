package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.User
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.UUID

class CloseExpiredChallengesUseCaseTest {

    private class FakeAuthRepository(
        private val currentUserId: String?
    ) : AuthRepository {
        override fun getCurrentUser() = MutableStateFlow<User?>(null)
        override fun getCurrentUserId(): String? = currentUserId
        override suspend fun login(email: String, password: String) = MutableStateFlow(Result.failure<Unit>(Exception("not used")))
        override suspend fun register(email: String, password: String) = MutableStateFlow(Result.failure<Unit>(Exception("not used")))
        override suspend fun signInWithGoogle() = MutableStateFlow(Result.failure<android.content.Intent?>(Exception("not used")))
        override suspend fun signOut() = MutableStateFlow(Result.success(Unit))
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

    private class FakeChallengeRepository : ChallengeRepository {
        private val items = LinkedHashMap<String, Challenge>()
        private val allFlow = MutableStateFlow<List<Challenge>>(emptyList())

        val updated = mutableListOf<Challenge>()

        val completedDaysFromFirestore = mutableMapOf<Pair<String, String>, Int>()
        val streakFromFirestore = mutableMapOf<Pair<String, String>, Int>()
        val streakForUserInChallenge = mutableMapOf<Pair<String, String>, Int>()

        var getCurrentStreakForUserInChallengeCalls = 0
        var getCurrentStreakFromFirestoreCalls = 0

        private fun emit() {
            allFlow.value = items.values.toList()
        }

        fun seed(challenges: List<Challenge>) {
            challenges.forEach { items[it.id] = it }
            emit()
        }

        override suspend fun insertChallenge(challenge: Challenge) {
            items[challenge.id] = challenge
            emit()
        }

        override suspend fun updateChallenge(challenge: Challenge) {
            items[challenge.id] = challenge
            updated.add(challenge)
            emit()
        }

        override suspend fun deleteChallenge(challengeId: String) {
            items.remove(challengeId)
            emit()
        }

        override suspend fun getChallengeById(challengeId: String): Challenge? = items[challengeId]

        override fun getAllChallenges(): Flow<List<Challenge>> = allFlow.asStateFlow()

        override fun getChallengesForUser(userId: String): Flow<List<Challenge>> = allFlow.asStateFlow()

        override fun getActiveChallenges(): Flow<List<Challenge>> = allFlow.asStateFlow()

        override fun getCompletedChallenges(): Flow<List<Challenge>> = allFlow.asStateFlow()

        override suspend fun getChallengesForHabit(habitId: String): List<Challenge> = items.values.filter { it.habitId == habitId }

        override suspend fun joinChallenge(challengeId: String, userId: String) = Unit

        override suspend fun leaveChallenge(challengeId: String, userId: String) = Unit

        override suspend fun getChallengeParticipants(challengeId: String): List<String> = items[challengeId]?.participants ?: emptyList()

        override suspend fun updateChallengeStatus(challengeId: String, status: String) = Unit

        override suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>> = emptyList()

        override suspend fun endChallenge(challengeId: String, winnerId: String?) = Unit

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
        ): Int {
            getCurrentStreakForUserInChallengeCalls++
            return streakForUserInChallenge[habitId to userId] ?: 0
        }

        override suspend fun getCompletedDaysFromFirestore(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = completedDaysFromFirestore[habitId to userId] ?: 0

        override suspend fun getCurrentStreakFromFirestore(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int {
            getCurrentStreakFromFirestoreCalls++
            return streakFromFirestore[habitId to userId] ?: 0
        }

        override suspend fun acceptChallengeInvitation(challengeId: String, userId: String) = Unit

        override suspend fun declineChallengeInvitation(challengeId: String, userId: String) = Unit

        override suspend fun cancelChallenge(challengeId: String) = Unit

        override suspend fun addParticipantsToChallenge(challengeId: String, participantIds: List<String>) = Unit
        
        override suspend fun getTotalValueForUserInChallenge(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
        
        override suspend fun getTotalValueFromFirestore(
            habitId: String,
            userId: String,
            startDate: LocalDate,
            endDate: LocalDate
        ): Int = 0
    }

    private data class CompletedNotif(
        val toUserId: String,
        val challengeId: String,
        val challengeTitle: String,
        val isWinner: Boolean
    )

    private data class CancelledNotif(
        val toUserId: String,
        val challengeId: String,
        val challengeTitle: String
    )

    private class FakeSocialRepository : SocialRepository {
        val completed = mutableListOf<CompletedNotif>()
        val cancelled = mutableListOf<CancelledNotif>()

        override suspend fun searchUsers(query: String, currentUserId: String) = emptyList<User>()
        override suspend fun getUserById(userId: String): User? = User(id = userId, name = "User$userId")
        override suspend fun getUserByIdFromFirestore(userId: String): User? = getUserById(userId)
        override suspend fun updateUserFcmToken(userId: String, token: String) = Unit
        override suspend fun updateFriendsCount(userId: String, count: Int) = Unit

        override suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?) =
            Result.failure<FriendRequest>(Exception("not used"))
        override suspend fun acceptFriendRequest(requestId: String) =
            Result.failure<Friendship>(Exception("not used"))
        override suspend fun declineFriendRequest(requestId: String) =
            Result.failure<FriendRequest>(Exception("not used"))
        override suspend fun cancelFriendRequest(requestId: String) = Result.success(Unit)
        override suspend fun getFriendRequestById(requestId: String) = null
        override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String) = null
        override fun getIncomingFriendRequests(userId: String) = MutableStateFlow(emptyList<com.habittracker.domain.model.FriendRequestWithUsers>()).asStateFlow()
        override fun getOutgoingFriendRequests(userId: String) = MutableStateFlow(emptyList<com.habittracker.domain.model.FriendRequestWithUsers>()).asStateFlow()
        override fun getIncomingRequestsCount(userId: String) = MutableStateFlow(0).asStateFlow()

        override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String) = null
        override suspend fun removeFriend(currentUserId: String, friendId: String) = Result.success(Unit)
        override suspend fun blockUser(currentUserId: String, userIdToBlock: String) =
            Result.failure<Friendship>(Exception("not used"))
        override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String) =
            Result.failure<Friendship>(Exception("not used"))
        override fun getFriends(userId: String) = MutableStateFlow(emptyList<com.habittracker.domain.model.FriendshipWithUsers>()).asStateFlow()
        override fun getFriendsCount(userId: String) = MutableStateFlow(0).asStateFlow()

        override suspend fun sendHabitInvitation(
            habitId: String,
            habitTitle: String,
            fromUserId: String,
            fromUserName: String,
            toUserId: String,
            message: String?
        ) = Result.failure<HabitInvitation>(Exception("not used"))

        override suspend fun acceptHabitInvitation(invitationId: String) = Result.failure<HabitInvitation>(Exception("not used"))
        override suspend fun declineHabitInvitation(invitationId: String) = Result.failure<HabitInvitation>(Exception("not used"))
        override suspend fun cancelHabitInvitation(invitationId: String) = Result.success(Unit)
        override suspend fun getHabitInvitationById(invitationId: String) = null
        override suspend fun getPendingHabitInvitation(habitId: String, userId: String) = null
        override fun getIncomingHabitInvitations(userId: String) = MutableStateFlow(emptyList<com.habittracker.domain.model.HabitInvitation>()).asStateFlow()
        override fun getOutgoingHabitInvitations(userId: String) = MutableStateFlow(emptyList<com.habittracker.domain.model.HabitInvitation>()).asStateFlow()
        override fun getHabitInvitations(habitId: String) = MutableStateFlow(emptyList<com.habittracker.domain.model.HabitInvitation>()).asStateFlow()
        override fun getPendingHabitInvitationsCount(userId: String) = MutableStateFlow(0).asStateFlow()
        override suspend fun getActiveOutgoingInvitationsCount(userId: String) = 0
        override suspend fun expireOldHabitInvitations() = Unit

        override suspend fun areFriends(userId1: String, userId2: String) = true
        override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String) = true
        override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String) = true
        override suspend fun expireOldFriendRequests() = Unit

        override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) = Unit
        override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) = Unit
        override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) = Unit
        override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String, habitId: String) = Unit
        override suspend fun sendParticipantJoinedNotification(toUserId: String, joinedUserName: String, habitTitle: String, habitId: String) = Unit

        override suspend fun sendChallengeInvitationNotification(
            toUserId: String,
            fromUserName: String,
            challengeId: String,
            challengeTitle: String,
            habitTitle: String
        ) = Unit

        override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeId: String, challengeTitle: String) = Unit
        override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeId: String, challengeTitle: String) = Unit

        override suspend fun sendChallengeCompletedNotification(
            toUserId: String,
            challengeId: String,
            challengeTitle: String,
            message: String,
            isWinner: Boolean
        ) {
            completed.add(CompletedNotif(toUserId, challengeId, challengeTitle, isWinner))
        }

        override suspend fun sendChallengeCancelledNotification(
            toUserId: String,
            challengeId: String,
            challengeTitle: String,
            reason: String
        ) {
            cancelled.add(CancelledNotif(toUserId, challengeId, challengeTitle))
        }

        override suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String>) = Unit
    }

    private fun challenge(
        today: LocalDate,
        status: ChallengeStatus = ChallengeStatus.ACTIVE,
        type: ChallengeType = ChallengeType.TOTAL_DAYS,
        target: Int = 10,
        createdBy: String = "creator",
        accepted: List<String> = emptyList(),
        participants: List<String> = accepted
    ): Challenge {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        return Challenge(
            id = UUID.randomUUID().toString(),
            habitId = "h1",
            title = "Ch",
            description = "",
            createdBy = createdBy,
            participants = (participants + createdBy).distinct(),
            acceptedParticipants = accepted,
            startDate = today.minus(10, DateTimeUnit.DAY),
            endDate = yesterday,
            rules = ChallengeRules(type = type, targetValue = target),
            reward = "",
            status = status,
            winnerId = null,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
    }

    @Test
    fun expired_noProgress_setsExpired_andSendsCancelled() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val repo = FakeChallengeRepository()
        val social = FakeSocialRepository()
        val auth = FakeAuthRepository(currentUserId = "creator")

        val ch = challenge(today, accepted = listOf("u2"))
        repo.seed(listOf(ch))

        repo.completedDaysFromFirestore["h1" to "creator"] = 0
        repo.completedDaysFromFirestore["h1" to "u2"] = 0

        val updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        val checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        val useCase = CloseExpiredChallengesUseCase(auth, repo, social, updateUserPointsUseCase, checkAchievementsUseCase)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val updated = repo.getChallengeById(ch.id)!!
        assertEquals(ChallengeStatus.EXPIRED, updated.status)
        assertNull(updated.winnerId)
        assertNotNull(updated.updatedAt)

        val toUsers = social.cancelled.map { it.toUserId }.sorted()
        assertEquals(listOf("creator", "u2"), toUsers)
        assertTrue(social.completed.isEmpty())
    }

    @Test
    fun expired_withProgress_setsCompleted_andSendsCompleted() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val repo = FakeChallengeRepository()
        val social = FakeSocialRepository()
        val auth = FakeAuthRepository(currentUserId = "creator")

        val ch = challenge(today, accepted = listOf("u2"), type = ChallengeType.TOTAL_DAYS, target = 10)
        repo.seed(listOf(ch))

        repo.completedDaysFromFirestore["h1" to "creator"] = 2
        repo.completedDaysFromFirestore["h1" to "u2"] = 5

        val updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        val checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        val useCase = CloseExpiredChallengesUseCase(auth, repo, social, updateUserPointsUseCase, checkAchievementsUseCase)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val updated = repo.getChallengeById(ch.id)!!
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertEquals("u2", updated.winnerId)

        val winnerNotifs = social.completed.filter { it.isWinner }
        assertEquals(1, winnerNotifs.size)
        assertEquals("u2", winnerNotifs.first().toUserId)

        val allTo = social.completed.map { it.toUserId }.sorted()
        assertEquals(listOf("creator", "u2"), allTo)
        assertTrue(social.cancelled.isEmpty())
    }

    @Test
    fun notExpired_endDateIsToday_doesNothing() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val repo = FakeChallengeRepository()
        val social = FakeSocialRepository()
        val auth = FakeAuthRepository(currentUserId = "creator")

        val ch = Challenge(
            id = UUID.randomUUID().toString(),
            habitId = "h1",
            title = "Ch",
            description = "",
            createdBy = "creator",
            participants = listOf("creator"),
            acceptedParticipants = emptyList(),
            startDate = today.minus(10, DateTimeUnit.DAY),
            endDate = today,
            rules = ChallengeRules(type = ChallengeType.TOTAL_DAYS, targetValue = 10),
            reward = "",
            status = ChallengeStatus.ACTIVE,
            winnerId = null,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )

        repo.seed(listOf(ch))

        val updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        val checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        val useCase = CloseExpiredChallengesUseCase(auth, repo, social, updateUserPointsUseCase, checkAchievementsUseCase)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertTrue(repo.updated.isEmpty())
        assertTrue(social.completed.isEmpty())
        assertTrue(social.cancelled.isEmpty())
    }

    @Test
    fun ignoresNonAcceptedParticipants_whenSelectingWinner() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val repo = FakeChallengeRepository()
        val social = FakeSocialRepository()
        val auth = FakeAuthRepository(currentUserId = "creator")

        val ch = challenge(
            today = today,
            accepted = listOf("u2"),
            participants = listOf("u2", "u3")
        )
        repo.seed(listOf(ch))

        repo.completedDaysFromFirestore["h1" to "creator"] = 2
        repo.completedDaysFromFirestore["h1" to "u2"] = 5
        repo.completedDaysFromFirestore["h1" to "u3"] = 9

        val updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        val checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        val useCase = CloseExpiredChallengesUseCase(auth, repo, social, updateUserPointsUseCase, checkAchievementsUseCase)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val updated = repo.getChallengeById(ch.id)!!
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertEquals("u2", updated.winnerId)
    }

    @Test
    fun tieBreak_sameProgress_picksLexicographicallySmallerUserId() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val repo = FakeChallengeRepository()
        val social = FakeSocialRepository()
        val auth = FakeAuthRepository(currentUserId = "creator")

        val ch = challenge(today, createdBy = "creator", accepted = listOf("b", "a"), type = ChallengeType.TOTAL_DAYS, target = 10)
        repo.seed(listOf(ch))

        repo.completedDaysFromFirestore["h1" to "creator"] = 0
        repo.completedDaysFromFirestore["h1" to "a"] = 5
        repo.completedDaysFromFirestore["h1" to "b"] = 5

        val updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        val checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        val useCase = CloseExpiredChallengesUseCase(auth, repo, social, updateUserPointsUseCase, checkAchievementsUseCase)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val updated = repo.getChallengeById(ch.id)!!
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertEquals("a", updated.winnerId)
    }

    @Test
    fun streakProgress_usesLocalForCurrentUser_andFirestoreForOthers() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val repo = FakeChallengeRepository()
        val social = FakeSocialRepository()
        val auth = FakeAuthRepository(currentUserId = "creator")

        val ch = challenge(today, accepted = listOf("u2"), type = ChallengeType.STREAK, target = 7)
        repo.seed(listOf(ch))

        repo.streakForUserInChallenge["h1" to "creator"] = 3
        repo.streakFromFirestore["h1" to "u2"] = 4

        val updateUserPointsUseCase = FakeUpdateUserPointsUseCase()
        val checkAchievementsUseCase = mock(CheckAchievementsUseCase::class.java)
        val useCase = CloseExpiredChallengesUseCase(auth, repo, social, updateUserPointsUseCase, checkAchievementsUseCase)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        assertEquals(1, repo.getCurrentStreakForUserInChallengeCalls)
        assertEquals(1, repo.getCurrentStreakFromFirestoreCalls)

        val updated = repo.getChallengeById(ch.id)!!
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertEquals("u2", updated.winnerId)
    }
}
