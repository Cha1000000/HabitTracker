package com.habittracker.di

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.repository.FirestoreRepositoryImpl
import com.habittracker.domain.repository.FirestoreRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt модуль для предоставления зависимостей Firebase Firestore.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirestoreModule {

    @Binds
    @Singleton
    abstract fun bindFirestoreRepository(
        firestoreRepositoryImpl: FirestoreRepositoryImpl
    ): FirestoreRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance()
        }
    }
}
