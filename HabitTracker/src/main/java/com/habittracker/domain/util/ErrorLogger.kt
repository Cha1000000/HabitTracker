package com.habittracker.domain.util

interface ErrorLogger {
    fun logError(throwable: Throwable, message: String? = null)
    fun logNonFatal(message: String)
    fun setCustomKey(key: String, value: String)
    fun setUserId(userId: String)
}
