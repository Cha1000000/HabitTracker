package com.habittracker.di

import android.content.Context
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.dao.AchievementDao
import com.habittracker.data.local.dao.ChallengeDao
import com.habittracker.data.local.dao.FriendRequestDao
import com.habittracker.data.local.dao.FriendshipDao
import com.habittracker.data.local.dao.HabitDao
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.dao.HabitInvitationDao
import com.habittracker.data.local.dao.UserAchievementDao
import com.habittracker.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HabitTrackerDatabase {
        return HabitTrackerDatabase.getDatabase(context)
    }

    @Provides
    fun provideUserDao(database: HabitTrackerDatabase): UserDao = database.userDao()

    @Provides
    fun provideHabitDao(database: HabitTrackerDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitEntryDao(database: HabitTrackerDatabase): HabitEntryDao = database.habitEntryDao()

    @Provides
    fun provideChallengeDao(database: HabitTrackerDatabase): ChallengeDao = database.challengeDao()

    @Provides
    fun provideFriendshipDao(database: HabitTrackerDatabase): FriendshipDao = database.friendshipDao()

    @Provides
    fun provideFriendRequestDao(database: HabitTrackerDatabase): FriendRequestDao = database.friendRequestDao()

    @Provides
    fun provideHabitInvitationDao(database: HabitTrackerDatabase): HabitInvitationDao = database.habitInvitationDao()

    @Provides
    fun provideAchievementDao(database: HabitTrackerDatabase): AchievementDao = database.achievementDao()

    @Provides
    fun provideUserAchievementDao(database: HabitTrackerDatabase): UserAchievementDao = database.userAchievementDao()
}
