package com.habittracker.domain.util

import com.google.gson.JsonParser
import com.habittracker.domain.model.HabitEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadsTest {

    @Test
    fun habitEntryToJson_incomplete_hasIsoDateAndNullsAndNumbers() {
        val date = LocalDate.parse("2025-09-03")
        val entry = HabitEntry(
            habitId = "h1",
            userId = "u1",
            date = date,
            completed = false,
            completedAt = null,
            value = 1,
            note = null,
            createdAt = LocalDate.parse("2025-09-01"),
            updatedAt = LocalDate.parse("2025-09-02")
        )

        val json = SyncPayloads.habitEntryToJson(entry)
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("h1", obj.get("habitId").asString)
        assertEquals("u1", obj.get("userId").asString)
        assertEquals("2025-09-03", obj.get("date").asString) // ISO
        assertTrue(!obj.get("completed").asBoolean)
        assertTrue(obj.get("completedAt").isJsonNull)
        // value -> number
        assertEquals(1, obj.get("value").asInt)
        // note -> null
        assertTrue(obj.get("note").isJsonNull)
        // timestamps -> ISO strings
        assertEquals("2025-09-01", obj.get("createdAt").asString)
        assertEquals("2025-09-02", obj.get("updatedAt").asString)
    }

    @Test
    fun habitEntryToJson_completed_hasIsoDateAndNumbersAndNote() {
        val date = LocalDate.parse("2025-09-04")
        val entry = HabitEntry(
            habitId = "h2",
            userId = "u2",
            date = date,
            completed = true,
            completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            value = 3,
            note = "Nice",
            createdAt = LocalDate.parse("2025-09-01"),
            updatedAt = LocalDate.parse("2025-09-02")
        )

        val json = SyncPayloads.habitEntryToJson(entry)
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("h2", obj.get("habitId").asString)
        assertEquals("u2", obj.get("userId").asString)
        assertEquals("2025-09-04", obj.get("date").asString)
        assertTrue(obj.get("completed").asBoolean)
        // completedAt is LocalDateTime - check it's not null and is a string
        assertTrue(!obj.get("completedAt").isJsonNull)
        assertTrue(obj.get("completedAt").asString.isNotEmpty())
        assertEquals(3, obj.get("value").asInt)
        assertEquals("Nice", obj.get("note").asString)
        assertEquals("2025-09-01", obj.get("createdAt").asString)
        assertEquals("2025-09-02", obj.get("updatedAt").asString)
    }
}
