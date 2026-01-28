package com.habittracker.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeTest {

    @Test
    fun `isOnCooldown returns true when nudge was sent less than 1 hour ago`() {
        val nudge = Nudge(
            id = "test-id",
            habitId = "habit-id",
            senderId = "sender-id",
            receiverId = "receiver-id",
            sentAt = System.currentTimeMillis() - (30 * 60 * 1000) // 30 minutes ago
        )

        assertTrue(nudge.isOnCooldown())
    }

    @Test
    fun `isOnCooldown returns false when nudge was sent more than 1 hour ago`() {
        val nudge = Nudge(
            id = "test-id",
            habitId = "habit-id",
            senderId = "sender-id",
            receiverId = "receiver-id",
            sentAt = System.currentTimeMillis() - (61 * 60 * 1000) // 61 minutes ago
        )

        assertFalse(nudge.isOnCooldown())
    }

    @Test
    fun `isOnCooldown returns true when nudge was just sent`() {
        val nudge = Nudge(
            id = "test-id",
            habitId = "habit-id",
            senderId = "sender-id",
            receiverId = "receiver-id",
            sentAt = System.currentTimeMillis()
        )

        assertTrue(nudge.isOnCooldown())
    }

    @Test
    fun `isOnCooldown returns false when nudge was sent exactly 1 hour ago`() {
        val nudge = Nudge(
            id = "test-id",
            habitId = "habit-id",
            senderId = "sender-id",
            receiverId = "receiver-id",
            sentAt = System.currentTimeMillis() - Nudge.COOLDOWN_MILLIS
        )

        assertFalse(nudge.isOnCooldown())
    }

    @Test
    fun `COOLDOWN_MILLIS equals 1 hour in milliseconds`() {
        val oneHourInMillis = 60 * 60 * 1000L
        assertTrue(Nudge.COOLDOWN_MILLIS == oneHourInMillis)
    }
}
