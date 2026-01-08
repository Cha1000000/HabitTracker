package com.habittracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habittracker.BuildConfig
import com.habittracker.data.local.dao.AchievementDao
import com.habittracker.data.local.dao.ChallengeDao
import com.habittracker.data.local.dao.FriendRequestDao
import com.habittracker.data.local.dao.FriendshipDao
import com.habittracker.data.local.dao.GroupHabitNotificationDao
import com.habittracker.data.local.dao.HabitDao
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.dao.HabitInvitationDao
import com.habittracker.data.local.dao.HabitParticipantSettingsDao
import com.habittracker.data.local.dao.NotificationDao
import com.habittracker.data.local.dao.PendingNotificationDao
import com.habittracker.data.local.dao.SyncOutboxDao
import com.habittracker.data.local.dao.UserAchievementDao
import com.habittracker.data.local.dao.UserDao
import com.habittracker.data.local.entity.AchievementEntity
import com.habittracker.data.local.entity.ChallengeEntity
import com.habittracker.data.local.entity.FriendRequestEntity
import com.habittracker.data.local.entity.FriendshipEntity
import com.habittracker.data.local.entity.GroupHabitNotificationEntity
import com.habittracker.data.local.entity.HabitEntity
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.HabitInvitationEntity
import com.habittracker.data.local.entity.HabitParticipantSettingsEntity
import com.habittracker.data.local.entity.NotificationReadStatusEntity
import com.habittracker.data.local.entity.PendingNotificationEntity
import com.habittracker.data.local.entity.SyncOutboxEntity
import com.habittracker.data.local.entity.UserAchievementEntity
import com.habittracker.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        ChallengeEntity::class,
        FriendshipEntity::class,
        FriendRequestEntity::class,
        HabitInvitationEntity::class,
        AchievementEntity::class,
        UserAchievementEntity::class,
        PendingNotificationEntity::class,
        SyncOutboxEntity::class,
        HabitParticipantSettingsEntity::class,
        NotificationReadStatusEntity::class,
        GroupHabitNotificationEntity::class
    ],
    version = 12,
    exportSchema = false
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
    abstract fun achievementDao(): AchievementDao
    abstract fun userAchievementDao(): UserAchievementDao
    abstract fun pendingNotificationDao(): PendingNotificationDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun habitParticipantSettingsDao(): HabitParticipantSettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun groupHabitNotificationDao(): GroupHabitNotificationDao

    companion object {
        const val DATABASE_NAME = "habit_tracker_db"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN deletedAtMillis INTEGER")

                db.execSQL("ALTER TABLE challenges ADD COLUMN deletedAtMillis INTEGER")

                // Best-effort backfill for ordering/merge (LocalDate stored as TEXT YYYY-MM-DD)
                db.execSQL(
                    "UPDATE habits SET updatedAtMillis = COALESCE(updatedAtMillis, 0) WHERE updatedAtMillis IS NULL"
                )
                db.execSQL(
                    "UPDATE habits SET updatedAtMillis = (strftime('%s', updatedAt) * 1000) WHERE updatedAtMillis = 0 AND updatedAt IS NOT NULL"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_achievements ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE user_achievements SET updatedAtMillis = CASE WHEN updatedAtMillis = 0 THEN createdAt ELSE updatedAtMillis END"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Индексы для таблицы habits
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_createdBy ON habits(createdBy)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_type ON habits(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_isActive ON habits(isActive)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_startDate ON habits(startDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_endDate ON habits(endDate)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habits_inviteCode ON habits(inviteCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_createdBy_isActive ON habits(createdBy, isActive)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_type_isActive ON habits(type, isActive)")
                
                // Индексы для таблицы habit_entries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_habitId ON habit_entries(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_userId ON habit_entries(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_date ON habit_entries(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_completed ON habit_entries(completed)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_habitId_date ON habit_entries(habitId, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_userId_date ON habit_entries(userId, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entries_habitId_userId ON habit_entries(habitId, userId)")
                
                // Индексы для таблицы users
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users(email)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_name ON users(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_lastActiveAt ON users(lastActiveAt)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN followUpReminderEnabled INTEGER NOT NULL DEFAULT 1")
                // Store LocalTime as String (HH:mm) or update type converter if it stores as something else.
                // Assuming standard ISO-8601 string format for LocalTime in this project based on other fields if they use standard converter.
                // Looking at HabitEntity, reminderTime is LocalTime. Let's check Converters later if needed, usually string.
                // Default '02:00'
                db.execSQL("ALTER TABLE habits ADD COLUMN followUpReminderDuration TEXT NOT NULL DEFAULT '02:00'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_entries ADD COLUMN isRestDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN timeOfDay TEXT NOT NULL DEFAULT 'ANY_TIME'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN goalType TEXT NOT NULL DEFAULT 'SIMPLE'")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN timeGroupId TEXT DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: HabitTrackerDatabase? = null

        fun getDatabase(context: Context): HabitTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    HabitTrackerDatabase::class.java,
                    DATABASE_NAME
                )

                if (BuildConfig.DEBUG) {
                    // В debug-сборках допускаем деструктивные миграции для ускорения разработки
                    builder.fallbackToDestructiveMigration()
                }

                builder.addMigrations(
                    MIGRATION_4_5, 
                    MIGRATION_5_6, 
                    MIGRATION_6_7, 
                    MIGRATION_7_8, 
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12
                )

                val instance = builder.build()

                INSTANCE = instance
                instance
            }
        }

        // Здесь будут добавляться миграции при необходимости в будущем
        // Пример:
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE users ADD COLUMN newField TEXT")
        //     }
        // }
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
