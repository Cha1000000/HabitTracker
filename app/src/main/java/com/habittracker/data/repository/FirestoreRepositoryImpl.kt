package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
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

    override fun getUserHabits(userId: String): Flow<List<Habit>> = callbackFlow {
        Log.d(TAG, "🔄 [getUserHabits] Starting listener for user: $userId")
        
        // 🚨 ДИАГНОСТИКА #2: МИНИМАЛЬНЫЙ QUERY - проблема в фильтрах, а не в десериализации
        val listener = firestore.collection(HABITS_COLLECTION)
            // ВРЕМЕННО УБИРАЕМ ВСЕ ФИЛЬТРЫ для диагностики фильтрации
            // .whereArrayContains("participants", userId)
            // .whereEqualTo("isActive", true)
            // .orderBy("createdAt", Query.Direction.DESCENDING)
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
                    try {
                        val habit = doc.toObject(Habit::class.java)?.copy(id = doc.id)
                        Log.d(TAG, "📝 [getUserHabits] Parsed habit: ${habit?.title} (id: ${doc.id}), participants: ${habit?.participants}")
                        habit
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [getUserHabits] Error parsing habit: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

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
            firestore.collection(HABITS_COLLECTION)
                .document(habit.id)
                .set(habit)
                .await()
            Log.d(TAG, "✅ [saveHabit] Habit saved successfully: ${habit.id}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [saveHabit] Error saving habit: ${habit.id}", e)
            throw e
        }
    }

    override suspend fun updateHabit(habit: Habit) {
        try {
            Log.d(TAG, "Updating habit: ${habit.id}")
            firestore.collection(HABITS_COLLECTION)
                .document(habit.id)
                .set(habit)
                .await()
            Log.d(TAG, "Habit updated successfully: ${habit.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating habit: ${habit.id}", e)
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
                document.toObject(Habit::class.java)?.copy(id = document.id)
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
}
