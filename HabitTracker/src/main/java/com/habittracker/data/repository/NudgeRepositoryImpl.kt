package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.model.FirebaseNudge
import com.habittracker.domain.model.Nudge
import com.habittracker.domain.repository.NudgeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NudgeRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : NudgeRepository {
    
    companion object {
        private const val TAG = "NudgeRepository"
        private const val COLLECTION_NUDGES = "nudges"
    }
    
    override suspend fun sendNudge(nudge: Nudge): Result<Unit> {
        return try {
            val nudgeWithId = if (nudge.id.isBlank()) {
                nudge.copy(id = UUID.randomUUID().toString())
            } else {
                nudge
            }
            
            val firebaseNudge = FirebaseNudge.fromDomain(nudgeWithId)
            
            firestore.collection(COLLECTION_NUDGES)
                .document(nudgeWithId.id)
                .set(firebaseNudge)
                .await()
            
            Timber.tag(TAG).d("Nudge sent: ${nudgeWithId.senderId} -> ${nudgeWithId.receiverId} for habit ${nudgeWithId.habitId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send nudge")
            Result.failure(e)
        }
    }
    
    override suspend fun getLastNudge(habitId: String, senderId: String, receiverId: String): Nudge? {
        return try {
            // Используем простой запрос без orderBy, сортируем на клиенте
            val snapshot = firestore.collection(COLLECTION_NUDGES)
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("receiverId", receiverId)
                .get()
                .await()
            
            snapshot.documents
                .mapNotNull { it.toObject(FirebaseNudge::class.java)?.toDomain() }
                .maxByOrNull { it.sentAt }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get last nudge")
            null
        }
    }
    
    override suspend fun canSendNudge(habitId: String, senderId: String, receiverId: String): Boolean {
        val lastNudge = getLastNudge(habitId, senderId, receiverId)
        return lastNudge == null || !lastNudge.isOnCooldown()
    }
    
    override fun getNudgedUserIdsFlow(habitId: String, senderId: String): Flow<Set<String>> = callbackFlow {
        // Простой запрос без whereGreaterThan, фильтруем на клиенте
        val listener = firestore.collection(COLLECTION_NUDGES)
            .whereEqualTo("habitId", habitId)
            .whereEqualTo("senderId", senderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.tag(TAG).e(error, "Error listening to nudges")
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                
                val cooldownThreshold = System.currentTimeMillis() - Nudge.COOLDOWN_MILLIS
                val nudgedIds = snapshot?.documents
                    ?.mapNotNull { it.toObject(FirebaseNudge::class.java) }
                    ?.filter { it.sentAt > cooldownThreshold }
                    ?.map { it.receiverId }
                    ?.toSet()
                    ?: emptySet()
                
                trySend(nudgedIds)
            }
        
        awaitClose { listener.remove() }
    }
    
    override suspend fun getNudgedUserIds(habitId: String, senderId: String): Set<String> {
        return try {
            val cooldownThreshold = System.currentTimeMillis() - Nudge.COOLDOWN_MILLIS
            
            // Простой запрос без whereGreaterThan, фильтруем на клиенте
            val snapshot = firestore.collection(COLLECTION_NUDGES)
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("senderId", senderId)
                .get()
                .await()
            
            snapshot.documents
                .mapNotNull { it.toObject(FirebaseNudge::class.java) }
                .filter { it.sentAt > cooldownThreshold }
                .map { it.receiverId }
                .toSet()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get nudged user ids")
            emptySet()
        }
    }
}
