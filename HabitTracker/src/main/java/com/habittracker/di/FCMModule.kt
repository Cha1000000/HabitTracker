package com.habittracker.di

import android.content.Context
import com.habittracker.data.service.FCMApiClient
import com.habittracker.data.service.FCMTokenManager
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt модуль для Firebase Cloud Messaging
 */
@Module
@InstallIn(SingletonComponent::class)
object FCMModule {

    @Provides
    @Singleton
    fun provideFCMApiClient(): FCMApiClient {
        return FCMApiClient()
    }

    @Provides
    @Singleton
    fun provideFCMTokenManager(
        socialRepository: SocialRepository,
        authRepository: AuthRepository,
        @ApplicationContext context: Context
    ): FCMTokenManager {
        return FCMTokenManager(socialRepository, authRepository, context)
    }
}
