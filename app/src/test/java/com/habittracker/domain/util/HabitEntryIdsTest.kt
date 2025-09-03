package com.habittracker.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.datetime.LocalDate

class HabitEntryIdsTest {

    @Test
    fun make_builds_expected_string() {
        val date = LocalDate.parse("2025-09-03")
        val id = HabitEntryIds.make("h1", "u1", date)
        assertEquals("h1_u1_2025-09-03", id)
    }

    @Test
    fun make_with_empty_parts_allows_empty() {
        val date = LocalDate.parse("2025-01-01")
        val id = HabitEntryIds.make("", "", date)
        assertEquals("__2025-01-01", id)
    }
}
