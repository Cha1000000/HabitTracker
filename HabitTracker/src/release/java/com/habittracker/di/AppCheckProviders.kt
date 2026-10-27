package com.habittracker.di

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Приложение распространяется только через RuStore: в консоли App Check для Play Integrity выбран
 * режим «Exclusively outside Google Play» — проверяется целостность устройства, а не установка из Play.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
