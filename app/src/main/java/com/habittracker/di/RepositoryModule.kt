package com.habittracker.di

import com.habittracker.data.repository.*
import com.habittracker.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindHabitRepository(
        habitRepositoryImpl: HabitRepositoryImpl
    ): HabitRepository

    @Binds
    @Singleton
    abstract fun bindHabitEntryRepository(
        habitEntryRepositoryImpl: HabitEntryRepositoryImpl
    ): HabitEntryRepository

    @Binds
    @Singleton
    abstract fun bindChallengeRepository(
        challengeRepositoryImpl: ChallengeRepositoryImpl
    ): ChallengeRepository

    @Binds
    @Singleton
    abstract fun bindFriendshipRepository(
        friendshipRepositoryImpl: FriendshipRepositoryImpl
    ): FriendshipRepository
}
