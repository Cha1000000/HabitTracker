package com.habittracker.domain.util

/**
 * Служебные поля профиля для серверного поиска пользователей в Firestore.
 *
 * Firestore не умеет искать подстроку, поэтому храним заранее посчитанные ключи:
 * - [FIELD_TOKENS] — префиксы каждого слова имени (поиск через `array-contains`);
 * - [FIELD_EMAIL_LOWER] — email в нижнем регистре (точное совпадение).
 */
object UserSearchKeys {
    const val FIELD_NAME_LOWER = "nameLower"
    const val FIELD_EMAIL_LOWER = "emailLower"
    const val FIELD_TOKENS = "searchTokens"

    /** Длиннее этого префиксы не храним — хватает для поиска и не раздувает документ. */
    const val MAX_TOKEN_LENGTH = 20

    private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(value: String): String = value.trim().lowercase()

    /** Слова строки в нижнем регистре; пунктуация и пробелы — разделители. */
    fun words(value: String): List<String> =
        normalize(value).split(wordSeparators).filter { it.isNotEmpty() }

    /** Все префиксы (1..[MAX_TOKEN_LENGTH]) каждого слова имени, без дублей. */
    fun tokens(name: String): List<String> =
        words(name)
            .flatMap { word -> (1..minOf(word.length, MAX_TOKEN_LENGTH)).map { word.take(it) } }
            .distinct()

    /** Поля для записи в документ `users/{uid}`. */
    fun fields(name: String, email: String): Map<String, Any> = mapOf(
        FIELD_NAME_LOWER to normalize(name),
        FIELD_EMAIL_LOWER to normalize(email),
        FIELD_TOKENS to tokens(name)
    )

    /**
     * Проверяет, что пользователь подходит под запрос: каждое слово запроса — начало какого-то
     * слова имени, либо запрос целиком совпадает с email.
     */
    fun matches(query: String, name: String, email: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return false
        if (normalize(email) == q) return true
        val nameWords = words(name)
        val queryWords = words(q)
        return queryWords.isNotEmpty() && queryWords.all { qw -> nameWords.any { it.startsWith(qw) } }
    }
}
