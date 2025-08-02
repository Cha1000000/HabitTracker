package com.habittracker

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HabitTrackerApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        Log.d("HabitTrackerApp", "Application onCreate() called")
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
