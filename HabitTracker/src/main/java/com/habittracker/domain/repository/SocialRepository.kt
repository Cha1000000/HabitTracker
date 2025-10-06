package com.habittracker.domain.repository

import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.User
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    
    // User search and management
    suspend fun searchUsers(query: String, currentUserId: String): List<User>
    suspend fun getUserById(userId: String): User?
    suspend fun getUserByIdFromFirestore(userId: String): User?
    suspend fun updateUserFcmToken(userId: String, token: String)
    suspend fun updateFriendsCount(userId: String, count: Int)
    
    // Friend requests
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?): Result<FriendRequest>
    suspend fun acceptFriendRequest(requestId: String): Result<Friendship>
    suspend fun declineFriendRequest(requestId: String): Result<FriendRequest>
    suspend fun cancelFriendRequest(requestId: String): Result<Unit>
    suspend fun getFriendRequestById(requestId: String): FriendRequest?
    suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String): FriendRequest?
    fun getIncomingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>>
    fun getOutgoingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>>
    fun getIncomingRequestsCount(userId: String): Flow<Int>
    
    // Friendships
    suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String): Friendship?
    suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit>
    suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<Friendship>
    suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<Friendship>
    fun getFriends(userId: String): Flow<List<FriendshipWithUsers>>
    fun getFriendsCount(userId: String): Flow<Int>
    
    // Habit invitations
    suspend fun sendHabitInvitation(
        habitId: String, 
        habitTitle: String, 
        fromUserId: String, 
        fromUserName: String, 
        toUserId: String, 
        message: String?
    ): Result<HabitInvitation>
    suspend fun acceptHabitInvitation(invitationId: String): Result<HabitInvitation>
    suspend fun declineHabitInvitation(invitationId: String): Result<HabitInvitation>
    suspend fun cancelHabitInvitation(invitationId: String): Result<Unit>
    suspend fun getHabitInvitationById(invitationId: String): HabitInvitation?
    suspend fun getPendingHabitInvitation(habitId: String, userId: String): HabitInvitation?
    fun getIncomingHabitInvitations(userId: String): Flow<List<HabitInvitation>>
    fun getOutgoingHabitInvitations(userId: String): Flow<List<HabitInvitation>>
    fun getHabitInvitations(habitId: String): Flow<List<HabitInvitation>>
    fun getPendingHabitInvitationsCount(userId: String): Flow<Int>
    suspend fun getActiveOutgoingInvitationsCount(userId: String): Int
    suspend fun expireOldHabitInvitations()
    
    // Utility functions
    suspend fun areFriends(userId1: String, userId2: String): Boolean
    suspend fun canSendFriendRequest(fromUserId: String, toUserId: String): Boolean
    suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String): Boolean
    suspend fun expireOldFriendRequests()
    
    // FCM notifications
    suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String)
    suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String)
    suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String)
    suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String, habitId: String)
    
    // Challenge notifications
    suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeTitle: String, habitTitle: String)
    suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeTitle: String)
    suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeTitle: String)
    suspend fun sendChallengeCompletedNotification(toUserId: String, challengeTitle: String, message: String, isWinner: Boolean)
    suspend fun sendChallengeCancelledNotification(toUserId: String, challengeTitle: String, reason: String)
    
    suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String> = emptyMap())
}
