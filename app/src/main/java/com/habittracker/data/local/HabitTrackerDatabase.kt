package com.habittracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habittracker.data.local.entity.ChallengeEntity
import com.habittracker.data.local.entity.FriendRequestEntity
import com.habittracker.data.local.entity.FriendshipEntity
import com.habittracker.data.local.entity.HabitEntity
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.HabitInvitationEntity
import com.habittracker.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        ChallengeEntity::class,
        FriendshipEntity::class,
        FriendRequestEntity::class,
        HabitInvitationEntity::class
    ],
    version = 3,
    exportSchema = false // Для простоты отключаем экспорт схемы
)
@TypeConverters(Converters::class)
abstract class HabitTrackerDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun friendshipDao(): FriendshipDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun habitInvitationDao(): HabitInvitationDao

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
                        MIGRATION_1_2,
                        MIGRATION_2_3
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
                // Добавляем новые поля в таблицу users
                db.execSQL("ALTER TABLE users ADD COLUMN friendsCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN fcmToken TEXT")
                
                // Создаем таблицу friend_requests
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS friend_requests (
                        id TEXT NOT NULL PRIMARY KEY,
                        fromUserId TEXT NOT NULL,
                        toUserId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        message TEXT,
                        createdAt INTEGER NOT NULL,
                        respondedAt INTEGER,
                        updatedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        // Миграция с версии 2 на 3 (групповые привычки)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем новые поля в таблицу habits для групповых привычек
                db.execSQL("ALTER TABLE habits ADD COLUMN maxParticipants INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE habits ADD COLUMN inviteCode TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN isPublic INTEGER NOT NULL DEFAULT 0")
                
                // Создаем таблицу habit_invitations
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS habit_invitations (
                        id TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        habitTitle TEXT NOT NULL,
                        fromUserId TEXT NOT NULL,
                        fromUserName TEXT NOT NULL,
                        toUserId TEXT NOT NULL,
                        message TEXT,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        respondedAt INTEGER,
                        expiresAt INTEGER,
                        FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
                    )
                """)
                
                // Создаем индексы для оптимизации запросов
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_invitations_habitId ON habit_invitations(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_invitations_fromUserId ON habit_invitations(fromUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_invitations_toUserId ON habit_invitations(toUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_invitations_status ON habit_invitations(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_invitations_createdAt ON habit_invitations(createdAt)")
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
