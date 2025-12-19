package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.ChallengeDao
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.entity.ChallengeEntity
import com.habittracker.data.sync.SyncManager
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.repository.ChallengeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.local.mappers.toDomain as challengeToDomain
import com.habittracker.data.local.mappers.toEntity as challengeToEntity

@Singleton
class ChallengeRepositoryImpl @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val habitEntryDao: HabitEntryDao,
    private val syncManager: SyncManager,
    private val firestore: FirebaseFirestore
) : ChallengeRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override suspend fun insertChallenge(challenge: Challenge) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "insertChallenge: id=${challenge.id}, participants=${challenge.participants}")
        challengeDao.insertChallenge(challenge.challengeToEntity(updatedAtMillis = now))
        
        // Прямая синхронизация в Firestore для немедленной доступности другим участникам
        try {
            val data = mapOf(
                "id" to challenge.id,
                "habitId" to challenge.habitId,
                "title" to challenge.title,
                "description" to challenge.description,
                "createdBy" to challenge.createdBy,
                "participants" to challenge.participants,
                "acceptedParticipants" to challenge.acceptedParticipants,
                "startDate" to challenge.startDate.toString(),
                "endDate" to challenge.endDate.toString(),
                "rules" to mapOf(
                    "type" to challenge.rules.type.name,
                    "targetValue" to challenge.rules.targetValue,
                    "allowMissedDays" to challenge.rules.allowMissedDays,
                    "requireConsecutive" to challenge.rules.requireConsecutive
                ),
                "reward" to challenge.reward,
                "status" to challenge.status.name,
                "winnerId" to challenge.winnerId,
                "createdAt" to challenge.createdAt.toString(),
                "updatedAt" to challenge.updatedAt.toString(),
                "updatedAtMillis" to now
            )
            firestore.collection("challenges").document(challenge.id).set(data).await()
            Log.d(TAG, "Challenge synced to Firestore: ${challenge.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync challenge to Firestore, using SyncManager fallback", e)
            // Fallback: используем очередь синхронизации
            val json = challenge.toFirestoreJson()
            syncManager.enqueueUpsert(
                entityType = "challenge",
                entityId = challenge.id,
                payloadJson = json,
                updatedAt = now
            )
        }
    }
    
    override suspend fun updateChallenge(challenge: Challenge) {
        val now = System.currentTimeMillis()
        challengeDao.updateChallenge(challenge.challengeToEntity(updatedAtMillis = now))
        
        // Прямая синхронизация в Firestore
        try {
            val data = mapOf(
                "id" to challenge.id,
                "habitId" to challenge.habitId,
                "title" to challenge.title,
                "description" to challenge.description,
                "createdBy" to challenge.createdBy,
                "participants" to challenge.participants,
                "acceptedParticipants" to challenge.acceptedParticipants,
                "startDate" to challenge.startDate.toString(),
                "endDate" to challenge.endDate.toString(),
                "rules" to mapOf(
                    "type" to challenge.rules.type.name,
                    "targetValue" to challenge.rules.targetValue,
                    "allowMissedDays" to challenge.rules.allowMissedDays,
                    "requireConsecutive" to challenge.rules.requireConsecutive
                ),
                "reward" to challenge.reward,
                "status" to challenge.status.name,
                "winnerId" to challenge.winnerId,
                "createdAt" to challenge.createdAt.toString(),
                "updatedAt" to challenge.updatedAt.toString(),
                "updatedAtMillis" to now
            )
            firestore.collection("challenges").document(challenge.id).set(data).await()
            Log.d(TAG, "Challenge updated in Firestore: ${challenge.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update challenge in Firestore, using SyncManager fallback", e)
            syncManager.enqueueUpsert(
                entityType = "challenge",
                entityId = challenge.id,
                payloadJson = challenge.toFirestoreJson(),
                updatedAt = now
            )
        }
    }
    
    override suspend fun deleteChallenge(challengeId: String) {
        val now = System.currentTimeMillis()
        challengeDao.deleteChallenge(challengeId)
        
        // Прямое удаление из Firestore
        try {
            firestore.collection("challenges").document(challengeId).delete().await()
            Log.d(TAG, "Challenge deleted from Firestore: $challengeId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete challenge from Firestore, using SyncManager fallback", e)
            syncManager.enqueueDelete(
                entityType = "challenge",
                entityId = challengeId,
                updatedAt = now
            )
        }
    }
    
    override suspend fun getChallengeById(challengeId: String): Challenge? {
        return challengeDao.getChallengeById(challengeId)?.challengeToDomain()
    }
    
    override fun getAllChallenges(): Flow<List<Challenge>> {
        return challengeDao.getAllChallenges().map { entities ->
            entities.map { it.challengeToDomain() }
        }
    }
    
    override fun getChallengesForUser(userId: String): Flow<List<Challenge>> {
        return challengeDao.getUserChallenges(userId).map { entities ->
            entities.map { it.challengeToDomain() }
        }
    }
    
    override fun getActiveChallenges(): Flow<List<Challenge>> {
        return challengeDao.getChallengesByStatus("ACTIVE").map { entities ->
            entities.map { it.challengeToDomain() }
        }
    }
    
    override fun getCompletedChallenges(): Flow<List<Challenge>> {
        return challengeDao.getChallengesByStatuses(listOf("COMPLETED", "EXPIRED")).map { entities ->
            entities.map { it.challengeToDomain() }
        }
    }
    
    override suspend fun getChallengesForHabit(habitId: String): List<Challenge> {
        return challengeDao.getChallengesForHabit(habitId).first().map { it.challengeToDomain() }
    }
    
    override suspend fun joinChallenge(challengeId: String, userId: String) {
        val challenge = getChallengeById(challengeId) ?: return
        val participants = challenge.participants.toMutableList()
        if (!participants.contains(userId)) {
            participants.add(userId)
            val now = System.currentTimeMillis()
            challengeDao.updateChallengeParticipants(challengeId, participants)
            val updatedChallenge = challenge.copy(participants = participants)
            syncManager.enqueueUpsert(
                entityType = "challenge",
                entityId = challengeId,
                payloadJson = updatedChallenge.toFirestoreJson(),
                updatedAt = now
            )
        }
    }
    
    override suspend fun leaveChallenge(challengeId: String, userId: String) {
        val challenge = getChallengeById(challengeId) ?: return
        
        if (!challenge.participants.contains(userId)) return
        
        val updatedParticipants = challenge.participants - userId
        val updatedAcceptedParticipants = challenge.acceptedParticipants - userId
        val now = System.currentTimeMillis()
        
        // Обновляем локальную базу (participants, acceptedParticipants и updatedAtMillis)
        challengeDao.updateChallengeParticipantsAndAccepted(challengeId, updatedParticipants, updatedAcceptedParticipants, now)
        
        // Обновляем Firestore напрямую для немедленной синхронизации
        try {
            firestore.collection("challenges")
                .document(challengeId)
                .update(
                    mapOf(
                        "participants" to updatedParticipants,
                        "acceptedParticipants" to updatedAcceptedParticipants,
                        "updatedAt" to kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                        "updatedAtMillis" to now
                    )
                )
                .await()
            Log.d(TAG, "User $userId left challenge $challengeId successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Firestore on leave challenge: ${e.message}", e)
            // Fallback to sync manager
            val updatedChallenge = challenge.copy(
                participants = updatedParticipants,
                acceptedParticipants = updatedAcceptedParticipants
            )
            syncManager.enqueueUpsert(
                entityType = "challenge",
                entityId = challengeId,
                payloadJson = updatedChallenge.toFirestoreJson(),
                updatedAt = now
            )
        }
    }
    
    override suspend fun getChallengeParticipants(challengeId: String): List<String> {
        return getChallengeById(challengeId)?.participants ?: emptyList()
    }
    
    override suspend fun updateChallengeStatus(challengeId: String, status: String) {
        val challenge = getChallengeById(challengeId) ?: return
        val now = System.currentTimeMillis()
        challengeDao.updateChallengeStatus(challengeId, status)
        val updatedChallenge = challenge.copy(
            status = com.habittracker.domain.model.ChallengeStatus.valueOf(status)
        )
        syncManager.enqueueUpsert(
            entityType = "challenge",
            entityId = challengeId,
            payloadJson = updatedChallenge.toFirestoreJson(),
            updatedAt = now
        )
    }
    
    override suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>> {
        val challenge = getChallengeById(challengeId) ?: return emptyList()
        return challenge.participants.map { participantId ->
            val score = habitEntryDao.getCompletedEntriesCountForUserAndHabit(challenge.habitId, participantId)
            Pair(participantId, score)
        }.sortedByDescending { it.second }
    }
    
    override suspend fun endChallenge(challengeId: String, winnerId: String?) {
        val challenge = getChallengeById(challengeId) ?: return
        val now = System.currentTimeMillis()
        challengeDao.updateChallengeWinner(challengeId, winnerId)
        val updatedChallenge = challenge.copy(
            status = com.habittracker.domain.model.ChallengeStatus.COMPLETED,
            winnerId = winnerId
        )
        syncManager.enqueueUpsert(
            entityType = "challenge",
            entityId = challengeId,
            payloadJson = updatedChallenge.toFirestoreJson(),
            updatedAt = now
        )
    }
    
    private fun Challenge.toFirestoreJson(): String {
        return buildJsonObject {
            put("id", JsonPrimitive(id))
            put("habitId", JsonPrimitive(habitId))
            put("title", JsonPrimitive(title))
            put("description", JsonPrimitive(description))
            put("createdBy", JsonPrimitive(createdBy))
            put("participants", buildJsonArray {
                participants.forEach { add(JsonPrimitive(it)) }
            })
            put("acceptedParticipants", buildJsonArray {
                acceptedParticipants.forEach { add(JsonPrimitive(it)) }
            })
            put("startDate", JsonPrimitive(startDate.toString()))
            put("endDate", JsonPrimitive(endDate.toString()))
            put("rules", buildJsonObject {
                put("type", JsonPrimitive(rules.type.name))
                put("targetValue", JsonPrimitive(rules.targetValue))
                put("allowMissedDays", JsonPrimitive(rules.allowMissedDays))
                put("requireConsecutive", JsonPrimitive(rules.requireConsecutive))
            })
            put("reward", JsonPrimitive(reward))
            put("status", JsonPrimitive(status.name))
            put("winnerId", if (winnerId != null) JsonPrimitive(winnerId) else kotlinx.serialization.json.JsonNull)
            put("createdAt", JsonPrimitive(createdAt.toString()))
            put("updatedAt", JsonPrimitive(updatedAt.toString()))
        }.toString()
    }

    override fun observeChallengesForUser(userId: String): Flow<List<Challenge>> = callbackFlow {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Слушаем локальную базу Room для реактивного обновления UI
        val roomJob = scope.launch {
            challengeDao.getUserChallenges(userId).collect { entities ->
                val challenges = entities.map { it.challengeToDomain() }
                trySend(challenges)
            }
        }

        // Слушатель для челленджей, где пользователь — участник
        val registration = firestore.collection("challenges")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Challenges listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                Log.d(TAG, "Challenges listener: userId=$userId, docs=${docs.size}")

                scope.launch {
                    // Получаем ID челленджей из Firestore, где пользователь участник
                    val remoteParticipantChallengeIds = docs.mapNotNull { it.id }.toSet()
                    
                    // Получаем локальные челленджи пользователя (где он участник, но не создатель)
                    val localChallenges = challengeDao.getUserChallengesSync(userId)
                    
                    // Удаляем из локальной базы челленджи, где пользователь больше не участник
                    // (кроме тех, где он создатель - они обрабатываются отдельным listener)
                    for (localChallenge in localChallenges) {
                        if (localChallenge.createdBy != userId && 
                            !remoteParticipantChallengeIds.contains(localChallenge.id)) {
                            Log.d(TAG, "Removing challenge ${localChallenge.id} - user no longer participant")
                            challengeDao.deleteChallenge(localChallenge.id)
                        }
                    }
                    
                    for (doc in docs) {
                        try {
                            val remoteChallenge = doc.toChallengeEntity()
                            if (remoteChallenge != null) {
                                // LWW: проверяем updatedAtMillis
                                val local = challengeDao.getChallengeById(remoteChallenge.id)
                                if (local == null || remoteChallenge.updatedAtMillis >= local.updatedAtMillis) {
                                    challengeDao.insertChallenge(remoteChallenge)
                                    Log.d(TAG, "Merged challenge from Firestore: ${remoteChallenge.id}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing challenge doc: ${doc.id}", e)
                        }
                    }
                }
            }

        // Также слушаем челленджи, созданные пользователем
        val createdByRegistration = firestore.collection("challenges")
            .whereEqualTo("createdBy", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "CreatedBy challenges listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                Log.d(TAG, "CreatedBy challenges listener: userId=$userId, docs=${docs.size}")

                scope.launch {
                    for (doc in docs) {
                        try {
                            val remoteChallenge = doc.toChallengeEntity()
                            if (remoteChallenge != null) {
                                val local = challengeDao.getChallengeById(remoteChallenge.id)
                                if (local == null || remoteChallenge.updatedAtMillis >= local.updatedAtMillis) {
                                    challengeDao.insertChallenge(remoteChallenge)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing createdBy challenge doc: ${doc.id}", e)
                        }
                    }
                }
            }

        awaitClose {
            registration.remove()
            createdByRegistration.remove()
            roomJob.cancel()
            scope.cancel()
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChallengeEntity(): ChallengeEntity? {
        return try {
            val data = this.data ?: return null
            val rulesMap = data["rules"] as? Map<*, *>
            
            ChallengeEntity(
                id = this.id,
                habitId = data["habitId"] as? String ?: return null,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                createdBy = data["createdBy"] as? String ?: return null,
                participants = (data["participants"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                acceptedParticipants = (data["acceptedParticipants"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                startDate = LocalDate.parse(data["startDate"] as? String ?: return null),
                endDate = LocalDate.parse(data["endDate"] as? String ?: return null),
                rules = Json.encodeToString(
                    ChallengeRules(
                        type = ChallengeType.valueOf(rulesMap?.get("type") as? String ?: "COMPLETION_RATE"),
                        targetValue = (rulesMap?.get("targetValue") as? Number)?.toInt() ?: 100,
                        allowMissedDays = (rulesMap?.get("allowMissedDays") as? Number)?.toInt() ?: 0,
                        requireConsecutive = rulesMap?.get("requireConsecutive") as? Boolean ?: false
                    )
                ),
                reward = data["reward"] as? String ?: "",
                status = data["status"] as? String ?: "PENDING",
                winnerId = data["winnerId"] as? String,
                createdAt = LocalDate.parse(data["createdAt"] as? String ?: data["startDate"] as String),
                updatedAt = LocalDate.parse(data["updatedAt"] as? String ?: data["startDate"] as String),
                updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Firestore doc to ChallengeEntity: ${this.id}", e)
            null
        }
    }

    override suspend fun getCompletedDaysForUserInChallenge(
        challengeId: String,
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        return habitEntryDao.getCompletedEntriesCountForUserInRange(
            habitId = habitId,
            userId = userId,
            startDate = startDate.toString(),
            endDate = endDate.toString()
        )
    }

    override suspend fun getCurrentStreakForUserInChallenge(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        val entries = habitEntryDao.getEntriesForUserInRange(
            habitId = habitId,
            userId = userId,
            startDate = startDate.toString(),
            endDate = endDate.toString()
        )
        
        if (entries.isEmpty()) return 0
        
        // Sort by date descending and count consecutive completed days
        val sortedEntries = entries.sortedByDescending { it.date }
        var streak = 0
        
        for (entry in sortedEntries) {
            if (entry.completed) {
                streak++
            } else {
                break
            }
        }
        
        return streak
    }

    override suspend fun getCompletedDaysFromFirestore(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        return try {
            val snapshot = firestore.collection("habit_entries")
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("userId", userId)
                .whereEqualTo("completed", true)
                .whereGreaterThanOrEqualTo("date", startDate.toString())
                .whereLessThanOrEqualTo("date", endDate.toString())
                .get()
                .await()
            
            snapshot.documents.size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting completed days from Firestore: ${e.message}", e)
            0
        }
    }

    override suspend fun getCurrentStreakFromFirestore(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        return try {
            val snapshot = firestore.collection("habit_entries")
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("userId", userId)
                .whereEqualTo("completed", true)
                .whereGreaterThanOrEqualTo("date", startDate.toString())
                .whereLessThanOrEqualTo("date", endDate.toString())
                .get()
                .await()

            val completedDates = snapshot.documents.mapNotNull { doc ->
                (doc.getString("date") ?: doc.get("date") as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            }.toSet()

            var streak = 0
            var current = endDate
            while (current >= startDate) {
                if (completedDates.contains(current)) {
                    streak++
                    current = current.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
                } else {
                    break
                }
            }
            streak
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current streak from Firestore: ${e.message}", e)
            0
        }
    }

    override suspend fun acceptChallengeInvitation(challengeId: String, userId: String) {
        val challenge = getChallengeById(challengeId) ?: return
        
        // Проверяем, что пользователь приглашён
        if (!challenge.participants.contains(userId)) return
        
        // Проверяем, что ещё не принял
        if (challenge.acceptedParticipants.contains(userId)) return
        
        val updatedChallenge = challenge.copy(
            acceptedParticipants = challenge.acceptedParticipants + userId
        )
        updateChallenge(updatedChallenge)
    }

    override suspend fun declineChallengeInvitation(challengeId: String, userId: String) {
        val challenge = getChallengeById(challengeId) ?: return
        
        // Удаляем из приглашённых и принявших
        val updatedChallenge = challenge.copy(
            participants = challenge.participants - userId,
            acceptedParticipants = challenge.acceptedParticipants - userId
        )
        updateChallenge(updatedChallenge)
    }

    override suspend fun cancelChallenge(challengeId: String) {
        val challenge = getChallengeById(challengeId) ?: return
        
        val updatedChallenge = challenge.copy(
            status = com.habittracker.domain.model.ChallengeStatus.CANCELLED
        )
        updateChallenge(updatedChallenge)
    }

    override suspend fun addParticipantsToChallenge(challengeId: String, participantIds: List<String>) {
        val challenge = getChallengeById(challengeId) ?: return
        
        // Добавляем новых участников (без дубликатов)
        val updatedParticipants = (challenge.participants + participantIds).distinct()
        
        val updatedChallenge = challenge.copy(
            participants = updatedParticipants
        )
        updateChallenge(updatedChallenge)
    }

    companion object {
        private const val TAG = "ChallengeRepository"
    }
}
