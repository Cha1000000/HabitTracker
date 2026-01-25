package com.habittracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.appcheck.FirebaseAppCheck
import com.habittracker.data.service.AppStateManager
import com.habittracker.data.sync.ChallengeExpirationWorkScheduler
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.data.local.seed.AchievementSeeder
import com.habittracker.domain.usecase.notification.ManageFCMTokenUseCase
import com.habittracker.data.logging.ReleaseTree
import com.habittracker.domain.util.ErrorLogger
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import timber.log.Timber
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
    
    @Inject
    lateinit var errorLogger: ErrorLogger
    
    // Application-level scope for non-blocking init tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree(errorLogger))
        }

        // Initialize AppMetrica SDK
        initAppMetrica()

        if (BuildConfig.DEBUG) {
            Timber.tag("HabitTrackerApp").d("Application onCreate() called")
        }
        
        // Touch AppCheck to ensure debug provider is installed (see FirebaseModule)
        if (BuildConfig.DEBUG) {
            Timber.tag("HabitTrackerApp").d("FirebaseAppCheck injected: ${::firebaseAppCheck.isInitialized}")
        }
        // Initialize FCM token once on app start (safe: use case checks auth internally)
        applicationScope.launch {
            manageFCMTokenUseCase.initializeFCMToken().onFailure { e ->
                if (BuildConfig.DEBUG) {
                    Timber.tag("HabitTrackerApp").d("FCM init skipped/failed: ${e.message}")
                }
            }
        }
        
        // Seed achievements on first launch
        applicationScope.launch {
            try {
                achievementSeeder.seedIfNeeded()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Timber.tag("HabitTrackerApp").d("Achievement seeding failed: ${e.message}")
                }
            }
        }

        // Schedule Outbox sync: periodic + one-time on app start
        try {
            syncWorkScheduler.schedulePeriodicSync()
            syncWorkScheduler.scheduleOneTimeSync()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Timber.tag("HabitTrackerApp").d("Sync scheduling failed: ${e.message}")
            }
        }

        // Schedule close expired challenges: daily 00:30 + one-time on app start
        try {
            challengeExpirationWorkScheduler.scheduleDailyAt0030()
            challengeExpirationWorkScheduler.scheduleOneTimeNow()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Timber.tag("HabitTrackerApp").d("Challenge expiration scheduling failed: ${e.message}")
            }
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() {
            if (BuildConfig.DEBUG) {
                Timber.tag("HabitTrackerApp").d("workManagerConfiguration called")
                Timber.tag("HabitTrackerApp").d("HiltWorkerFactory initialized: ${::workerFactory.isInitialized}")
            }
            
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) 3 else 6)
                .build()
        }
    
    private fun initAppMetrica() {
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isBlank()) {
            Timber.tag("HabitTrackerApp").w("AppMetrica API key is not configured. Analytics disabled.")
            return
        }
        
        try {
            val config = AppMetricaConfig.newConfigBuilder(apiKey)
                .withLogs()
                .withSessionTimeout(300) // 5 minutes session timeout
                .withCrashReporting(true)
                .withAppVersion(BuildConfig.VERSION_NAME)
                .build()
            
            AppMetrica.activate(this, config)
            
            if (BuildConfig.DEBUG) {
                Timber.tag("HabitTrackerApp").d("AppMetrica SDK initialized successfully")
            }
        } catch (e: Exception) {
            Timber.tag("HabitTrackerApp").e(e, "Failed to initialize AppMetrica SDK")
        }
    }
}
