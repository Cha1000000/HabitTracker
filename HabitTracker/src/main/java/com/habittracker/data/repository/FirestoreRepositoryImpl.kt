package com.habittracker.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.habittracker.data.model.FirebaseHabitEntry
import com.habittracker.data.model.FirebaseHabitEntry.Companion.toDomain
import com.habittracker.data.model.FirebaseParticipantSettings
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.FirestoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDate.Companion.parse
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Реализация FirestoreRepository для работы с Firebase Firestore.
 */
@Singleton
class FirestoreRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirestoreRepository {

    companion object {
        private const val HABITS_COLLECTION = "habits"
        private const val HABIT_ENTRIES_COLLECTION = "habit_entries"
        private const val PUBLIC_HABITS_LIMIT = 100L
        private const val TAG = "FirestoreRepository"

        /** Свои привычки и те, где пользователь участник: без дублей (берём свежую копию), свежие сверху. */
        internal fun mergeUserHabits(created: List<Habit>, participating: List<Habit>): List<Habit> =
            (created + participating)
                .groupBy { it.id }
                .map { (_, copies) -> copies.maxBy { it.updatedAtMillis } }
                .sortedByDescending { it.updatedAtMillis }
    }

    private fun parseHabitSafely(doc: DocumentSnapshot): Habit? {
        return try {
            val data = doc.data ?: return null

            val id = doc.id
            val title = data["title"] as? String ?: ""
            val description = data["description"] as? String ?: ""

            // icon
            val icon = data["icon"] as? String ?: "🎯"

            // color может быть числом или строкой
            val color: String = when (val raw = data["color"]) {
                is Number -> run {
                    val intVal = raw.toInt()
                    "#%06X".format(0xFFFFFF and intVal)
                }
                is String -> raw
                else -> "#2196F3"
            }

            // type и frequency как строки
            val typeStr = (data["type"] as? String)?.uppercase() ?: "PERSONAL"
            val type = runCatching { com.habittracker.domain.model.HabitType.valueOf(typeStr) }
                .getOrElse { com.habittracker.domain.model.HabitType.PERSONAL }

            val freqStr = (data["frequency"] as? String)?.uppercase() ?: "DAILY"
            val frequency = runCatching { com.habittracker.domain.model.HabitFrequency.valueOf(freqStr) }
                .getOrElse { com.habittracker.domain.model.HabitFrequency.DAILY }

            // reminderTime может быть сериализован сложным объектом или картой c hour/minute
            val reminderTime: kotlinx.datetime.LocalTime? = when (val rt = data["reminderTime"]) {
                is Map<*, *> -> {
                    val hour = (rt["hour"] as? Number)?.toInt()
                    val minute = (rt["minute"] as? Number)?.toInt()
                    if (hour != null && minute != null) kotlinx.datetime.LocalTime(hour, minute) else null
                }
                else -> null
            }

            val rawEnabled = data["reminderEnabled"] as? Boolean
            val reminderEnabled = when (rawEnabled) {
                null -> (reminderTime != null)
                false -> if (reminderTime != null) true else false
                else -> rawEnabled
            }
            val targetValue = (data["targetValue"] as? Number)?.toInt() ?: 1
            // reminderDays как список чисел (1..7)
            @Suppress("UNCHECKED_CAST")
            val reminderDaysRaw: List<Int> = (data["reminderDays"] as? List<*>)
                ?.filterIsInstance<Number>()
                ?.map { it.toInt() }
                ?: emptyList()
            val unit = data["unit"] as? String ?: "раз"

            val createdBy = data["createdBy"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val participants = (data["participants"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val maxParticipants = (data["maxParticipants"] as? Number)?.toInt() ?: 10
            val inviteCode = data["inviteCode"] as? String
            val isActive: Boolean = (data["isActive"] as? Boolean) ?: true

            val updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong()
                ?: run {
                    // Fallback: если millis нет, используем LocalDate updatedAt в начале дня
                    val fallback = (data["updatedAt"] as? String)?.let { runCatching { parse(it) }.getOrNull() }
                    (fallback ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }

            val deletedAtMillis = (data["deletedAtMillis"] as? Number)?.toLong()

            // timestamps - теперь LocalDate
            val createdAt = (data["createdAt"] as? String)?.let {
                try {
                    parse(it)
                } catch (_: Exception) {
                    Timber.tag(TAG).w("Failed to parse createdAt: $it")
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                }
            } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            
            val updatedAt = (data["updatedAt"] as? String)?.let {
                try {
                    parse(it)
                } catch (_: Exception) {
                    Timber.tag(TAG).w("Failed to parse updatedAt: $it")
                    createdAt
                }
            } ?: createdAt
            
            // Даты начала и окончания
            val startDate = (data["startDate"] as? String)?.let {
                try {
                    parse(it)
                } catch (_: Exception) {
                    Timber.tag(TAG).w("Failed to parse startDate: $it")
                    null
                }
            }
            
            val endDate = (data["endDate"] as? String)?.let {
                try {
                    parse(it)
                } catch (_: Exception) {
                    Timber.tag(TAG).w("Failed to parse endDate: $it")
                    null
                }
            }

            val timeOfDayStr = (data["timeOfDay"] as? String)?.uppercase() ?: "ANY_TIME"
            val timeOfDay = runCatching { com.habittracker.domain.model.HabitTimeOfDay.valueOf(timeOfDayStr) }
                .getOrElse { com.habittracker.domain.model.HabitTimeOfDay.ANY_TIME }

            val goalTypeStr = (data["goalType"] as? String)?.uppercase() ?: "SIMPLE"
            val goalType = runCatching { com.habittracker.domain.model.HabitGoalType.valueOf(goalTypeStr) }
                .getOrElse { com.habittracker.domain.model.HabitGoalType.SIMPLE }

            Habit(
                id = id,
                title = title,
                description = description,
                icon = icon,
                color = color,
                type = type,
                frequency = frequency,
                reminderTime = reminderTime,
                reminderEnabled = reminderEnabled,
                reminderDays = reminderDaysRaw.mapNotNull { d ->
                    kotlinx.datetime.DayOfWeek.entries.firstOrNull { it.isoDayNumber == d }
                }.toSet(),
                targetValue = targetValue,
                unit = unit,
                createdBy = createdBy,
                participants = participants,
                maxParticipants = maxParticipants,
                inviteCode = inviteCode,
                isPublic = data["public"] as? Boolean ?: false,
                isActive = isActive,
                createdAt = createdAt,
                updatedAt = updatedAt,
                updatedAtMillis = updatedAtMillis,
                deletedAtMillis = deletedAtMillis,
                startDate = startDate,
                endDate = endDate,
                timeOfDay = timeOfDay,
                goalType = goalType
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ [parseHabitSafely] Error parsing habit: ${doc.id}")
            null
        }
    }

    override fun getUserHabits(userId: String): Flow<List<Habit>> = callbackFlow {
        Timber.tag(TAG).d("[getUserHabits] Starting listeners for user: $userId")

        // Firestore не умеет OR по разным полям, поэтому два запроса: свои и те, где пользователь
        // участник. Раньше слушали всю коллекцию и фильтровали на клиенте — каждый запуск читал
        // привычки всех пользователей (сотни документов вместо десятка)
        var created: List<Habit>? = null
        var participating: List<Habit>? = null

        fun emitMerged() {
            // Ждём оба снимка, иначе первым уйдёт неполный список
            val own = created ?: return
            val joined = participating ?: return
            val habits = mergeUserHabits(own, joined)
            Timber.tag(TAG).d("[getUserHabits] Sending ${habits.size} habits for user: $userId")
            trySend(habits)
        }

        fun listen(query: Query, label: String, onHabits: (List<Habit>) -> Unit) =
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Слушатель после ошибки замолкает навсегда; закрываем поток, перезапуск — у вызывающего
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                Timber.tag(TAG).d("[getUserHabits] $label snapshot: ${docs.size} documents")
                onHabits(docs.mapNotNull { parseHabitSafely(it) })
                emitMerged()
            }

        val habits = firestore.collection(HABITS_COLLECTION)
        val createdListener = listen(habits.whereEqualTo("createdBy", userId), "createdBy") { created = it }
        val participantListener = listen(habits.whereArrayContains("participants", userId), "participants") {
            participating = it
        }

        awaitClose {
            Timber.tag(TAG).d("[getUserHabits] Closing listeners for user: $userId")
            createdListener.remove()
            participantListener.remove()
        }
    }


    override suspend fun getHabitById(habitId: String): Habit? {
        return try {
            Timber.tag(TAG).d("Getting habit by ID: $habitId")
            val document = firestore.collection(HABITS_COLLECTION)
                .document(habitId)
                .get()
                .await()
            
            if (document.exists()) {
                // Используем безопасный парсер, чтобы корректно обработать возможные различия форматов
                parseHabitSafely(document)
            } else {
                Timber.tag(TAG).d("Habit not found: $habitId")
                null
            }
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (
                e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                e.code == FirebaseFirestoreException.Code.CANCELLED
            ) {
                Timber.tag(TAG).d("getHabitById: transient/unauthorized state for habit=$habitId, code=${e.code}")
                return null
            } else {
                Timber.tag(TAG).e(e, "Error getting habit: $habitId, code=${e.code}")
            }
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting habit: $habitId")
            throw e
        }
    }


    override suspend fun syncData() {
        // Базовая синхронизация - в будущем можно расширить
        // для более сложной логики конфликтов и офлайн-поддержки
        Timber.tag(TAG).d("Data sync completed (basic implementation)")
    }

    // region Participant settings
    override suspend fun getParticipantSettings(habitId: String, userId: String): HabitParticipantSettings? {
        return try {
            val doc = FirebaseParticipantSettings.document(firestore, habitId, userId).get().await()
            val data = doc.data ?: return null
            FirebaseParticipantSettings.toDomain(habitId, userId, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Timber.tag(TAG).d("getParticipantSettings: device is offline, habit=$habitId user=$userId")
            } else {
                Timber.tag(TAG).e(e, "Error getParticipantSettings: habit=$habitId user=$userId, code=${e.code}")
            }
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getParticipantSettings: habit=$habitId user=$userId")
            null
        }
    }

    // endregion
    
    override fun getAllPublicHabits(excludeUserId: String?): Flow<List<Habit>> = callbackFlow {
        try {
            val query: Query = firestore.collection(HABITS_COLLECTION)
                .whereEqualTo("public", true)
                .whereEqualTo("isActive", true)
                // Каталог листают глазами: больше сотни никто не просмотрит, а читается каждый документ
                .limit(PUBLIC_HABITS_LIMIT)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
                    if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        Timber.tag(TAG).d("[getAllPublicHabits] Device is offline, using cached data")
                    } else {
                        Timber.tag(TAG).e(error, "Error getting public habits: ${error.code}")
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val habits = snapshot.documents.mapNotNull { doc ->
                        parseHabitSafely(doc)?.let { habit ->
                            // Фильтруем привычки текущего пользователя
                            if (excludeUserId != null && habit.createdBy == excludeUserId) {
                                null
                            } else {
                                habit
                            }
                        }
                    }
                    
                    Timber.tag(TAG).d("Got ${habits.size} public habits from Firestore")
                    trySend(habits)
                } else {
                    trySend(emptyList())
                }
            }
            
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in getAllPublicHabits")
            trySend(emptyList())
        }
    }
    
    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = suspendCancellableCoroutine { continuation ->
        Timber.tag(TAG).d("🔍 [getHabitByInviteCode] Searching for habit with invite code: $inviteCode")
        
        firestore.collection(HABITS_COLLECTION)
            .whereEqualTo("inviteCode", inviteCode)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                val habit = documents.documents.firstOrNull()?.let { doc ->
                    parseHabitSafely(doc)
                }
                
                if (habit != null) {
                    Timber.tag(TAG).d("✅ [getHabitByInviteCode] Found habit: ${habit.title} (${habit.id})")
                } else {
                    Timber.tag(TAG).d("❌ [getHabitByInviteCode] No habit found with code: $inviteCode")
                }
                
                continuation.resume(habit)
            }
            .addOnFailureListener { exception ->
                // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
                if (exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    Timber.tag(TAG).d("[getHabitByInviteCode] Device is offline")
                } else {
                    Timber.tag(TAG).e(exception, "❌ [getHabitByInviteCode] Error searching for habit")
                }
                continuation.resume(null)
            }
    }
}
