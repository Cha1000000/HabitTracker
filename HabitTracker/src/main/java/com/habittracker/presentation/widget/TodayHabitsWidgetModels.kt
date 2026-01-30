package com.habittracker.presentation.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class TodayHabitWidgetItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val isGroup: Boolean,
    val currentValue: Int = 0,
    val targetValue: Int = 1,
    val unit: String = "",
    val type: String = "SIMPLE", // "SIMPLE" or "MEASURABLE"
    val timeOfDay: String = "" // "MORNING", "AFTERNOON", "EVENING", "ANY_TIME" or empty
) {
    companion object {
        val COMPARATOR = compareBy<TodayHabitWidgetItem> { it.isCompleted }
            .thenBy {
                when (it.timeOfDay) {
                    "MORNING" -> 0
                    "AFTERNOON" -> 1
                    "EVENING" -> 2
                    else -> 3
                }
            }

        fun toJson(items: List<TodayHabitWidgetItem>): String {
            return Json.encodeToString(ListSerializer(serializer()), items)
        }

        fun fromJson(json: String): List<TodayHabitWidgetItem> {
            return try {
                Json.decodeFromString(ListSerializer(serializer()), json)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
