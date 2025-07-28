package com.habittracker.di

import android.content.Context
import com.google.gson.Gson
import com.habittracker.data.local.ChallengeDao
import com.habittracker.data.local.FriendshipDao
import com.habittracker.data.local.HabitDao
import com.habittracker.data.local.HabitEntryDao
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.UserDao
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
    @Singleton
    fun provideHabitEntryDao(database: HabitTrackerDatabase): HabitEntryDao = database.habitEntryDao()

    @Provides
    @Singleton
    fun provideChallengeDao(database: HabitTrackerDatabase): ChallengeDao = database.challengeDao()

    @Provides
    @Singleton
    fun provideFriendshipDao(database: HabitTrackerDatabase): FriendshipDao = database.friendshipDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
