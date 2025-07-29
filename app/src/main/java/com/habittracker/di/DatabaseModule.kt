package com.habittracker.di

import android.content.Context
import androidx.room.Room
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.HabitDao
import com.habittracker.data.local.HabitEntryDao
import com.habittracker.data.local.UserDao
import com.habittracker.data.local.ChallengeDao
import com.habittracker.data.local.FriendshipDao
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
}
