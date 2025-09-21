package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.repository.FirestoreRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

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
        private const val TAG = "FirestoreRepository"
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

            // timestamps
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt

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
                    kotlinx.datetime.DayOfWeek.entries.firstOrNull { it.value == d }
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
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ [parseHabitSafely] Error parsing habit: ${doc.id}", e)
            null
        }
    }

    override fun getUserHabits(userId: String): Flow<List<Habit>> = callbackFlow {
        Log.d(TAG, "🔄 [getUserHabits] Starting listener for user: $userId")
        
        // Важно: Firestore не поддерживает простой OR по полям (createdBy OR participants contains).
        // Берем все документы и фильтруем на клиенте. При желании можно добавить whereEqualTo("active", true),
        // но поле может называться по-разному ("active"/"isActive") на старых данных, поэтому безопаснее без фильтра.
        val listener = firestore.collection(HABITS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ [getUserHabits] Error getting habits for user: $userId", error)
                    Log.e(TAG, "❌ [getUserHabits] Error code: ${error.code}")
                    Log.e(TAG, "❌ [getUserHabits] Error message: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }

                Log.d(TAG, "📊 [getUserHabits] Firestore listener triggered for user: $userId, documents count: ${snapshot?.documents?.size ?: 0}")

                val habits = snapshot?.documents?.mapNotNull { doc ->
                    parseHabitSafely(doc)
                }?.filter { habit ->
                    // Клиентская фильтрация: активные привычки пользователя (создал сам или участник)
                    habit.isActive && (habit.createdBy == userId || habit.participants.contains(userId))
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                Log.d(TAG, "✅ [getUserHabits] Sending ${habits.size} habits to Flow for user: $userId")
                habits.forEach { habit ->
                    Log.d(TAG, "   📋 Habit: ${habit.title} (${habit.id}), participants: ${habit.participants}")
                }
                trySend(habits)
            }

        awaitClose { 
            Log.d(TAG, "🔒 [getUserHabits] Closing listener for user: $userId")
            listener.remove() 
        }
    }

    override suspend fun saveHabit(habit: Habit) {
        try {
            Log.d(TAG, "💾 [saveHabit] Saving habit: ${habit.id}")
            Log.d(TAG, "💾 [saveHabit] Habit details: title='${habit.title}', participants=${habit.participants}, isActive=${habit.isActive}")
            Log.d(TAG, "⏰ [saveHabit] reminderTime=${habit.reminderTime}, reminderEnabled(computed)=${habit.reminderTime != null}")
            val payload = buildMap<String, Any?> {
                // Простые поля
                put("id", habit.id)
                put("title", habit.title)
                put("description", habit.description)
                put("icon", habit.icon)
                put("color", habit.color)
                put("type", habit.type.name)
                put("frequency", habit.frequency.name)
                // Напоминания
                val hasReminder = habit.reminderTime != null
                put("reminderEnabled", hasReminder)
                if (hasReminder) {
                    val rt = habit.reminderTime!!
                    put("reminderTime", mapOf("hour" to rt.hour, "minute" to rt.minute))
                }
                // Target
                put("targetValue", habit.targetValue)
                put("unit", habit.unit)
                // Дни недели напоминаний
                if (habit.reminderDays.isNotEmpty()) {
                    put("reminderDays", habit.reminderDays.map { it.value })
                }
                // Социальные
                put("createdBy", habit.createdBy)
                put("participants", habit.participants)
                put("maxParticipants", habit.maxParticipants)
                put("inviteCode", habit.inviteCode)
                put("public", habit.isPublic)
                put("isActive", habit.isActive)
                // Метки времени
                put("createdAt", habit.createdAt)
                put("updatedAt", habit.updatedAt)
            }
            firestore.collection(HABITS_COLLECTION)
                .document(habit.id)
                .set(payload)
                .await()
            Log.d(TAG, "✅ [saveHabit] Habit saved successfully: ${habit.id}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [saveHabit] Error saving habit: ${habit.id}", e)
            throw e
        }
    }

    override suspend fun updateHabit(habit: Habit) {
        try {
            Log.d(TAG, "🛠️ [updateHabit] Updating habit: ${habit.id}")
            Log.d(TAG, "⏰ [updateHabit] incoming reminderTime=${habit.reminderTime}, reminderEnabled(computed)=${habit.reminderTime != null}")
            val payload = mutableMapOf<String, Any>().apply {
                put("title", habit.title)
                put("description", habit.description)
                put("icon", habit.icon)
                put("color", habit.color)
                put("type", habit.type.name)
                put("frequency", habit.frequency.name)
                val hasReminder = habit.reminderTime != null
                put("reminderEnabled", hasReminder)
                if (hasReminder) {
                    val rt = habit.reminderTime!!
                    put("reminderTime", mapOf("hour" to rt.hour, "minute" to rt.minute))
                } else {
                    put("reminderTime", FieldValue.delete())
                }
                if (habit.reminderDays.isNotEmpty()) {
                    put("reminderDays", habit.reminderDays.map { it.value })
                } else {
                    put("reminderDays", FieldValue.delete())
                }
                put("targetValue", habit.targetValue)
                put("unit", habit.unit)
                // Important guard: don't clear participants if UI didn't load them yet
                if (habit.participants.isNotEmpty()) {
                    put("participants", habit.participants)
                }
                put("maxParticipants", habit.maxParticipants)
                if (habit.inviteCode != null) {
                    put("inviteCode", habit.inviteCode)
                } else {
                    put("inviteCode", FieldValue.delete())
                }
                put("public", habit.isPublic)
                put("isActive", habit.isActive)
                put("updatedAt", habit.updatedAt)
            }
            firestore.collection(HABITS_COLLECTION)
                .document(habit.id)
                .update(payload as Map<String, Any>)
                .await()
            Log.d(TAG, "✅ [updateHabit] Habit updated successfully: ${habit.id}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [updateHabit] Error updating habit: ${habit.id}", e)
            throw e
        }
    }

    override suspend fun deleteHabit(habitId: String) {
        try {
            Log.d(TAG, "Deleting habit: $habitId")
            // Мягкое удаление - помечаем как неактивную
            firestore.collection(HABITS_COLLECTION)
                .document(habitId)
                .update("isActive", false)
                .await()
            Log.d(TAG, "Habit deleted successfully: $habitId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting habit: $habitId", e)
            throw e
        }
    }

    override suspend fun getHabitById(habitId: String): Habit? {
        return try {
            Log.d(TAG, "Getting habit by ID: $habitId")
            val document = firestore.collection(HABITS_COLLECTION)
                .document(habitId)
                .get()
                .await()
            
            if (document.exists()) {
                // Используем безопасный парсер, чтобы корректно обработать возможные различия форматов
                parseHabitSafely(document)
            } else {
                Log.d(TAG, "Habit not found: $habitId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting habit: $habitId", e)
            throw e
        }
    }

    override suspend fun saveHabitEntry(habitEntry: HabitEntry) {
        try {
            Log.d(TAG, "Saving habit entry: ${habitEntry.habitId} for ${habitEntry.date}")
            val entryId = "${habitEntry.habitId}_${habitEntry.userId}_${habitEntry.date}"
            firestore.collection(HABIT_ENTRIES_COLLECTION)
                .document(entryId)
                .set(habitEntry)
                .await()
            Log.d(TAG, "Habit entry saved successfully: $entryId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving habit entry", e)
            throw e
        }
    }

    override fun getHabitEntries(habitId: String, userId: String): Flow<List<HabitEntry>> = callbackFlow {
        Log.d(TAG, "Getting habit entries for habit: $habitId, user: $userId")
        
        val listener = firestore.collection(HABIT_ENTRIES_COLLECTION)
            .whereEqualTo("habitId", habitId)
            .whereEqualTo("userId", userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting habit entries", error)
                    close(error)
                    return@addSnapshotListener
                }

                val entries = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(HabitEntry::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing habit entry: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Loaded ${entries.size} habit entries from Firestore")
                trySend(entries)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? {
        return try {
            Log.d(TAG, "Getting habit entry for: $habitId, $userId, $date")
            val entryId = "${habitId}_${userId}_$date"
            val document = firestore.collection(HABIT_ENTRIES_COLLECTION)
                .document(entryId)
                .get()
                .await()
            
            if (document.exists()) {
                document.toObject(HabitEntry::class.java)
            } else {
                Log.d(TAG, "Habit entry not found: $entryId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting habit entry", e)
            throw e
        }
    }

    override suspend fun syncData() {
        // Базовая синхронизация - в будущем можно расширить
        // для более сложной логики конфликтов и офлайн-поддержки
        Log.d(TAG, "Data sync completed (basic implementation)")
    }

    // region Participant settings
    private fun participantSettingsDoc(habitId: String, userId: String) =
        firestore.collection(HABITS_COLLECTION)
            .document(habitId)
            .collection("participants")
            .document(userId)
            .collection("meta")
            .document("settings")

    override suspend fun getParticipantSettings(habitId: String, userId: String): HabitParticipantSettings? {
        return try {
            val doc = participantSettingsDoc(habitId, userId).get().await()
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val rt = data["reminderTime"] as? Map<*, *>
            val reminderTime = if (rt != null) {
                val hour = (rt["hour"] as? Number)?.toInt()
                val minute = (rt["minute"] as? Number)?.toInt()
                if (hour != null && minute != null) kotlinx.datetime.LocalTime(hour, minute) else null
            } else null
            val reminderEnabled = (data["reminderEnabled"] as? Boolean) ?: (reminderTime != null)
            @Suppress("UNCHECKED_CAST")
            val reminderDaysRaw: List<Int> = (data["reminderDays"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() } ?: emptyList()
            val notificationsMuted = data["notificationsMuted"] as? Boolean ?: false
            val timeZone = data["timeZone"] as? String
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            HabitParticipantSettings(
                habitId = habitId,
                userId = userId,
                reminderTime = reminderTime,
                reminderEnabled = reminderEnabled,
                reminderDays = reminderDaysRaw.mapNotNull { d ->
                    kotlinx.datetime.DayOfWeek.entries.firstOrNull { it.value == d }
                }.toSet(),
                notificationsMuted = notificationsMuted,
                timeZone = timeZone,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getParticipantSettings: habit=$habitId user=$userId", e)
            null
        }
    }

    override suspend fun setParticipantSettings(settings: HabitParticipantSettings) {
        try {
            // Если включено, но время null — пробуем подтянуть дефолт из документа привычки
            var finalTime = settings.reminderTime
            if (settings.reminderEnabled && finalTime == null) {
                try {
                    val habitDoc = firestore.collection(HABITS_COLLECTION).document(settings.habitId).get().await()
                    val rt = habitDoc.get("reminderTime") as? Map<*, *>
                    if (rt != null) {
                        val hour = (rt["hour"] as? Number)?.toInt()
                        val minute = (rt["minute"] as? Number)?.toInt()
                        if (hour != null && minute != null) {
                            finalTime = kotlinx.datetime.LocalTime(hour, minute)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback to habit.reminderTime failed: ${e.message}")
                }
            }

            val payload = mutableMapOf<String, Any>().apply {
                put("reminderEnabled", settings.reminderEnabled)
                if (finalTime != null && settings.reminderEnabled) {
                    put("reminderTime", mapOf("hour" to finalTime.hour, "minute" to finalTime.minute))
                } else {
                    put("reminderTime", FieldValue.delete())
                }
                if (settings.reminderDays.isNotEmpty()) {
                    put("reminderDays", settings.reminderDays.map { it.value })
                } else {
                    put("reminderDays", FieldValue.delete())
                }
                put("notificationsMuted", settings.notificationsMuted)
                settings.timeZone?.let { put("timeZone", it) } ?: put("timeZone", FieldValue.delete())
                put("updatedAt", settings.updatedAt)
            }
            participantSettingsDoc(settings.habitId, settings.userId)
                .set(payload as Map<String, Any>, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d(TAG, "setParticipantSettings OK: ${settings.habitId}/${settings.userId}, time=$finalTime, enabled=${settings.reminderEnabled}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setParticipantSettings: ${settings.habitId}/${settings.userId}", e)
            throw e
        }
    }
    // endregion
}
