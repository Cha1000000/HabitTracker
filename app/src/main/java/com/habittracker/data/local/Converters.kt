package com.habittracker.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class Converters {
    private val gson = Gson()

    // Удалены TypeConverter методы для Instant, так как HabitEntity теперь использует Long напрямую

    @TypeConverter
    fun fromLocalDateString(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun fromLocalTimeString(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun localTimeToString(time: LocalTime?): String? = time?.toString()

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

    // ChallengeRules converters
    @TypeConverter
    fun toChallengeRules(value: String?): ChallengeRules? {
        return value?.let { gson.fromJson(it, ChallengeRules::class.java) }
    }

    @TypeConverter
    fun fromChallengeRules(rules: ChallengeRules?): String? {
        return gson.toJson(rules)
    }

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
}
