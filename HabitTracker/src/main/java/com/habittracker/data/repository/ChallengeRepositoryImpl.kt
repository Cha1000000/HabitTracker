package com.habittracker.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.habittracker.data.local.dao.ChallengeDao
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.entity.ChallengeEntity
import com.habittracker.data.model.FirebaseHabitEntry
import com.habittracker.data.model.FirebaseHabitEntry.Companion.toDomain
import com.habittracker.data.sync.RemoteDataSync
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.data.sync.keepRestarting
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.local.mappers.toDomain as challengeToDomain
import com.habittracker.data.local.mappers.toEntity as challengeToEntity

@Singleton
class ChallengeRepositoryImpl @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val habitEntryDao: HabitEntryDao,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val firestore: FirebaseFirestore
) : ChallengeRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        repoScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    override suspend fun insertChallenge(challenge: Challenge) {
        val now = System.currentTimeMillis()
        Timber.tag(TAG).d("insertChallenge: id=${challenge.id}, participants=${challenge.participants}")
        challengeDao.insertChallenge(challenge.challengeToEntity(updatedAtMillis = now))
        
        // Use Outbox for reliable sync
        val json = SyncPayloads.challengeToJson(challenge, now)
        syncManager.enqueueUpsert(
            entityType = "challenge",
            entityId = challenge.id,
            payloadJson = json,
            updatedAt = now
        )

        kickSync()
    }
    
    override suspend fun updateChallenge(challenge: Challenge) {
        val now = System.currentTimeMillis()
        challengeDao.updateChallenge(challenge.challengeToEntity(updatedAtMillis = now))
        
        // Use Outbox for reliable sync
        val json = SyncPayloads.challengeToJson(challenge, now)
        syncManager.enqueueUpsert(
            entityType = "challenge",
            entityId = challenge.id,
            payloadJson = json,
            updatedAt = now
        )
        kickSync()
    }
    
    override suspend fun deleteChallenge(challengeId: String) {
        val now = System.currentTimeMillis()
        challengeDao.softDeleteChallengeById(challengeId, deletedAtMillis = now, updatedAtMillis = now)
        
        // Use Outbox for reliable sync
        syncManager.enqueueDelete(
            entityType = "challenge",
            entityId = challengeId,
            updatedAt = now
        )

        kickSync()
    }
    
    override suspend fun getChallengeById(challengeId: String): Challenge? {
        return challengeDao.getActiveChallengeById(challengeId)?.challengeToDomain()
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
                payloadJson = SyncPayloads.challengeToJson(updatedChallenge, now),
                updatedAt = now
            )

            kickSync()
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
        
        val updatedChallenge = challenge.copy(
            participants = updatedParticipants,
            acceptedParticipants = updatedAcceptedParticipants
        )
        syncManager.enqueueUpsert(
            entityType = "challenge",
            entityId = challengeId,
            payloadJson = SyncPayloads.challengeToJson(updatedChallenge, now),
            updatedAt = now
        )

        kickSync()
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
            payloadJson = SyncPayloads.challengeToJson(updatedChallenge, now),
            updatedAt = now
        )
        kickSync()
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
            payloadJson = SyncPayloads.challengeToJson(updatedChallenge, now),
            updatedAt = now
        )
        kickSync()
    }

    override fun observeChallengesForUser(userId: String): Flow<List<Challenge>> = callbackFlow {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Слушаем локальную базу Room для реактивного обновления UI
        scope.launch {
            challengeDao.getUserChallenges(userId).collect { entities ->
                val challenges = entities.map { it.challengeToDomain() }
                trySend(challenges)
            }
        }

        // Челленджи, где пользователь — участник. Слушатель после ошибки замолкает навсегда,
        // поэтому поднимаем его заново, а экран продолжает показывать Room
        scope.launch {
            keepRestarting(TAG, "participant challenges") {
                challengeSnapshots(
                    firestore.collection("challenges").whereArrayContains("participants", userId)
                ).collect { snapshot ->
                    val docs = snapshot.documents
                    Timber.tag(TAG).d("Challenges listener: userId=$userId, docs=${docs.size}, fromCache=${snapshot.metadata.isFromCache}")
                    // Снимок из кэша бывает неполным (холодный старт офлайн) — по нему не удаляем
                    if (!snapshot.metadata.isFromCache) {
                        dropLeftChallenges(userId, remoteIds = docs.map { it.id }.toSet())
                    }
                    mergeRemoteChallenges(docs)
                }
            }
        }

        // Также слушаем челленджи, созданные пользователем
        scope.launch {
            keepRestarting(TAG, "created challenges") {
                challengeSnapshots(
                    firestore.collection("challenges").whereEqualTo("createdBy", userId)
                ).collect { snapshot ->
                    Timber.tag(TAG).d("CreatedBy challenges listener: userId=$userId, docs=${snapshot.size()}")
                    mergeRemoteChallenges(snapshot.documents)
                }
            }
        }

        awaitClose { scope.cancel() }
    }

    private fun challengeSnapshots(query: Query): Flow<QuerySnapshot> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot?.let { trySend(it) }
        }
        awaitClose { registration.remove() }
    }.conflate()

    /** Удаляем из Room челленджи, где пользователь больше не участник (свои — не трогаем). */
    private suspend fun dropLeftChallenges(userId: String, remoteIds: Set<String>) {
        for (localChallenge in challengeDao.getUserChallengesSync(userId)) {
            if (localChallenge.createdBy == userId || localChallenge.id in remoteIds) continue
            if (hasPendingChallengeOps(localChallenge.id)) continue
            Timber.tag(TAG).d("Removing challenge ${localChallenge.id} - user no longer participant")
            val now = System.currentTimeMillis()
            challengeDao.softDeleteChallengeById(localChallenge.id, deletedAtMillis = now, updatedAtMillis = now)
        }
    }

    /** LWW по updatedAtMillis; челленджи с неотправленными правками не трогаем. */
    private suspend fun mergeRemoteChallenges(docs: List<DocumentSnapshot>) {
        for (doc in docs) {
            try {
                val remoteChallenge = doc.toChallengeEntity() ?: continue
                if (hasPendingChallengeOps(remoteChallenge.id)) continue
                val local = challengeDao.getChallengeById(remoteChallenge.id)
                if (local == null || remoteChallenge.updatedAtMillis >= local.updatedAtMillis) {
                    challengeDao.insertChallenge(remoteChallenge)
                    Timber.tag(TAG).d("Merged challenge from Firestore: ${remoteChallenge.id}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error processing challenge doc: ${doc.id}")
            }
        }
    }

    private suspend fun hasPendingChallengeOps(challengeId: String): Boolean = try {
        syncManager.hasPendingOperations("challenge", challengeId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Pending check failed for challenge=$challengeId")
        false
    }

    /** Удалённые отметки остаются в облаке надгробиями — в счёт челленджа их не берём. */
    private fun List<DocumentSnapshot>.liveEntries(): List<DocumentSnapshot> = filter { doc ->
        val updatedAt = try {
            doc.toObject(FirebaseHabitEntry::class.java)?.toDomain()?.updatedAt
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "skip malformed habit entry ${doc.id}")
            null
        } ?: return@filter false
        !RemoteDataSync.isTombstone(
            deletedAtMillis = doc.getLong("deletedAtMillis"),
            updatedAtMillis = doc.getLong("updatedAtMillis"),
            deletedOn = doc.getString("deletedOn")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            updatedAt = updatedAt
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChallengeEntity(): ChallengeEntity? {
        return try {
            val data = this.data ?: return null
            val rulesMap = data["rules"] as? Map<*, *>
            val deletedAtMillis = (data["deletedAtMillis"] as? Number)?.toLong()

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
                        requireConsecutive = rulesMap?.get("requireConsecutive") as? Boolean ?: false,
                        allowBackfillCompletions = rulesMap?.get("allowBackfillCompletions") as? Boolean ?: false
                    )
                ),
                reward = data["reward"] as? String ?: "",
                status = data["status"] as? String ?: "PENDING",
                winnerId = data["winnerId"] as? String,
                createdAt = LocalDate.parse(data["createdAt"] as? String ?: data["startDate"] as String),
                updatedAt = LocalDate.parse(data["updatedAt"] as? String ?: data["startDate"] as String),
                updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                deletedAtMillis = deletedAtMillis
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error converting Firestore doc to ChallengeEntity: ${this.id}")
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
            
            snapshot.documents.liveEntries().size
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Timber.tag(TAG).d("getCompletedDaysFromFirestore: device is offline")
            } else {
                Timber.tag(TAG).e(e, "Error getting completed days from Firestore: ${e.code}")
            }
            0
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting completed days from Firestore: ${e.message}")
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

            val completedDates = snapshot.documents.liveEntries().mapNotNull { doc ->
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
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Timber.tag(TAG).d("getCurrentStreakFromFirestore: device is offline")
            } else {
                Timber.tag(TAG).e(e, "Error getting current streak from Firestore: ${e.code}")
            }
            0
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting current streak from Firestore: ${e.message}")
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

    override suspend fun getTotalValueForUserInChallenge(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        return try {
            habitEntryDao.getTotalValueForPeriod(habitId, userId, startDate.toString(), endDate.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting total value for user in challenge: ${e.message}")
            0
        }
    }

    override suspend fun getTotalValueFromFirestore(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        return try {
            val snapshot = firestore.collection("habit_entries")
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("date", startDate.toString())
                .whereLessThanOrEqualTo("date", endDate.toString())
                .get()
                .await()
            
            snapshot.documents.liveEntries().sumOf { doc ->
                (doc.getLong("value") ?: 0L).toInt()
            }
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Timber.tag(TAG).d("getTotalValueFromFirestore: device is offline")
            } else {
                Timber.tag(TAG).e(e, "Error getting total value from Firestore: ${e.code}")
            }
            0
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting total value from Firestore: ${e.message}")
            0
        }
    }

    companion object {
        private const val TAG = "ChallengeRepository"
    }
}
