package com.habittracker.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitTimeOfDay
import com.habittracker.domain.model.HabitType
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class Converters {
    private val gson = Gson()

    // Instant converters для NotificationReadStatusEntity
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(milliseconds: Long?): Instant? = milliseconds?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun fromLocalDateString(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun fromLocalTimeString(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun localTimeToString(time: LocalTime?): String? = time?.toString()

    @TypeConverter
    fun fromLocalDateTimeString(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }

    @TypeConverter
    fun localDateTimeToString(dateTime: LocalDateTime?): String? = dateTime?.toString()

    // List<String> converters (для participants)
    @TypeConverter
    fun fromStringListToJson(list: List<String>?): String? =
        list?.let { gson.toJson(it) }

    @TypeConverter
    fun fromJsonToStringList(json: String?): List<String> =
        json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(it, type)
        } ?: emptyList()

    // List<Int> converters (для reminderDays)
    @TypeConverter
    fun fromIntListToJson(list: List<Int>?): String? =
        list?.let { gson.toJson(it) }

    @TypeConverter
    fun fromJsonToIntList(json: String?): List<Int> =
        json?.let {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(it, type)
        } ?: emptyList()

    // HabitType converters
    @TypeConverter
    fun fromHabitType(type: HabitType): String = type.name

    @TypeConverter
    fun toHabitType(typeName: String): HabitType =
        HabitType.valueOf(typeName)

    // HabitFrequency converters
    @TypeConverter
    fun fromHabitFrequency(frequency: HabitFrequency): String = frequency.name

    @TypeConverter
    fun toHabitFrequency(frequencyName: String): HabitFrequency =
        HabitFrequency.valueOf(frequencyName)

    // HabitTimeOfDay converters
    @TypeConverter
    fun fromHabitTimeOfDay(timeOfDay: HabitTimeOfDay): String = timeOfDay.name

    @TypeConverter
    fun toHabitTimeOfDay(timeOfDayName: String): HabitTimeOfDay =
        try {
            HabitTimeOfDay.valueOf(timeOfDayName)
        } catch (_: IllegalArgumentException) {
            HabitTimeOfDay.ANY_TIME
        }

    // HabitGoalType converters
    @TypeConverter
    fun fromHabitGoalType(type: com.habittracker.domain.model.HabitGoalType): String = type.name

    @TypeConverter
    fun toHabitGoalType(typeName: String): com.habittracker.domain.model.HabitGoalType =
        try {
            com.habittracker.domain.model.HabitGoalType.valueOf(typeName)
        } catch (_: IllegalArgumentException) {
            com.habittracker.domain.model.HabitGoalType.SIMPLE
        }
}
