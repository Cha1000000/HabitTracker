package com.habittracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habittracker.data.local.entity.ChallengeEntity
import com.habittracker.data.local.entity.FriendshipEntity
import com.habittracker.data.local.entity.HabitEntity
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        ChallengeEntity::class,
        FriendshipEntity::class
    ],
    version = 1,
    exportSchema = false // Для простоты отключаем экспорт схемы
)
@TypeConverters(Converters::class)
abstract class HabitTrackerDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun friendshipDao(): FriendshipDao

    companion object {
        const val DATABASE_NAME = "habit_tracker_db"


        @Volatile
        private var INSTANCE: HabitTrackerDatabase? = null

        fun getDatabase(context: Context): HabitTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HabitTrackerDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        // Добавим миграции по мере необходимости
                    )
                    .fallbackToDestructiveMigration() // Только для разработки!
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // Миграция с версии 1 на 2 (пример для будущих изменений)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Пример: добавление нового поля
                // database.execSQL("ALTER TABLE habits ADD COLUMN difficulty INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Миграция с версии 2 на 3 (пример)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Пример: создание новой таблицы для челленджей
                /*
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS challenges (
                        id TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        rules TEXT NOT NULL,
                        reward TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
                    )
                """)
                */
            }
        }
    }

    // Callback для первоначального заполнения БД (опционально)
    class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Здесь можно добавить начальные данные
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Включаем поддержку foreign keys
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }
}
