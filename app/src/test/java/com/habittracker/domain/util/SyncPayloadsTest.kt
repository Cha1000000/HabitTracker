package com.habittracker.domain.util

import com.google.gson.JsonParser
import com.habittracker.domain.model.HabitEntry
import kotlinx.datetime.LocalDate
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
            createdAt = 1000L,
            updatedAt = 2000L
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
        // timestamps -> numbers
        assertEquals(1000L, obj.get("createdAt").asLong)
        assertEquals(2000L, obj.get("updatedAt").asLong)
    }

    @Test
    fun habitEntryToJson_completed_hasIsoDateAndNumbersAndNote() {
        val date = LocalDate.parse("2025-09-04")
        val entry = HabitEntry(
            habitId = "h2",
            userId = "u2",
            date = date,
            completed = true,
            completedAt = 123456789L,
            value = 3,
            note = "Nice",
            createdAt = 111L,
            updatedAt = 222L
        )

        val json = SyncPayloads.habitEntryToJson(entry)
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("h2", obj.get("habitId").asString)
        assertEquals("u2", obj.get("userId").asString)
        assertEquals("2025-09-04", obj.get("date").asString)
        assertTrue(obj.get("completed").asBoolean)
        assertEquals(123456789L, obj.get("completedAt").asLong)
        assertEquals(3, obj.get("value").asInt)
        assertEquals("Nice", obj.get("note").asString)
        assertEquals(111L, obj.get("createdAt").asLong)
        assertEquals(222L, obj.get("updatedAt").asLong)
    }
}
