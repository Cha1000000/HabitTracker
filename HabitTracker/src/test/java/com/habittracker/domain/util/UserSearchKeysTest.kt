package com.habittracker.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSearchKeysTest {

    @Test
    fun tokens_contain_prefixes_of_every_word() {
        val tokens = UserSearchKeys.tokens("Иван Петров")
        assertEquals(listOf("и", "ив", "ива", "иван", "п", "пе", "пет", "петр", "петро", "петров"), tokens)
    }

    @Test
    fun tokens_split_by_punctuation_and_ignore_case() {
        val tokens = UserSearchKeys.tokens("  Anna-Maria O'Neil ")
        assertTrue("anna" in tokens)
        assertTrue("maria" in tokens)
        assertTrue("o" in tokens)
        assertTrue("neil" in tokens)
        assertTrue(tokens.none { it.any(Char::isUpperCase) })
    }

    @Test
    fun tokens_are_distinct_and_capped() {
        val tokens = UserSearchKeys.tokens("aa aa " + "x".repeat(30))
        assertEquals(tokens.distinct(), tokens)
        assertEquals(UserSearchKeys.MAX_TOKEN_LENGTH, tokens.maxOf { it.length })
    }

    @Test
    fun tokens_of_blank_name_are_empty() {
        assertTrue(UserSearchKeys.tokens("   ").isEmpty())
    }

    @Test
    fun fields_normalize_email_and_name() {
        val fields = UserSearchKeys.fields(" Иван ", " Ivan@Gmail.COM ")
        assertEquals("иван", fields[UserSearchKeys.FIELD_NAME_LOWER])
        assertEquals("ivan@gmail.com", fields[UserSearchKeys.FIELD_EMAIL_LOWER])
    }

    @Test
    fun matches_by_any_word_prefix_and_exact_email() {
        assertTrue(UserSearchKeys.matches("пет", "Иван Петров", "ivan@gmail.com"))
        assertTrue(UserSearchKeys.matches("Ив Пе", "Иван Петров", "ivan@gmail.com"))
        assertTrue(UserSearchKeys.matches("IVAN@gmail.com", "Иван Петров", "ivan@gmail.com"))
        assertFalse(UserSearchKeys.matches("ван", "Иван Петров", "ivan@gmail.com"))
        assertFalse(UserSearchKeys.matches("ivan@", "Иван Петров", "ivan@gmail.com"))
        assertFalse(UserSearchKeys.matches(" ", "Иван Петров", "ivan@gmail.com"))
    }
}
