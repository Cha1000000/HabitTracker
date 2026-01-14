package com.habittracker.domain.util

interface AnalyticsManager {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun setUserProperty(name: String, value: String)
    fun setUserId(userId: String?)
    fun logScreenView(screenName: String, screenClass: String? = null)
}

object AnalyticsEvents {
    const val HABIT_CREATED = "habit_created"
    const val HABIT_COMPLETED = "habit_completed"
    const val HABIT_DELETED = "habit_deleted"
    const val CHALLENGE_JOINED = "challenge_joined"
    const val CHALLENGE_COMPLETED = "challenge_completed"
    const val FRIEND_REQUEST_SENT = "friend_request_sent"
    const val ACHIEVEMENT_UNLOCKED = "achievement_unlocked"
    
    // Auth events
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val LOGOUT = "logout"
    
    // Social events
    const val CHALLENGE_CREATED = "challenge_created"
    
    object Params {
        const val HABIT_ID = "habit_id"
        const val HABIT_TYPE = "habit_type"
        const val CHALLENGE_ID = "challenge_id"
        const val ACHIEVEMENT_ID = "achievement_id"
        const val USER_ID = "user_id"
    }
}
