package com.habittracker.data.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.repository.AuthRepository

object PendingNotificationManager {
    private const val TAG = "PendingNotifMgr"

    suspend fun deliverForCurrentUser(context: Context, authRepository: AuthRepository) {
        val userId = try { authRepository.getCurrentUserId() } catch (e: Exception) {
            Log.w(TAG, "getCurrentUserId() failed in PendingNotificationManager", e)
            null
        } ?: return
        val dao = HabitTrackerDatabase.getDatabase(context).pendingNotificationDao()
        dao.deleteExpired(System.currentTimeMillis())
        val items = dao.getAllForUser(userId)
        if (items.isEmpty()) return
        Log.d(TAG, "Deliver pending notifications: ${items.size} for user=$userId")
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type
        items.forEach { item ->
            val data: Map<String, String> = runCatching { gson.fromJson<Map<String, String>>(item.dataJson, type) }.getOrElse { emptyMap() }
            NotificationHelper.showNotification(context, item.title, item.body, data)
            runCatching { dao.deleteById(item.id) }
                .onFailure { e -> Log.w(TAG, "Failed to delete pending notification id=${item.id}", e) }
        }
    }
}
