package com.habittracker.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.habittracker.data.local.dao.HabitDao
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.dao.HabitParticipantSettingsDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.data.model.FirebaseHabitEntry
import com.habittracker.data.model.FirebaseHabitEntry.Companion.toDomain
import com.habittracker.data.model.FirebaseParticipantSettings
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.util.HabitEntryIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Единственное место, где данные вошедшего пользователя приходят из Firestore в Room.
 * Экраны, виджет и воркеры читают только Room.
 */
@Singleton
class RemoteDataSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firestoreRepository: FirestoreRepository,
    private val habitDao: HabitDao,
    private val habitEntryDao: HabitEntryDao,
    private val participantSettingsDao: HabitParticipantSettingsDao,
    private val syncManager: SyncManager
) {

    /** Все записи синка в Room идут по одной; [withWritesBlocked] не пускает их во время очистки. */
    private val writeMutex = Mutex()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Держит слушатели, пока корутину не отменят:
     * - привычки пользователя;
     * - по каждой привычке — её отметки (свои для личной, всех участников для групповой)
     *   и личные настройки участника.
     *
     * Упавший слушатель поднимается заново, остальные при этом работают.
     *
     * Полную историю своих отметок, включая удалённые привычки (их учитывает статистика за год),
     * догружает один раз на устройство.
     */
    suspend fun run(userId: String): Unit = coroutineScope {
        Timber.tag(TAG).d("start for user=$userId")
        launch { keepRestarting(TAG, "habits") { observeHabits(userId) } }
        launch { loadOwnHistoryOnce(userId) }

        // Снятый и заново поднятый слушатель перечитывает все свои документы, поэтому при изменении
        // списка привычек трогаем только добавленные и убранные
        val running = mutableMapOf<HabitKey, Job>()
        habitDao.getUserHabits(userId)
            .map { entities -> entities.map { it.toDomain() }.map { HabitKey(it.id, it.isGroupHabit()) }.toSet() }
            .distinctUntilChanged()
            .collect { keys ->
                (running.keys - keys).forEach { running.remove(it)?.cancel() }
                (keys - running.keys).forEach { key -> running[key] = launch { observeHabitData(key, userId) } }
                Timber.tag(TAG).d("habits: ${keys.size}, group: ${keys.count { it.isGroup }}")
            }
    }

    /** Ждёт текущую запись синка и не пускает новые, пока выполняется [block] (очистка Room). */
    suspend fun <T> withWritesBlocked(block: suspend () -> T): T = writeMutex.withLock { block() }

    private fun entries() = firestore.collection(ENTRIES_COLLECTION)

    private suspend fun observeHabits(userId: String) {
        firestoreRepository.getUserHabits(userId).collect { remoteHabits ->
            writeForUser(userId) {
                for (remoteHabit in remoteHabits) {
                    val localHabit = habitDao.getHabitById(remoteHabit.id)
                    // LWW по updatedAtMillis; локальную правку, ещё не ушедшую в облако, не перетираем
                    val apply = localHabit == null ||
                        (remoteHabit.updatedAtMillis >= localHabit.updatedAtMillis &&
                            !syncManager.hasPendingOperations("habit", remoteHabit.id))
                    if (apply) habitDao.insertHabit(remoteHabit.toEntity())
                }
            }
        }
    }

    private suspend fun loadOwnHistoryOnce(userId: String) {
        val key = HISTORY_LOADED_KEY_PREFIX + userId
        if (prefs.getBoolean(key, false)) return
        try {
            // Только с сервера: из кэша пришла бы неполная история, а флаг поставился бы навсегда
            val snapshot = entries().whereEqualTo("userId", userId).get(Source.SERVER).await()
            Timber.tag(TAG).d("[history] ${snapshot.size()} documents")
            val parsed = snapshot.documents.mapNotNull { parseEntry(it) }
            writeForUser(userId) {
                mergeEntries(parsed)
                // Битые документы не повторяем: при следующей загрузке они будут такими же
                prefs.edit().putBoolean(key, true).apply()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "[history] load failed, will retry on next start")
        }
    }

    private suspend fun observeHabitData(key: HabitKey, userId: String) = coroutineScope {
        launch { keepRestarting(TAG, "entries ${key.habitId}") { observeEntries(key, userId) } }
        launch { keepRestarting(TAG, "settings ${key.habitId}") { observeSettings(key.habitId, userId) } }
    }

    /**
     * Первый раз слушатель берёт всю историю привычки, дальше — окно последних
     * [ENTRY_WINDOW_DAYS] дней, но не позже дня последней сверки с сервером: так устройство,
     * которое долго не открывали, не пропустит отметки за это время.
     */
    private suspend fun observeEntries(key: HabitKey, userId: String) {
        val syncedOnKey = SYNCED_ON_KEY_PREFIX + userId + "_" + key.habitId
        val syncedOn = prefs.getString(syncedOnKey, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Личная — только свои отметки: индекс (habitId, userId, date); групповая — все: индекс (habitId, date)
        var query: Query = entries().whereEqualTo("habitId", key.habitId)
        if (!key.isGroup) query = query.whereEqualTo("userId", userId)
        if (syncedOn != null) {
            val since = minOf(today().minus(ENTRY_WINDOW_DAYS, DateTimeUnit.DAY), syncedOn.minus(1, DateTimeUnit.DAY))
            query = query.whereGreaterThanOrEqualTo("date", since.toString())
        }
        val label = "${if (key.isGroup) "group" else "personal"} entries ${key.habitId}"
        entryChanges(query, label).collect { batch ->
            writeForUser(userId) {
                mergeEntries(batch.entries)
                // Room сверен с сервером на сегодня — следующий запуск может слушать только окно
                if (!batch.fromCache) prefs.edit().putString(syncedOnKey, today().toString()).apply()
            }
        }
    }

    /**
     * Изменённые и новые документы каждого снимка; первый снимок отдаёт все документы. Пустую
     * пачку отдаёт, когда данные впервые подтвердил сервер. Удалённые документы и выпавшие из окна
     * не трогают Room: удаление отметки — это пометка в документе, а старая история остаётся.
     */
    private fun entryChanges(query: Query, label: String): Flow<EntryBatch> = callbackFlow {
        var confirmedByServer = false
        // MetadataChanges.INCLUDE — чтобы узнать о подтверждении сервером, даже если данные не менялись
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val fromCache = snapshot.metadata.isFromCache
            val changed = snapshot.getDocumentChanges(MetadataChanges.EXCLUDE)
                .filter { it.type != DocumentChange.Type.REMOVED }
            val firstServerSnapshot = !fromCache && !confirmedByServer
            if (changed.isEmpty() && !firstServerSnapshot) return@addSnapshotListener
            if (!fromCache) confirmedByServer = true
            Timber.tag(TAG).d(
                "[$label] snapshot: ${snapshot.size()} documents, ${changed.size} changed, fromCache=$fromCache"
            )
            trySend(EntryBatch(changed.mapNotNull { parseEntry(it.document) }, fromCache))
        }
        awaitClose { registration.remove() }
    }.buffer(Channel.UNLIMITED)

    private fun parseEntry(doc: DocumentSnapshot): RemoteEntry? = try {
        doc.toObject(FirebaseHabitEntry::class.java)?.toDomain()?.let { entry ->
            val deleted = isTombstone(
                deletedAtMillis = doc.getLong("deletedAtMillis"),
                updatedAtMillis = doc.getLong("updatedAtMillis"),
                deletedOn = doc.getString("deletedOn")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                updatedAt = entry.updatedAt
            )
            RemoteEntry(entry, deleted)
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "skip malformed habit entry ${doc.id}")
        null
    }

    /**
     * Кладёт отметки из облака в Room, а удалённые в облаке убирает — одной транзакцией.
     * Локальную запись не трогает, если по ней есть неотправленная операция или она новее облачной.
     * Пропущенная так отметка вернётся при следующем снимке или запуске слушателя.
     */
    private suspend fun mergeEntries(remote: List<RemoteEntry>) {
        if (remote.isEmpty()) return
        val pending = syncManager.pendingEntityIds("habit_entry")
        val upserts = mutableListOf<HabitEntry>()
        val deletes = mutableListOf<HabitEntry>()
        for ((entry, isDeleted) in remote) {
            if (HabitEntryIds.make(entry.habitId, entry.userId, entry.date) in pending) continue
            val local = habitEntryDao.getEntry(entry.habitId, entry.userId, entry.date)?.toDomain()
            when {
                isDeleted -> if (local != null) deletes += entry
                shouldApplyRemote(entry, local) -> upserts += entry
            }
        }
        if (upserts.isEmpty() && deletes.isEmpty()) return
        habitEntryDao.applyRemote(upserts.map { it.toEntity() }, deletes.map { it.toEntity() })
        Timber.tag(TAG).d("entries of ${remote.size}: ${upserts.size} saved, ${deletes.size} deleted")
    }

    private suspend fun observeSettings(habitId: String, userId: String) {
        callbackFlow {
            val registration = FirebaseParticipantSettings.document(firestore, habitId, userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    // Документа нет — настройки живут только локально, трогать их нечем
                    val data = snapshot?.data ?: return@addSnapshotListener
                    try {
                        trySend(FirebaseParticipantSettings.toDomain(habitId, userId, data))
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "skip malformed settings habit=$habitId")
                    }
                }
            awaitClose { registration.remove() }
        }.buffer(Channel.CONFLATED).collect { remote ->
            writeForUser(userId) { mergeSettings(remote) }
        }
    }

    private suspend fun mergeSettings(remote: HabitParticipantSettings) {
        if (syncManager.hasPendingOperations("habit_participant_settings", "${remote.habitId}:${remote.userId}")) return
        val local = participantSettingsDao.get(remote.habitId, remote.userId)?.toDomain()
        if (shouldApplyRemote(remote, local)) participantSettingsDao.upsert(remote.toEntity())
    }

    /** Пишет в Room, только пока [userId] в аккаунте: снимок, пришедший после выхода, не оживит стёртые данные. */
    private suspend fun writeForUser(userId: String, block: suspend () -> Unit) = writeMutex.withLock {
        if (firebaseAuth.currentUser?.uid != userId) {
            Timber.tag(TAG).d("user $userId signed out, skip write")
            return@withLock
        }
        block()
    }

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private data class HabitKey(val habitId: String, val isGroup: Boolean)

    private data class RemoteEntry(val entry: HabitEntry, val isDeleted: Boolean)

    private class EntryBatch(val entries: List<RemoteEntry>, val fromCache: Boolean)

    companion object {
        private const val TAG = "RemoteDataSync"
        private const val ENTRIES_COLLECTION = "habit_entries"

        /** Глубина окна отметок: правки с другого устройства почти всегда за последние дни. */
        private const val ENTRY_WINDOW_DAYS = 14

        const val PREFS_NAME = "remote_data_sync"
        private const val SYNCED_ON_KEY_PREFIX = "entries_synced_on_"
        private const val HISTORY_LOADED_KEY_PREFIX = "entry_history_loaded_"

        /**
         * Удалённая отметка хранит в облаке `deletedAtMillis`. Новые версии при повторной отметке
         * снимают его и пишут свежий `updatedAtMillis`; старые не снимали, поэтому запись,
         * изменённая днём позже удаления, тоже считается живой. День удаления берём из `deletedOn`
         * (часы удалившего), а у старых надгробий — переводом `deletedAtMillis` в свой пояс.
         */
        internal fun isTombstone(
            deletedAtMillis: Long?,
            updatedAtMillis: Long?,
            deletedOn: LocalDate?,
            updatedAt: LocalDate
        ): Boolean {
            if (deletedAtMillis == null) return false
            if (updatedAtMillis != null && updatedAtMillis > deletedAtMillis) return false
            val deletionDay = deletedOn ?: Instant.fromEpochMilliseconds(deletedAtMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            return updatedAt <= deletionDay
        }

        /**
         * Отметка из облака заменяет локальную, если та старше. updatedAt у отметки — дата без
         * времени, поэтому при равной дате и другом содержимом тоже берём облачную.
         */
        internal fun shouldApplyRemote(remote: HabitEntry, local: HabitEntry?): Boolean =
            local == null || remote.updatedAt > local.updatedAt ||
                (remote.updatedAt == local.updatedAt && !remote.hasSameContent(local))

        /** Часы устройств расходятся, поэтому при отличии содержимого настроек верим облаку. */
        internal fun shouldApplyRemote(remote: HabitParticipantSettings, local: HabitParticipantSettings?): Boolean =
            local == null || remote.updatedAt > local.updatedAt || !remote.hasSameContent(local)

        private fun HabitEntry.hasSameContent(other: HabitEntry): Boolean =
            completed == other.completed &&
                completedAt == other.completedAt &&
                value == other.value &&
                note == other.note &&
                isRestDay == other.isRestDay &&
                streakBonusAwardedDays == other.streakBonusAwardedDays &&
                photoUrl == other.photoUrl

        private fun HabitParticipantSettings.hasSameContent(other: HabitParticipantSettings): Boolean =
            reminderEnabled == other.reminderEnabled &&
                reminderTime == other.reminderTime &&
                reminderDays == other.reminderDays &&
                notificationsMuted == other.notificationsMuted &&
                seasonStartDate == other.seasonStartDate &&
                timeZone == other.timeZone
    }
}
