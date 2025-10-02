package com.habittracker.presentation.utils

import android.annotation.SuppressLint
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Утилита для форматирования даты и времени
 */
object DateTimeFormatter {
    
    /**
     * Форматирует Instant в читаемую строку времени
     * Примеры:
     * - "только что" (менее минуты назад)
     * - "5 минут назад"
     * - "2 часа назад"
     * - "вчера в 14:30"
     * - "15 марта в 10:45"
     */
    fun formatRelativeTime(instant: Instant): String {
        val now = Clock.System.now()
        val diffMillis = (now - instant).inWholeMilliseconds
        val diffMinutes = diffMillis / (60 * 1000)
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24
        
        val timeZone = TimeZone.currentSystemDefault()
        val localDateTime = instant.toLocalDateTime(timeZone)
        val today = Clock.System.todayIn(timeZone)
        val yesterday = today.minus(DatePeriod(days = 1))
        
        return when {
            diffMinutes < 1 -> "только что"
            diffMinutes < 60 -> "$diffMinutes ${pluralizeMinutes(diffMinutes.toInt())} назад"
            diffHours < 24 && localDateTime.date == today -> {
                "$diffHours ${pluralizeHours(diffHours.toInt())} назад"
            }
            localDateTime.date == yesterday -> {
                "вчера в ${formatTime(localDateTime)}"
            }
            diffDays < 7 -> {
                "${formatDate(localDateTime.date)} в ${formatTime(localDateTime)}"
            }
            else -> {
                formatDate(localDateTime.date)
            }
        }
    }
    
    /**
     * Форматирует LocalDateTime в строку времени (HH:mm)
     */
    @SuppressLint("DefaultLocale")
    fun formatTime(dateTime: LocalDateTime): String =
        String.format("%02d:%02d", dateTime.hour, dateTime.minute)
    
    /**
     * Форматирует LocalDate в строку даты
     */
    fun formatDate(date: LocalDate): String {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val yesterday = today.minus(DatePeriod(days = 1))
        
        return when (date) {
            today -> "Сегодня"
            yesterday -> "Вчера"
            else -> {
                val months = arrayOf(
                    "января", "февраля", "марта", "апреля", "мая", "июня",
                    "июля", "августа", "сентября", "октября", "ноября", "декабря"
                )
                "${date.dayOfMonth} ${months[date.monthNumber - 1]}"
            }
        }
    }
    
    /**
     * Форматирует дату для отображения в статистике
     */
    fun formatStatDate(date: LocalDate): String {
        val formatter = SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru-RU"))
        // Конвертируем LocalDate в java.util.Date для форматирования
        val epochMillis = date.toEpochDays() * 24 * 60 * 60 * 1000L
        return formatter.format(Date(epochMillis))
    }
    
    private fun pluralizeMinutes(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "минуту"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "минуты"
            else -> "минут"
        }
    }
    
    private fun pluralizeHours(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "час"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "часа"
            else -> "часов"
        }
    }
}
