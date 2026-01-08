package com.habittracker.domain.scheduler

import kotlinx.datetime.LocalTime

interface DailyReminderScheduler {
    fun schedule(time: LocalTime)
    fun scheduleIfNotSet(time: LocalTime)
    fun cancel()
}
