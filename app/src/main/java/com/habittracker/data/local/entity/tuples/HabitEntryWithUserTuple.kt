package com.habittracker.data.local.entity.tuples

import androidx.room.Embedded
import androidx.room.Relation
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.UserEntity

data class HabitEntryWithUserTuple(
    @Embedded
    val entry: HabitEntryEntity,

    @Relation(
        parentColumn = "userId",
        entityColumn = "id"
    )
    val user: UserEntity
)
