package com.habittracker.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.habittracker.BuildConfig
import com.habittracker.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timber.log.Timber

import com.habittracker.data.analytics.CrashlyticsErrorLogger
import com.habittracker.data.analytics.FirebaseAnalyticsManager
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import dagger.Binds

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsManager(
        firebaseAnalyticsManager: FirebaseAnalyticsManager
    ): AnalyticsManager

    @Binds
    @Singleton
    abstract fun bindErrorLogger(
        crashlyticsErrorLogger: CrashlyticsErrorLogger
    ): ErrorLogger
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        // Resolve bucket from FirebaseApp options to support both appspot.com and firebasestorage.app
        val bucket = try { FirebaseApp.getInstance().options.storageBucket } catch (e: Exception) {
            Timber.tag("FirebaseModule").w(e, "Failed to read storageBucket from FirebaseApp options")
            null
        }
        Timber.tag("FirebaseModule").d("Firebase Storage bucket: $bucket")
        return if (!bucket.isNullOrBlank()) {
            FirebaseStorage.getInstance("gs://$bucket")
        } else {
            FirebaseStorage.getInstance()
        }
    }

    @Provides
    @Singleton
    fun provideGetGoogleIdOption(@ApplicationContext context: Context): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Allow sign-up for new users
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()
    }
}
