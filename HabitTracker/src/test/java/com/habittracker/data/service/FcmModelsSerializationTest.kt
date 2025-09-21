package com.habittracker.data.service

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmModelsSerializationTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `data-only payload should not contain notification field`() {
        val msg = FCMV1Message(
            message = FCMV1MessageData(
                token = "abc123",
                notification = null,
                data = mapOf(
                    "type" to "friend_request",
                    "title" to "New friend request",
                    "body" to "User A wants to add you"
                ),
                android = AndroidConfig(priority = "high")
            )
        )

        val s = json.encodeToString(msg)
        assertFalse("JSON should not contain notification key", s.contains("\"notification\""))
        assertTrue(s.contains("\"priority\":\"high\""))
        assertTrue(s.contains("\"data\""))
        assertTrue(s.contains("\"token\":\"abc123\""))
    }

    @Test
    fun `topic token should be preserved and notification omitted`() {
        val msg = FCMV1Message(
            message = FCMV1MessageData(
                token = "/topics/demo",
                data = mapOf("type" to "test"),
                android = AndroidConfig(priority = "high", ttl = "3600s")
            )
        )

        val s = json.encodeToString(msg)
        assertTrue(s.contains("\"token\":\"/topics/demo\""))
        assertTrue(s.contains("\"ttl\":\"3600s\""))
        assertFalse(s.contains("\"notification\""))
    }
}
