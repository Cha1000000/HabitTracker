package com.habittracker.data.local.entity.tuples

import androidx.room.Embedded
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.UserEntity

data class HabitEntryWithUserTuple(
    @Embedded
    val entry: HabitEntryEntity,

    @Embedded(prefix = "user_")
    val user: UserEntity
)
