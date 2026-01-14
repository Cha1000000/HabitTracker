package com.habittracker.data.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.habittracker.domain.util.ErrorLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashlyticsErrorLogger @Inject constructor() : ErrorLogger {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun logError(throwable: Throwable, message: String?) {
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable)
    }

    override fun logNonFatal(message: String) {
        crashlytics.log(message)
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    override fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }
}
