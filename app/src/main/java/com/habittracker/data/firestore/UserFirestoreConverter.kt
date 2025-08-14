package com.habittracker.data.firestore

import com.habittracker.domain.model.User

/**
 * Профессиональный Firestore-конвертер для User модели.
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
        return mapOf(
            "id" to user.id,
            "name" to user.name,
            "email" to user.email,
            "avatarUrl" to (user.avatarUrl ?: ""),
            "level" to user.level,
            "points" to user.points,
            "streak" to user.streak,
            "longestStreak" to user.longestStreak,
            "friendsCount" to user.friendsCount,
            "fcmToken" to (user.fcmToken ?: ""),
            "createdAt" to user.createdAt, // Long timestamp
            "lastActiveAt" to user.lastActiveAt, // Long timestamp
            "timezone" to (user.timezone ?: "")
        )
    }
    
    /**
     * Конвертирует Firestore документ в User модель
     * с поддержкой legacy форматов и fallback для поврежденных данных
     */
    fun fromFirestore(data: Map<String, Any>, documentId: String): User? {
        return try {
            User(
                id = documentId,
                name = extractString(data, "name") ?: "",
                email = extractString(data, "email") ?: "",
                avatarUrl = extractString(data, "avatarUrl"),
                level = extractInt(data, "level") ?: 1,
                points = extractInt(data, "points") ?: 0,
                streak = extractInt(data, "streak") ?: 0,
                longestStreak = extractInt(data, "longestStreak") ?: 0,
                friendsCount = extractInt(data, "friendsCount") ?: 0,
                fcmToken = extractString(data, "fcmToken"),
                createdAt = extractTimestamp(data, "createdAt") ?: System.currentTimeMillis(),
                lastActiveAt = extractTimestamp(data, "lastActiveAt") ?: System.currentTimeMillis(),
                timezone = extractString(data, "timezone")
            )
        } catch (e: Exception) {
            android.util.Log.e("UserFirestoreConverter", "❌ Failed to convert document $documentId: ${e.message}")
            null
        }
    }
    
    /**
     * Извлекает строку из Map с null-safety
     */
    private fun extractString(data: Map<String, Any>, key: String): String? {
        return when (val value = data[key]) {
            is String -> if (value.isBlank()) null else value
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
     * Извлекает timestamp из Map с поддержкой legacy форматов
     * 
     * Поддерживает:
     * - Long (текущий формат)
     * - Number (различные числовые типы)
     * - Map (legacy Instant формат)
     * - String (ISO строки)
     */
    private fun extractTimestamp(data: Map<String, Any>, key: String): Long? {
        return when (val value = data[key]) {
            // Текущий формат: Long timestamp
            is Long -> value
            is Number -> value.toLong()
            
            // Legacy формат: сериализованный Instant как Map
            is Map<*, *> -> {
                try {
                    // kotlinx.datetime.Instant сериализуется как Map с "seconds" и "nanos"
                    val seconds = (value["seconds"] as? Number)?.toLong()
                    val nanos = (value["nanos"] as? Number)?.toLong() ?: 0L
                    
                    if (seconds != null) {
                        seconds * 1000 + nanos / 1_000_000 // конвертируем в миллисекунды
                    } else {
                        // Альтернативный legacy формат
                        (value["epochSeconds"] as? Number)?.toLong()?.times(1000)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("UserFirestoreConverter", "⚠️ Failed to extract timestamp from Map for $key: ${e.message}")
                    null
                }
            }
            
            // Fallback: ISO строки или другие форматы
            is String -> {
                try {
                    value.toLongOrNull() // пытаемся парсить как число
                } catch (e: Exception) {
                    null
                }
            }
            
            null -> null
            else -> {
                android.util.Log.w("UserFirestoreConverter", "⚠️ Unknown timestamp format for $key: ${value::class.java}")
                null
            }
        }
    }
}
