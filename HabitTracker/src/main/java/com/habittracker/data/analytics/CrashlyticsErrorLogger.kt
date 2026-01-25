package com.habittracker.data.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.habittracker.domain.util.ErrorLogger
import io.appmetrica.analytics.AppMetrica
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashlyticsErrorLogger @Inject constructor() : ErrorLogger {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun logError(throwable: Throwable, message: String?) {
        // Firebase Crashlytics
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable)
        
        // AppMetrica Analytics - track error event
        try {
            AppMetrica.reportEvent("error_occurred", mapOf(
                "error_type" to (throwable::class.simpleName ?: "Unknown"),
                "error_message" to (message ?: throwable.message ?: "No message")
            ))
            AppMetrica.reportError(message ?: "Error", throwable)
        } catch (e: Exception) {
            Timber.tag("ErrorLogger").e(e, "Failed to report error to AppMetrica")
        }
    }

    override fun logNonFatal(message: String) {
        // Firebase Crashlytics
        crashlytics.log(message)
        
        // AppMetrica Analytics
        try {
            AppMetrica.reportEvent("non_fatal_error", mapOf(
                "message" to message
            ))
        } catch (e: Exception) {
            Timber.tag("ErrorLogger").e(e, "Failed to report non-fatal to AppMetrica")
        }
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    override fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
        
        // AppMetrica user profile
        try {
            AppMetrica.setUserProfileID(userId)
        } catch (e: Exception) {
            Timber.tag("ErrorLogger").e(e, "Failed to set AppMetrica user profile ID")
        }
    }
}
