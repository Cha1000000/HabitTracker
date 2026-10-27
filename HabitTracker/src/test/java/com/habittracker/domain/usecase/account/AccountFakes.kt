package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.PendingAccountDeletion
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.ReauthRequiredException
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout

/** Фейки репозиториев удаления: пишут вызовы в общий журнал [calls], чтобы проверять порядок шагов. */
class FakeAuth(private val calls: MutableList<String>) : AuthRepository {
    var userId: String? = "me"
    var signInMethod: SignInMethod? = SignInMethod.PASSWORD
    var recentSignIn = false
    var reauthError: Exception? = null
    var deleteError: Exception? = null
    var deletedOnServer = false
    var lastReauth: ReauthRequest? = null

    override fun getCurrentUserId(): String? = userId
    override fun observeCurrentUserId(): Flow<String?> = flowOf(userId)
    override fun currentSignInMethod(): SignInMethod? = signInMethod
    override fun isRecentSignIn(): Boolean = recentSignIn
    override suspend fun reauthenticate(request: ReauthRequest) {
        calls += "reauth"
        lastReauth = request
        reauthError?.let { throw it }
        if (request == ReauthRequest.None && !recentSignIn) throw ReauthRequiredException()
    }
    override suspend fun deleteCurrentAuthUser() {
        calls += "deleteAuth"
        deleteError?.let { throw it }
    }
    override suspend fun isCurrentUserDeleted(): Boolean = deletedOnServer

    override fun getCurrentUser(): Flow<User?> = flowOf(null)
    override suspend fun login(email: String, password: String) = flowOf(Result.success(Unit))
    override suspend fun register(email: String, password: String) = flowOf(Result.success(Unit))
    override suspend fun signInWithGoogle(activityContext: android.content.Context) =
        flowOf(Result.success<android.content.Intent?>(null))
    override suspend fun signOut() = flowOf(Result.success(Unit)).also {
        calls += "signOut"
        userId = null
    }
    override suspend fun sendPasswordResetEmail(email: String) = flowOf(Result.success(Unit))
}

class FakeAccount(private val calls: MutableList<String>) : AccountRepository {
    var remoteError: Exception? = null
    var pauseError: Exception? = null
    var timeoutOnRemote = false
    var hangOnRemote = false
    val marker = MutableStateFlow<PendingAccountDeletion?>(null)

    override suspend fun pauseSync() {
        calls += "pauseSync"
        pauseError?.let { throw it }
    }
    override fun resumeSync() {
        calls += "resumeSync"
    }
    override suspend fun deleteRemoteUserData(userId: String) {
        calls += "deleteRemote:$userId"
        remoteError?.let { throw it }
        if (timeoutOnRemote) withTimeout(1) { awaitCancellation() }
        if (hangOnRemote) awaitCancellation()
    }
    override suspend fun wipeLocalData() {
        calls += "wipeLocal"
        marker.value = null
    }
    override fun pendingDeletion(): PendingAccountDeletion? = marker.value
    override fun observePendingDeletion(): Flow<PendingAccountDeletion?> = marker
    override suspend fun markDeletion(userId: String, stage: DeletionStage) {
        calls += "mark:$stage"
        marker.value = PendingAccountDeletion(userId, stage)
    }
    override fun cancelPendingDeletion() {
        calls += "cancelPending"
        marker.value = null
    }
}
