package com.habittracker.di

import com.habittracker.data.scheduler.DailyReminderSchedulerImpl
import com.habittracker.domain.scheduler.DailyReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {

    @Binds
    @Singleton
    abstract fun bindDailyReminderScheduler(
        dailyReminderSchedulerImpl: DailyReminderSchedulerImpl
    ): DailyReminderScheduler
}
