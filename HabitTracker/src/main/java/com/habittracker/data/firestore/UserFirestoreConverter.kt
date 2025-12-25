package com.habittracker.data.firestore

import android.util.Log
import com.habittracker.domain.model.User
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Firestore-конвертер для User модели.
 * 
 * Обеспечивает:
 * - Типобезопасную сериализацию/десериализацию
 * - Поддержку legacy документов (Instant → Long conversion)
 * - Fallback для поврежденных данных
 * - Централизованную логику работы с Firestore
 */
object UserFirestoreConverter {
    
    /**
     * Конвертирует User в Map для сохранения в Firestore
     */
    fun toFirestore(user: User): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "id" to user.id,
            "name" to user.name,
            "email" to user.email,
            "avatarUrl" to (user.avatarUrl ?: ""),
            // thumbnail хранится отдельно, пустые строки не сохраняем
            // добавим позже условно, чтобы не записывать пустое значение
            "level" to user.level,
            "points" to user.points,
            "streak" to user.streak,
            "longestStreak" to user.longestStreak,
            "friendsCount" to user.friendsCount,
            "createdAt" to user.createdAt.toString(), // LocalDate as ISO string
            "lastActiveAt" to user.lastActiveAt, // Long timestamp для точного онлайн-статуса
            "timezone" to (user.timezone ?: "")
        )
        user.avatarThumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumb ->
            base["avatarThumbnailUrl"] = thumb
        }
        // Backward compatibility: сохраняем старое поле, но избегаем пустой строки
        user.fcmToken?.takeIf { it.isNotBlank() }?.let { token ->
            base["fcmToken"] = token
        }
        return base
    }
    
    /**
     * Конвертирует Firestore документ в User модель
     * с поддержкой legacy форматов и fallback для поврежденных данных
     */
    fun fromFirestore(data: Map<String, Any>, documentId: String): User? {
        return try {
            val tokens = extractStringList(data, key = "fcmTokens")
            val singleToken = extractString(data, "fcmToken")
            val resolvedToken = when {
                !tokens.isNullOrEmpty() -> tokens.firstOrNull()
                !singleToken.isNullOrBlank() -> singleToken
                else -> null
            }
            User(
                id = documentId,
                name = extractString(data, "name") ?: "",
                email = extractString(data, "email") ?: "",
                avatarUrl = extractString(data, "avatarUrl"),
                avatarThumbnailUrl = extractString(data, "avatarThumbnailUrl"),
                level = extractInt(data, "level") ?: 1,
                points = extractInt(data, "points") ?: 0,
                streak = extractInt(data, "streak") ?: 0,
                longestStreak = extractInt(data, "longestStreak") ?: 0,
                friendsCount = extractInt(data, "friendsCount") ?: 0,
                fcmToken = resolvedToken,
                createdAt = extractDate(data, "createdAt") ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                lastActiveAt = extractTimestamp(data, "lastActiveAt") ?: Clock.System.now().toEpochMilliseconds(),
                timezone = extractString(data, "timezone")
            )
        } catch (e: Exception) {
            Log.e("UserFirestoreConverter", "❌ Failed to convert document $documentId: ${e.message}")
            null
        }
    }
    
    /**
     * Извлекает строку из Map с null-safety
     */
    private fun extractString(data: Map<String, Any>, key: String): String? {
        return when (val value = data[key]) {
            is String -> value.ifBlank { null }
            null -> null
            else -> value.toString().takeIf { it.isNotBlank() }
        }
    }
    
    /**
     * Извлекает число из Map с поддержкой разных типов (Long, Int, Double)
     */
    private fun extractInt(data: Map<String, Any>, key: String): Int? {
        return when (val value = data[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            null -> null
            else -> null
        }
    }

    /**
     * Извлекает список строк из произвольного массива/коллекции.
     */
    private fun extractStringList(data: Map<String, Any>, key: String): List<String>? {
        return when (val value = data[key]) {
            is List<*> -> value.mapNotNull {
                when (it) {
                    is String -> it
                    is Number -> it.toString()
                    else -> null
                }
            }
            null -> null
            else -> null
        }
    }
    
    /**
     * Извлекает timestamp (Long) с поддержкой различных форматов:
     * - Long (прямой timestamp)
     * - Number (конвертируем в Long)
     * - String (ISO дата - конвертируем в timestamp начала дня)
     */
    private fun extractTimestamp(data: Map<String, Any>, key: String): Long? {
        return when (val value = data[key]) {
            is Long -> value
            is Number -> value.toLong()
            is String -> {
                // Пробуем распарсить как ISO дату и конвертировать в timestamp
                try {
                    LocalDate.parse(value)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                } catch (_: Exception) {
                    value.toLongOrNull()
                }
            }
            null -> null
            else -> null
        }
    }

    /**
     * Извлекает LocalDate с поддержкой различных форматов:
     * - String (ISO формат: "2025-10-11")
     * - Long (legacy timestamp - конвертируем в LocalDate)
     * - Map (legacy Instant формат)
     */
    private fun extractDate(data: Map<String, Any>, key: String): LocalDate? {
        return when (val value = data[key]) {
            // Текущий формат: ISO строка
            is String -> {
                try {
                    LocalDate.parse(value)
                } catch (_: Exception) {
                    // Fallback: может быть timestamp как строка
                    value.toLongOrNull()?.let { timestamp ->
                        fromEpochMilliseconds(timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }
                }
            }
            
            // Legacy формат: Long timestamp
            is Long -> {
                fromEpochMilliseconds(value)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            is Number -> {
                fromEpochMilliseconds(value.toLong())
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            
            // Legacy формат: сериализованный Instant как Map
            is Map<*, *> -> {
                try {
                    val seconds = (value["seconds"] as? Number)?.toLong()
                    val nanos = (value["nanos"] as? Number)?.toLong() ?: 0L
                    
                    if (seconds != null) {
                        val millis = seconds * 1000 + nanos / 1_000_000
                        fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w("UserFirestoreConverter", "⚠️ Failed to extract date from Map for $key: ${e.message}")
                    null
                }
            }
            
            null -> null
            else -> {
                Log.w("UserFirestoreConverter", "⚠️ Unknown date format for $key: ${value::class.java}")
                null
            }
        }
    }
}
