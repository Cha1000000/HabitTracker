package com.habittracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HabitTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализация приложения
    }
}
