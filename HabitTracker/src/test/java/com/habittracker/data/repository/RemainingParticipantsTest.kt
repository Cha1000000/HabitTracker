package com.habittracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemainingParticipantsTest {

    @Test
    fun excludes_deleted_user_and_keeps_order() {
        assertEquals(listOf("b", "c"), remainingParticipants(listOf("me", "b", "me", "c", "b"), "me"))
    }

    @Test
    fun owner_listed_in_participants_does_not_become_next_owner() {
        // Создатель обычно стоит первым в participants — новым владельцем должен стать другой
        val next = remainingParticipants(listOf("me", "friend"), "me").first()
        assertEquals("friend", next)
    }

    @Test
    fun missing_or_malformed_field_gives_empty_list() {
        assertTrue(remainingParticipants(null, "me").isEmpty())
        assertTrue(remainingParticipants("me", "me").isEmpty())
        assertTrue(remainingParticipants(listOf("me", 42), "me").isEmpty())
    }
}
