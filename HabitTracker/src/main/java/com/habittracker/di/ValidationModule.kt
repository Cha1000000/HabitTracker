package com.habittracker.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ValidationModule {
    // HabitValidation - это object с статическими методами, не требует DI
}
