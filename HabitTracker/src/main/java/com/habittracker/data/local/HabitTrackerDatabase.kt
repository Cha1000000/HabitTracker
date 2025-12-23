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
    version = 6,
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

                builder.addMigrations(MIGRATION_4_5, MIGRATION_5_6)

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
