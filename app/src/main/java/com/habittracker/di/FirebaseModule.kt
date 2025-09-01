package com.habittracker.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
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
        val bucket = try { FirebaseApp.getInstance().options.storageBucket } catch (_: Exception) { null }
        return if (!bucket.isNullOrBlank()) {
            FirebaseStorage.getInstance("gs://${'$'}bucket")
        } else {
            FirebaseStorage.getInstance()
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseAppCheck(@ApplicationContext context: Context): FirebaseAppCheck {
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            // В debug-сборках пытаемся подключить Debug App Check провайдер через рефлексию,
            // чтобы не держать compile-time зависимость в release.
            try {
                val factoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                val getInstance = factoryClass.getMethod("getInstance")
                val factory = getInstance.invoke(null)
                val providerIface = Class.forName("com.google.firebase.appcheck.AppCheckProviderFactory")
                val install = appCheck::class.java.getMethod("installAppCheckProviderFactory", providerIface)
                install.invoke(appCheck, factory)
            } catch (_: Throwable) {
                // no-op in case debug artifact is absent
            }
        }
        return appCheck
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
