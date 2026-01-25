package com.habittracker.domain.util

interface AnalyticsManager {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun setUserProperty(name: String, value: String)
    fun setUserId(userId: String?)
    fun logScreenView(screenName: String, screenClass: String? = null)
}

object AnalyticsEvents {
    // Habit events
    const val HABIT_CREATED = "habit_created"
    const val HABIT_COMPLETED = "habit_completed"
    const val HABIT_DELETED = "habit_deleted"
    const val GROUP_HABIT_JOINED = "group_habit_joined"
    
    // Challenge events
    const val CHALLENGE_CREATED = "challenge_created"
    const val CHALLENGE_JOINED = "challenge_joined"
    const val CHALLENGE_COMPLETED = "challenge_completed"
    
    // Social events
    const val FRIEND_REQUEST_SENT = "friend_request_sent"
    const val FRIEND_REQUEST_ACCEPTED = "friend_request_accepted"
    
    // Achievement events
    const val ACHIEVEMENT_UNLOCKED = "achievement_unlocked"
    
    // Auth events
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val LOGOUT = "logout"
    
    // Screen events
    const val SCREEN_VIEW = "screen_view"
    
    // Statistics events
    const val PARTICIPANT_CALENDAR_EXPANDED = "participant_calendar_expanded"
    
    // Error events
    const val ERROR_OCCURRED = "error_occurred"
    
    object Params {
        const val HABIT_ID = "habit_id"
        const val HABIT_TYPE = "habit_type"
        const val CHALLENGE_ID = "challenge_id"
        const val ACHIEVEMENT_ID = "achievement_id"
        const val USER_ID = "user_id"
        const val SCREEN_NAME = "screen_name"
        const val PARTICIPANT_ID = "participant_id"
        const val ERROR_TYPE = "error_type"
        const val ERROR_MESSAGE = "error_message"
    }
    
    object Screens {
        const val HABITS = "habits"
        const val ADD_HABIT = "add_habit"
        const val EDIT_HABIT = "edit_habit"
        const val HABIT_STATISTICS = "habit_statistics"
        const val GROUP_HABIT_STATISTICS = "group_habit_statistics"
        const val GROUP_HABIT_DETAIL = "group_habit_detail"
        const val CHALLENGES = "challenges"
        const val CHALLENGE_DETAIL = "challenge_detail"
        const val CREATE_CHALLENGE = "create_challenge"
        const val FRIENDS = "friends"
        const val PROFILE = "profile"
        const val ACHIEVEMENTS = "achievements"
        const val NOTIFICATIONS = "notifications"
        const val PUBLIC_HABITS = "public_habits"
        const val SETTINGS = "settings"
        const val AUTH = "auth"
    }
}
