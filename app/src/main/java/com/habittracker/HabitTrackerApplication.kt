package com.habittracker

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
    
    // Application-level scope for non-blocking init tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        Log.d("HabitTrackerApp", "Application onCreate() called")
        // Initialize FCM token once on app start (safe: use case checks auth internally)
        applicationScope.launch {
            manageFCMTokenUseCase.initializeFCMToken().onFailure { e ->
                Log.d("HabitTrackerApp", "FCM init skipped/failed: ${'$'}{e.message}")
            }
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() {
            Log.d("HabitTrackerApp", "workManagerConfiguration called")
            Log.d("HabitTrackerApp", "HiltWorkerFactory initialized: ${::workerFactory.isInitialized}")
            
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
        }
}
