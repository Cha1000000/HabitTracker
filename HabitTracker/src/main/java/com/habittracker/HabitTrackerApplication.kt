package com.habittracker

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.appcheck.FirebaseAppCheck
import com.habittracker.data.service.AppStateManager
import com.habittracker.data.sync.ChallengeExpirationWorkScheduler
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.data.local.seed.AchievementSeeder
import com.habittracker.domain.usecase.notification.ManageFCMTokenUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HabitTrackerApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var manageFCMTokenUseCase: ManageFCMTokenUseCase

    @Inject
    lateinit var syncWorkScheduler: SyncWorkScheduler

    @Inject
    lateinit var challengeExpirationWorkScheduler: ChallengeExpirationWorkScheduler

    @Inject
    lateinit var firebaseAppCheck: FirebaseAppCheck
    
    @Inject
    lateinit var appStateManager: AppStateManager
    
    @Inject
    lateinit var achievementSeeder: AchievementSeeder
    
    // Application-level scope for non-blocking init tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d("HabitTrackerApp", "Application onCreate() called")
        }
        
        // Touch AppCheck to ensure debug provider is installed (see FirebaseModule)
        if (BuildConfig.DEBUG) {
            Log.d("HabitTrackerApp", "FirebaseAppCheck injected: ${::firebaseAppCheck.isInitialized}")
        }
        // Initialize FCM token once on app start (safe: use case checks auth internally)
        applicationScope.launch {
            manageFCMTokenUseCase.initializeFCMToken().onFailure { e ->
                if (BuildConfig.DEBUG) {
                    Log.d("HabitTrackerApp", "FCM init skipped/failed: ${e.message}")
                }
            }
        }
        
        // Seed achievements on first launch
        applicationScope.launch {
            try {
                achievementSeeder.seedIfNeeded()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d("HabitTrackerApp", "Achievement seeding failed: ${e.message}")
                }
            }
        }

        // Schedule Outbox sync: periodic + one-time on app start
        try {
            syncWorkScheduler.schedulePeriodicSync()
            syncWorkScheduler.scheduleOneTimeSync()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d("HabitTrackerApp", "Sync scheduling failed: ${e.message}")
            }
        }

        // Schedule close expired challenges: daily 00:30 + one-time on app start
        try {
            challengeExpirationWorkScheduler.scheduleDailyAt0030()
            challengeExpirationWorkScheduler.scheduleOneTimeNow()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d("HabitTrackerApp", "Challenge expiration scheduling failed: ${e.message}")
            }
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() {
            if (BuildConfig.DEBUG) {
                Log.d("HabitTrackerApp", "workManagerConfiguration called")
                Log.d("HabitTrackerApp", "HiltWorkerFactory initialized: ${::workerFactory.isInitialized}")
            }
            
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
                .build()
        }
}
