package com.habittracker.data.service

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FCM HTTP v1 models used to build request payloads in a type-safe way.
 *
 * We use data-only messages (no platform-level notification) so that
 * all clicks are handled by our FirebaseMessagingService and can navigate
 * with custom extras (e.g. navigate_to, habit_id).
 *
 * See: https://firebase.google.com/docs/cloud-messaging/send-message#http_v1
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class FCMV1Message(
    val message: FCMV1MessageData
)

/**
 * Inner payload for FCM HTTP v1.
 *
 * - token: destination. For topics, pass "/topics/<topic>" instead of a token
 * - notification: optional legacy notification payload (we keep it null by default)
 * - data: app-defined key/value pairs (type, title, body, navigate_to, habit_id, ...)
 * - android: Android specific config (priority/ttl)
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class FCMV1MessageData(
    val token: String,
    // Optional notification for backward compatibility; data-only by default
    val notification: FCMNotification? = null,
    val data: Map<String, String>,
    val android: AndroidConfig
)

/**
 * Android-specific delivery parameters.
 *
 * - priority: usually "high" for time-sensitive notifications
 * - ttl: time-to-live (e.g., "3600s"); omitted when not needed
 * - notification: optional AndroidNotification; we keep it null for data-only
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AndroidConfig(
    val priority: String,
    val ttl: String? = null,
    val notification: AndroidNotification? = null
)

/**
 * Android-level visual options if we ever decide to send non data-only messages.
 *
 * Note: clickAction is NOT supported in FCM HTTP v1; navigation must be
 * implemented on the client by handling data payload in FirebaseMessagingService.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AndroidNotification(
    val sound: String = "default",
    @SerialName("channel_id") val channelId: String? = null
)

/**
 * Legacy FCM Notification payload. Not used in our current data-only strategy,
 * but kept for potential hybrid scenarios.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class FCMNotification(
    val title: String,
    val body: String
)

/**
 * Result of a batch send operation (one HTTP request per token under the hood).
 */
data class BatchResult(
    val totalSent: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<SingleResult> = emptyList()
)

/**
 * Per-token delivery result within a batch.
 */
data class SingleResult(
    val token: String,
    val success: Boolean,
    val error: String? = null
)
