package com.habittracker.data.local.entity.tuples

import androidx.room.ColumnInfo
import kotlinx.datetime.LocalDate

data class DailyCompletionCountTuple(
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "count") val count: Int
)
