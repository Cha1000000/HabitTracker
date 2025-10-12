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
    version = 19,
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
    abstract fun achievementDao(): AchievementDao
    abstract fun userAchievementDao(): UserAchievementDao
    abstract fun pendingNotificationDao(): PendingNotificationDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun habitParticipantSettingsDao(): HabitParticipantSettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun groupHabitNotificationDao(): GroupHabitNotificationDao

    companion object {
        const val DATABASE_NAME = "habit_tracker_db"


        @Volatile
        private var INSTANCE: HabitTrackerDatabase? = null

        fun getDatabase(context: Context): HabitTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    HabitTrackerDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19
                )

                if (BuildConfig.DEBUG) {
                    // В debug-сборках допускаем деструктивные миграции для ускорения разработки
                    builder.fallbackToDestructiveMigration()
                }

                val instance = builder.build()

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

        // Миграция с версии 3 на 4 (достижения)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Создаем таблицу achievements
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT '🏆',
                        category TEXT NOT NULL,
                        type TEXT NOT NULL,
                        conditionMetric TEXT NOT NULL,
                        conditionTargetValue INTEGER NOT NULL,
                        conditionTimeframe TEXT,
                        pointsReward INTEGER NOT NULL,
                        isSecret INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // Создаем таблицу user_achievements
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_achievements (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        userId TEXT NOT NULL,
                        achievementId TEXT NOT NULL,
                        progress INTEGER NOT NULL DEFAULT 0,
                        isUnlocked INTEGER NOT NULL DEFAULT 0,
                        unlockedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY(achievementId) REFERENCES achievements(id) ON DELETE CASCADE
                    )
                """)
                
                // Создаем индексы для оптимизации запросов
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_achievements_userId ON user_achievements(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_achievements_achievementId ON user_achievements(achievementId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_achievements_user_achievement ON user_achievements(userId, achievementId)")
            }
        }

        // Миграция с версии 4 на 5 (локальная очередь уведомлений)
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_notifications (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        recipientUserId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        dataJson TEXT NOT NULL,
                        receivedAt INTEGER NOT NULL,
                        ttlSeconds INTEGER,
                        dedupKey TEXT
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_notifications_recipientUserId ON pending_notifications(recipientUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_notifications_receivedAt ON pending_notifications(receivedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_notifications_dedupKey ON pending_notifications(dedupKey)")
            }
        }

        // Миграция с версии 5 на 6 (Outbox для синхронизации)
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        operationId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        opType TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        state TEXT NOT NULL DEFAULT 'PENDING'
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_state ON sync_outbox(state)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_updatedAt ON sync_outbox(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_entity ON sync_outbox(entityType, entityId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_operationId ON sync_outbox(operationId)")
            }
        }

        // Миграция с версии 6 на 7 (миниатюры аватаров)
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем новое поле avatarThumbnailUrl в таблицу users
                db.execSQL("ALTER TABLE users ADD COLUMN avatarThumbnailUrl TEXT")
            }
        }

        // Миграция с версии 7 на 8 (нет изменений в схеме — заглушка для непрерывной цепочки)
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op (схема без изменений)
            }
        }

        // Миграция с версии 8 на 9 (дни напоминаний для привычек)
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Храним список дней недели как JSON-строку (конвертер List<Int> <-> String)
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderDays TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // Миграция с версии 9 на 10 (персональные настройки напоминаний участников)
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_participant_settings (
                        key TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        reminderTime TEXT,
                        reminderEnabled INTEGER NOT NULL,
                        reminderDays TEXT NOT NULL DEFAULT '[]',
                        notificationsMuted INTEGER NOT NULL DEFAULT 0,
                        timeZone TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hps_habit_user ON habit_participant_settings(habitId, userId)")
            }
        }
        
        // Миграция с версии 10 на 11 (статусы прочитанных уведомлений)
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_read_status (
                        notificationId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        habitId TEXT NOT NULL,
                        isRead INTEGER NOT NULL,
                        readAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_read_status_userId ON notification_read_status(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_read_status_habitId ON notification_read_status(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_read_status_isRead ON notification_read_status(isRead)")
            }
        }
        
        // Миграция с версии 11 на 12 (уведомления групповых привычек)
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS group_habit_notifications (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        habitId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        participantId TEXT NOT NULL,
                        participantName TEXT NOT NULL,
                        habitTitle TEXT NOT NULL,
                        message TEXT NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        readAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_habit_notifications_userId ON group_habit_notifications(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_habit_notifications_habitId ON group_habit_notifications(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_habit_notifications_isRead ON group_habit_notifications(isRead)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_habit_notifications_createdAt ON group_habit_notifications(createdAt)")
            }
        }
        
        // Миграция с версии 12 на 13 (даты начала и окончания привычек)
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем новые поля для дат начала и окончания привычек
                db.execSQL("ALTER TABLE habits ADD COLUMN startDate TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN endDate TEXT")
            }
        }
        
        // Миграция с версии 13 на 14 (персональная дата окончания для участников)
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем поле персональной даты окончания для участников групповых привычек
                db.execSQL("ALTER TABLE habit_participant_settings ADD COLUMN personalEndDate TEXT")
            }
        }
        
        // Миграция с версии 14 на 15 (рефакторинг дат Habit: Long -> LocalDate)
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite не поддерживает изменение типа колонки напрямую
                // Используем стратегию: создать новую таблицу, скопировать данные, удалить старую
                
                // 1. Создаем временную таблицу с новой схемой
                db.execSQL("""
                    CREATE TABLE habits_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        color TEXT NOT NULL,
                        type TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        reminderTime TEXT,
                        reminderEnabled INTEGER NOT NULL,
                        reminderDays TEXT NOT NULL,
                        targetValue INTEGER NOT NULL,
                        unit TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        maxParticipants INTEGER NOT NULL,
                        inviteCode TEXT,
                        isPublic INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        startDate TEXT,
                        endDate TEXT
                    )
                """)
                
                // 2. Копируем данные, конвертируя timestamp (Long) в LocalDate (String)
                // Используем datetime() для конвертации Unix timestamp в ISO 8601 дату
                db.execSQL("""
                    INSERT INTO habits_new 
                    SELECT 
                        id, title, description, icon, color, type, frequency,
                        reminderTime, reminderEnabled, reminderDays, targetValue, unit,
                        createdBy, participants, maxParticipants, inviteCode, isPublic, isActive,
                        date(createdAt / 1000, 'unixepoch') as createdAt,
                        date(updatedAt / 1000, 'unixepoch') as updatedAt,
                        startDate, endDate
                    FROM habits
                """)
                
                // 3. Удаляем старую таблицу
                db.execSQL("DROP TABLE habits")
                
                // 4. Переименовываем новую таблицу
                db.execSQL("ALTER TABLE habits_new RENAME TO habits")
            }
        }
        
        // Миграция с версии 15 на 16 (рефакторинг дат User: Long -> LocalDate)
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Аналогично Habit: создаем новую таблицу, копируем данные с конвертацией
                
                // 1. Создаем временную таблицу с новой схемой
                db.execSQL("""
                    CREATE TABLE users_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        avatarUrl TEXT,
                        avatarThumbnailUrl TEXT,
                        level INTEGER NOT NULL DEFAULT 1,
                        points INTEGER NOT NULL DEFAULT 0,
                        streak INTEGER NOT NULL DEFAULT 0,
                        longestStreak INTEGER NOT NULL DEFAULT 0,
                        friendsCount INTEGER NOT NULL DEFAULT 0,
                        fcmToken TEXT,
                        createdAt TEXT NOT NULL,
                        lastActiveAt TEXT NOT NULL,
                        timezone TEXT NOT NULL
                    )
                """)
                
                // 2. Копируем данные, конвертируя timestamp (Long) в LocalDate (String)
                db.execSQL("""
                    INSERT INTO users_new 
                    SELECT 
                        id, name, email, avatarUrl, avatarThumbnailUrl,
                        level, points, streak, longestStreak, friendsCount, fcmToken,
                        date(createdAt / 1000, 'unixepoch') as createdAt,
                        date(lastActiveAt / 1000, 'unixepoch') as lastActiveAt,
                        timezone
                    FROM users
                """)
                
                // 3. Удаляем старую таблицу
                db.execSQL("DROP TABLE users")
                
                // 4. Переименовываем новую таблицу
                db.execSQL("ALTER TABLE users_new RENAME TO users")
            }
        }
        
        // Миграция с версии 16 на 17 (рефакторинг дат HabitEntry: Long -> LocalDate/LocalDateTime)
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Создаем новую таблицу с обновленными типами полей
                
                // 1. Создаем временную таблицу
                db.execSQL("""
                    CREATE TABLE habit_entries_new (
                        habitId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        completed INTEGER NOT NULL,
                        completedAt TEXT,
                        value INTEGER NOT NULL,
                        note TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY (habitId, userId, date)
                    )
                """)
                
                // 2. Копируем данные с конвертацией
                // completedAt: Long -> LocalDateTime (ISO string)
                // createdAt, updatedAt: Long -> LocalDate (ISO string)
                db.execSQL("""
                    INSERT INTO habit_entries_new 
                    SELECT 
                        habitId, userId, date, completed,
                        CASE 
                            WHEN completedAt IS NOT NULL 
                            THEN datetime(completedAt / 1000, 'unixepoch')
                            ELSE NULL 
                        END as completedAt,
                        value, note,
                        date(createdAt / 1000, 'unixepoch') as createdAt,
                        date(updatedAt / 1000, 'unixepoch') as updatedAt
                    FROM habit_entries
                """)
                
                // 3. Удаляем старую таблицу
                db.execSQL("DROP TABLE habit_entries")
                
                // 4. Переименовываем новую таблицу
                db.execSQL("ALTER TABLE habit_entries_new RENAME TO habit_entries")
            }
        }
        
        // Миграция с версии 17 на 18 (рефакторинг дат Challenge: Long -> LocalDate)
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Создаем новую таблицу с обновленными типами полей
                
                // 1. Создаем временную таблицу
                db.execSQL("""
                    CREATE TABLE challenges_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        habitId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        rules TEXT NOT NULL,
                        reward TEXT NOT NULL,
                        status TEXT NOT NULL,
                        winnerId TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                """)
                
                // 2. Копируем данные с конвертацией
                // createdAt, updatedAt: Long -> LocalDate (ISO string)
                db.execSQL("""
                    INSERT INTO challenges_new 
                    SELECT 
                        id, habitId, title, description, createdBy, participants,
                        startDate, endDate, rules, reward, status, winnerId,
                        date(createdAt / 1000, 'unixepoch') as createdAt,
                        date(updatedAt / 1000, 'unixepoch') as updatedAt
                    FROM challenges
                """)
                
                // 3. Удаляем старую таблицу
                db.execSQL("DROP TABLE challenges")
                
                // 4. Переименовываем новую таблицу
                db.execSQL("ALTER TABLE challenges_new RENAME TO challenges")
            }
        }
        
        // Миграция с версии 18 на 19 (рефакторинг дат Friendship, FriendRequest, HabitInvitation)
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Friendships
                db.execSQL("""
                    CREATE TABLE friendships_new (
                        userId1 TEXT NOT NULL,
                        userId2 TEXT NOT NULL,
                        status TEXT NOT NULL,
                        requestedBy TEXT NOT NULL,
                        requestedAt TEXT NOT NULL,
                        acceptedAt TEXT,
                        blockedBy TEXT,
                        blockedAt TEXT,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY (userId1, userId2)
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO friendships_new 
                    SELECT 
                        userId1, userId2, status, requestedBy,
                        datetime(requestedAt / 1000, 'unixepoch') as requestedAt,
                        CASE WHEN acceptedAt IS NOT NULL 
                            THEN datetime(acceptedAt / 1000, 'unixepoch') 
                            ELSE NULL END as acceptedAt,
                        blockedBy,
                        CASE WHEN blockedAt IS NOT NULL 
                            THEN datetime(blockedAt / 1000, 'unixepoch') 
                            ELSE NULL END as blockedAt,
                        date(updatedAt / 1000, 'unixepoch') as updatedAt
                    FROM friendships
                """)
                
                db.execSQL("DROP TABLE friendships")
                db.execSQL("ALTER TABLE friendships_new RENAME TO friendships")
                
                // 2. Friend Requests
                db.execSQL("""
                    CREATE TABLE friend_requests_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        fromUserId TEXT NOT NULL,
                        toUserId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        message TEXT,
                        createdAt TEXT NOT NULL,
                        respondedAt TEXT,
                        updatedAt TEXT NOT NULL
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO friend_requests_new 
                    SELECT 
                        id, fromUserId, toUserId, status, message,
                        datetime(createdAt / 1000, 'unixepoch') as createdAt,
                        CASE WHEN respondedAt IS NOT NULL 
                            THEN datetime(respondedAt / 1000, 'unixepoch') 
                            ELSE NULL END as respondedAt,
                        date(updatedAt / 1000, 'unixepoch') as updatedAt
                    FROM friend_requests
                """)
                
                db.execSQL("DROP TABLE friend_requests")
                db.execSQL("ALTER TABLE friend_requests_new RENAME TO friend_requests")
                
                // 3. Habit Invitations
                db.execSQL("""
                    CREATE TABLE habit_invitations_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        habitId TEXT NOT NULL,
                        habitTitle TEXT NOT NULL,
                        fromUserId TEXT NOT NULL,
                        fromUserName TEXT NOT NULL,
                        toUserId TEXT NOT NULL,
                        message TEXT,
                        status TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        respondedAt TEXT,
                        expiresAt TEXT
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO habit_invitations_new 
                    SELECT 
                        id, habitId, habitTitle, fromUserId, fromUserName, toUserId, message, status,
                        datetime(createdAt / 1000, 'unixepoch') as createdAt,
                        CASE WHEN respondedAt IS NOT NULL 
                            THEN datetime(respondedAt / 1000, 'unixepoch') 
                            ELSE NULL END as respondedAt,
                        CASE WHEN expiresAt IS NOT NULL 
                            THEN datetime(expiresAt / 1000, 'unixepoch') 
                            ELSE NULL END as expiresAt
                    FROM habit_invitations
                """)
                
                db.execSQL("DROP TABLE habit_invitations")
                db.execSQL("ALTER TABLE habit_invitations_new RENAME TO habit_invitations")
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
