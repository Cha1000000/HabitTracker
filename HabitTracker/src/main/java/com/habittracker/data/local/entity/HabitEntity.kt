package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
    val type: HabitType,
    val frequency: HabitFrequency,
    val reminderTime: LocalTime?,
    val reminderEnabled: Boolean,
    val reminderDays: List<Int> = emptyList(),
    val targetValue: Int,
    val unit: String,
    val createdBy: String,
    val participants: List<String>,
    val maxParticipants: Int,
    val inviteCode: String?,
    val isPublic: Boolean,
    val isActive: Boolean,
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
