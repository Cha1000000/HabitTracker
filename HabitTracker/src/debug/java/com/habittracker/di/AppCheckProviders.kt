package com.habittracker.di

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-сборки (эмулятор, отладка) Play Integrity не пройдут. Debug-провайдер при первом запуске
 * печатает в logcat секрет (тег `DebugAppCheckProvider`) — его добавляют в консоли Firebase:
 * App Check → приложение → Manage debug tokens.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
