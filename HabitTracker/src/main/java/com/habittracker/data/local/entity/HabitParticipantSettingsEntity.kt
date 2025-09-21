package com.habittracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalTime

@Entity(
    tableName = "habit_participant_settings",
    indices = [Index(value = ["habitId", "userId"], name = "index_hps_habit_user")]
)
data class HabitParticipantSettingsEntity(
    @PrimaryKey(autoGenerate = false)
    val key: String, // composite key: "habitId_userId"
    val habitId: String,
    val userId: String,
    val reminderTime: LocalTime?,
    val reminderEnabled: Boolean,
    @ColumnInfo(defaultValue = "'[]'")
    val reminderDays: List<Int> = emptyList(),
    @ColumnInfo(defaultValue = "0")
    val notificationsMuted: Boolean = false,
    val timeZone: String? = null,
    val updatedAt: Long
)
