package com.habittracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.sync.RemoteDataSync
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.local.seed.AchievementSeeder
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.ReminderRepository
import com.habittracker.domain.scheduler.DailyReminderScheduler
import com.habittracker.presentation.widget.TodayHabitsWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/** Участники из поля-списка документа, кроме [userId], в исходном порядке и без дублей. */
internal fun remainingParticipants(field: Any?, userId: String): List<String> =
    (field as? List<*>).orEmpty().filterIsInstance<String>().filter { it != userId }.distinct()

@Singleton
class AccountRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val database: HabitTrackerDatabase,
    private val remoteDataSync: RemoteDataSync,
    private val syncManager: SyncManager,
    private val achievementSeeder: AchievementSeeder,
    private val reminderRepository: ReminderRepository,
    private val dailyReminderScheduler: DailyReminderScheduler
) : AccountRepository {

    override suspend fun pauseSync() {
        // Зависший дренаж дописал бы данные (например, профиль) уже после удаления
        if (!syncManager.pauseAndAwait()) throw IOException("Pending sync did not finish in time")
    }

    override fun resumeSync() = syncManager.resume()

    override suspend fun deleteRemoteUserData(userId: String) = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("deleteRemoteUserData: start")
        leaveOrDeleteHabits(userId)
        leaveOrDeleteChallenges(userId)
        deleteWhere(HABIT_ENTRIES, "userId", userId)
        deleteStorageFolder(storage.reference.child("proofs/$userId"))
        deleteStorageFolder(storage.reference.child("user_avatars/$userId"))
        deleteFriendships(userId)
        deleteWhere(FRIEND_REQUESTS, "fromUserId", userId)
        deleteWhere(FRIEND_REQUESTS, "toUserId", userId)
        deleteWhere(HABIT_INVITATIONS, "fromUserId", userId)
        deleteWhere(HABIT_INVITATIONS, "toUserId", userId)
        deleteWhere(NUDGES, "senderId", userId)
        deleteWhere(NUDGES, "receiverId", userId)
        deleteWhere(GROUP_HABIT_NOTIFICATIONS, "userId", userId)
        deleteWhere(GROUP_HABIT_NOTIFICATIONS, "participantId", userId)
        deleteWhere(USER_ACHIEVEMENTS, "userId", userId)
        withNetworkTimeout { firestore.collection(USERS).document(userId).delete().await() }
        Timber.tag(TAG).d("deleteRemoteUserData: done")
    }

    /**
     * Свои групповые привычки с другими участниками передаём следующему участнику, из чужих
     * выходим, а привычки без других участников удаляем.
     */
    private suspend fun leaveOrDeleteHabits(userId: String) {
        val habits = collectDocuments(
            firestore.collection(HABITS).whereArrayContains("participants", userId),
            // Создатель обычно есть в participants, но старые документы могли его не содержать
            firestore.collection(HABITS).whereEqualTo("createdBy", userId)
        )
        val ops = mutableListOf<BatchOp>()
        habits.forEach { (ref, data) ->
            val others = remainingParticipants(data["participants"], userId)
            ops += if (others.isEmpty()) {
                BatchOp.Delete(ref)
            } else {
                BatchOp.Update(ref, leaveUpdate(userId, data["createdBy"] == userId, others.first(), "participants"))
            }
            ops += BatchOp.Delete(
                ref.collection("participants").document(userId).collection("meta").document("settings")
            )
        }
        commit(ops)
        Timber.tag(TAG).d("habits processed: ${habits.size}")
    }

    /** Те же правила для челленджей; новым владельцем предпочтительно становится принявший участник. */
    private suspend fun leaveOrDeleteChallenges(userId: String) {
        val challenges = collectDocuments(
            firestore.collection(CHALLENGES).whereArrayContains("participants", userId),
            firestore.collection(CHALLENGES).whereEqualTo("createdBy", userId)
        )
        val ops = challenges.map { (ref, data) ->
            val others = remainingParticipants(data["participants"], userId)
            if (others.isEmpty()) {
                BatchOp.Delete(ref)
            } else {
                val accepted = remainingParticipants(data["acceptedParticipants"], userId)
                val newOwner = accepted.firstOrNull() ?: others.first()
                BatchOp.Update(
                    ref,
                    leaveUpdate(userId, data["createdBy"] == userId, newOwner, "participants", "acceptedParticipants")
                )
            }
        }
        commit(ops)
        Timber.tag(TAG).d("challenges processed: ${challenges.size}")
    }

    private fun leaveUpdate(
        userId: String,
        isOwner: Boolean,
        newOwner: String,
        vararg arrayFields: String
    ): Map<String, Any> = buildMap {
        arrayFields.forEach { put(it, FieldValue.arrayRemove(userId)) }
        if (isOwner) put("createdBy", newOwner)
        // Другие клиенты сравнивают метки времени (LWW) — без них изменение могут проигнорировать
        put("updatedAtMillis", Clock.System.now().toEpochMilliseconds())
        put("updatedAt", Clock.System.todayIn(TimeZone.currentSystemDefault()).toString())
    }

    /**
     * Удаляем дружбы. Счётчик друзей у второй стороны не трогаем: клиент друга пересчитывает его
     * сам, когда его слушатель увидит, что дружбы больше нет.
     */
    private suspend fun deleteFriendships(userId: String) {
        val friendships = collectDocuments(
            firestore.collection(FRIENDSHIPS).whereEqualTo("userId1", userId),
            firestore.collection(FRIENDSHIPS).whereEqualTo("userId2", userId)
        )
        commit(friendships.map { (ref, _) -> BatchOp.Delete(ref) })
    }

    private suspend fun deleteWhere(collection: String, field: String, userId: String) {
        val docs = collectDocuments(firestore.collection(collection).whereEqualTo(field, userId))
        commit(docs.map { (ref, _) -> BatchOp.Delete(ref) })
    }

    private suspend fun collectDocuments(vararg queries: Query): List<Pair<DocumentReference, Map<String, Any>>> =
        queries
            // Только сервер: оффлайн-кэш вернул бы неполный список и удаление «успело» бы не всё
            .flatMap { query -> withNetworkTimeout { query.get(Source.SERVER).await() }.documents }
            .distinctBy { it.reference.path }
            .map { it.reference to (it.data ?: emptyMap()) }

    private suspend fun deleteStorageFolder(folder: StorageReference) {
        try {
            val listing = withNetworkTimeout { folder.listAll().await() }
            listing.items.forEach { item ->
                try {
                    withNetworkTimeout { item.delete().await() }
                } catch (e: StorageException) {
                    // Файл уже удалён прошлой попыткой — это нормально при повторе
                    if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
                }
            }
            listing.prefixes.forEach { deleteStorageFolder(it) }
        } catch (e: StorageException) {
            // Правила Storage сейчас запрещают клиенту любой доступ (даже загрузку). Удалить файлы
            // отсюда невозможно, а блокировать из-за этого удаление аккаунта нельзя. Пишем ошибкой,
            // чтобы оставшиеся файлы были видны в Crashlytics.
            if (e.errorCode != StorageException.ERROR_NOT_AUTHORIZED) throw e
            Timber.tag(TAG).e(e, "Storage denied access to ${folder.path}, files are left in the bucket")
        }
    }

    private suspend fun commit(ops: List<BatchOp>) {
        ops.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { op ->
                when (op) {
                    is BatchOp.Delete -> batch.delete(op.ref)
                    is BatchOp.Update -> batch.update(op.ref, op.fields)
                }
            }
            withNetworkTimeout { batch.commit().await() }
        }
    }

    /**
     * Оффлайн запись в Firestore ждёт сеть бесконечно — обрываем по таймауту. Таймаут превращаем
     * в [IOException]: [kotlinx.coroutines.TimeoutCancellationException] наследует
     * CancellationException, и выше его приняли бы за отмену, а не за ошибку.
     */
    private suspend fun <T> withNetworkTimeout(block: suspend () -> T): T = try {
        withTimeout(NETWORK_TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Network operation timed out", e)
    }

    private sealed interface BatchOp {
        data class Delete(val ref: DocumentReference) : BatchOp
        data class Update(val ref: DocumentReference, val fields: Map<String, Any>) : BatchOp
    }

    override suspend fun wipeLocalData() = withContext(Dispatchers.IO) {
        // Аккаунт в облаке к этому моменту уже удалён, поэтому идём до конца, а сбои только логируем
        wipeStep("sign out") { firebaseAuth.signOut() }
        wipeStep("fcm token") {
            // Токен без профиля никому не нужен: пуши на него просто перестанут доходить
            withNetworkTimeout { firebaseMessaging.deleteToken().await() }
        }
        wipeStep("reminders") {
            reminderRepository.cancelAllReminders()
            dailyReminderScheduler.cancel()
        }
        wipeStep("database") {
            // Синк к этому моменту ещё может дописывать снимок: ждём его и не пускаем новые записи
            remoteDataSync.withWritesBlocked { database.clearAllTables() }
            // Справочник достижений общий для всех — сразу засеваем заново
            achievementSeeder.forceReseed()
        }
        wipeStep("datastore") { context.dataStore.edit { it.clear() } }
        wipeStep("shared prefs") {
            USER_PREFS.forEach { name ->
                // clear() вместо deleteSharedPreferences: синглтоны держат открытые экземпляры
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        wipeStep("files") { File(context.filesDir, "proofs").deleteRecursively() }
        wipeStep("widget") { TodayHabitsWidget.clearTodayHabits(context) }
        Timber.tag(TAG).d("wipeLocalData: done")
    }

    private suspend fun wipeStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "wipeLocalData: step '$name' failed")
        }
    }

    private companion object {
        const val TAG = "AccountRepository"
        const val BATCH_LIMIT = 450
        const val NETWORK_TIMEOUT_MS = 30_000L

        const val USERS = "users"
        const val HABITS = "habits"
        const val HABIT_ENTRIES = "habit_entries"
        const val CHALLENGES = "challenges"
        const val FRIENDSHIPS = "friendships"
        const val FRIEND_REQUESTS = "friend_requests"
        const val HABIT_INVITATIONS = "habit_invitations"
        const val NUDGES = "nudges"
        const val GROUP_HABIT_NOTIFICATIONS = "group_habit_notifications"
        const val USER_ACHIEVEMENTS = "user_achievements"

        val USER_PREFS = listOf(
            "fcm_manager_prefs",
            "notification_settings",
            "today_habits_widget_prefs",
            RemoteDataSync.PREFS_NAME
        )
    }
}
