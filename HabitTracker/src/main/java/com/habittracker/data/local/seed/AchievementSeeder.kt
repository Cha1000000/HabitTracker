package com.habittracker.data.local.seed

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.habittracker.data.local.dao.AchievementDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Заполняет таблицу achievements начальными данными при первом запуске.
 * Использует SharedPreferences для отслеживания версии данных.
 */
@Singleton
class AchievementSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val achievementDao: AchievementDao
) {
    companion object {
        private const val TAG = "AchievementSeeder"
        private const val PREFS_NAME = "achievement_seeder_prefs"
        private const val KEY_SEEDED_VERSION = "seeded_version"
        
        /**
         * Версия данных достижений.
         * Увеличивайте при добавлении новых достижений или изменении существующих.
         * v2: Добавлены titleResId и descriptionResId для локализации
         */
        private const val CURRENT_VERSION = 2
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Проверяет и заполняет таблицу достижений при необходимости.
     * Вызывайте при старте приложения.
     */
    suspend fun seedIfNeeded() {
        withContext(Dispatchers.IO) {
            val seededVersion = prefs.getInt(KEY_SEEDED_VERSION, 0)
            
            if (seededVersion < CURRENT_VERSION) {
                Log.d(TAG, "Seeding achievements: current=$seededVersion, target=$CURRENT_VERSION")
                seed()
                prefs.edit().putInt(KEY_SEEDED_VERSION, CURRENT_VERSION).apply()
                Log.d(TAG, "Achievements seeded successfully")
            } else {
                Log.d(TAG, "Achievements already seeded (version=$seededVersion)")
            }
        }
    }

    /**
     * Принудительно перезаполняет таблицу достижений.
     * Используйте для обновления данных при изменении версии.
     */
    suspend fun forceReseed() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Force reseeding achievements")
            seed()
            prefs.edit().putInt(KEY_SEEDED_VERSION, CURRENT_VERSION).apply()
        }
    }

    private suspend fun seed() {
        val achievements = DefaultAchievements.getAll()
        Log.d(TAG, "Inserting ${achievements.size} achievements")
        achievementDao.insertAchievements(achievements)
    }
}
