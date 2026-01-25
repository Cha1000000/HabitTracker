package com.habittracker.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.habittracker.domain.util.AnalyticsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.appmetrica.analytics.AppMetrica
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : AnalyticsManager {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun logEvent(name: String, params: Map<String, Any>) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
        
        // AppMetrica Analytics
        try {
            AppMetrica.reportEvent(name, params)
        } catch (e: Exception) {
            Timber.tag("AnalyticsManager").e(e, "Failed to report event to AppMetrica: $name")
        }
    }

    override fun setUserProperty(name: String, value: String) {
        firebaseAnalytics.setUserProperty(name, value)
    }

    override fun setUserId(userId: String?) {
        firebaseAnalytics.setUserId(userId)
        
        // AppMetrica user profile
        try {
            if (userId != null) {
                AppMetrica.setUserProfileID(userId)
            }
        } catch (e: Exception) {
            Timber.tag("AnalyticsManager").e(e, "Failed to set AppMetrica user profile ID")
        }
    }

    override fun logScreenView(screenName: String, screenClass: String?) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass ?: screenName)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        
        // AppMetrica Analytics
        try {
            AppMetrica.reportEvent("screen_view", mapOf(
                "screen_name" to screenName,
                "screen_class" to (screenClass ?: screenName)
            ))
        } catch (e: Exception) {
            Timber.tag("AnalyticsManager").e(e, "Failed to report screen view to AppMetrica: $screenName")
        }
    }
}
