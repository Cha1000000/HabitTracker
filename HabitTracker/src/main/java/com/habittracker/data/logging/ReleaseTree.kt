package com.habittracker.data.logging

import com.habittracker.domain.util.ErrorLogger
import timber.log.Timber

class ReleaseTree(private val errorLogger: ErrorLogger) : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == 2 || priority == 3) {
            return
        }

        errorLogger.logNonFatal("Tag: $tag, Message: $message")

        if (t != null) {
            errorLogger.logError(t, message)
        }
    }
}
