package com.habittracker.data.analytics

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.ErrorLogger
import io.appmetrica.analytics.AppMetrica
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashlyticsErrorLogger @Inject constructor() : ErrorLogger {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    private fun isBenignThrowable(throwable: Throwable): Boolean {
        if (throwable is CancellationException) return true
        if (throwable is SocketTimeoutException || throwable is UnknownHostException || throwable is IOException) {
            return true
        }
        if (throwable is FirebaseFirestoreException) {
            return when (throwable.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.CANCELLED,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> true
                else -> false
            }
        }
        val className = throwable::class.qualifiedName.orEmpty()
        return className.contains("JobCancellationException") ||
            className.contains("ChildCancelledException") ||
            className.contains("GaiException")
    }

    override fun logError(throwable: Throwable, message: String?) {
        if (isBenignThrowable(throwable)) {
            Timber.tag("ErrorLogger").d("Skip benign throwable: ${throwable::class.simpleName}")
            return
        }
        // Firebase Crashlytics
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable)
        
        // AppMetrica Analytics - track error event
        try {
            AppMetrica.reportEvent(AnalyticsEvents.ERROR_OCCURRED, mapOf(
                AnalyticsEvents.Params.ERROR_TYPE to (throwable::class.simpleName ?: "Unknown"),
                AnalyticsEvents.Params.ERROR_MESSAGE to (message ?: throwable.message ?: "No message")
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
                AnalyticsEvents.Params.ERROR_MESSAGE to message
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
