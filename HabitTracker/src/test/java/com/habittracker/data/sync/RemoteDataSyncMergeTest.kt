package com.habittracker.data.sync

import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitParticipantSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDataSyncMergeTest {

    private val day = LocalDate(2026, 10, 20)
    private val entry = HabitEntry(
        habitId = "h1",
        userId = "u1",
        date = day,
        completed = true,
        completedAt = null,
        note = null,
        createdAt = day,
        updatedAt = day
    )
    private val settings = HabitParticipantSettings(
        habitId = "h1",
        userId = "u1",
        reminderTime = LocalTime(9, 0),
        reminderEnabled = true,
        reminderDays = emptySet(),
        notificationsMuted = false,
        timeZone = null,
        updatedAt = 1_000L
    )

    @Test
    fun entry_absent_locally_is_applied() {
        assertTrue(RemoteDataSync.shouldApplyRemote(entry, null))
    }

    @Test
    fun newer_remote_entry_is_applied() {
        val local = entry.copy(updatedAt = LocalDate(2026, 10, 19), completed = false)
        assertTrue(RemoteDataSync.shouldApplyRemote(entry, local))
    }

    @Test
    fun older_remote_entry_is_skipped() {
        val local = entry.copy(updatedAt = LocalDate(2026, 10, 21), completed = false)
        assertFalse(RemoteDataSync.shouldApplyRemote(entry, local))
    }

    @Test
    fun same_day_entry_applies_only_when_content_differs() {
        assertFalse(RemoteDataSync.shouldApplyRemote(entry, entry))
        assertTrue(RemoteDataSync.shouldApplyRemote(entry, entry.copy(completed = false)))
    }

    @Test
    fun settings_apply_when_content_differs_even_if_remote_clock_is_behind() {
        val local = settings.copy(updatedAt = 2_000L, notificationsMuted = true)
        assertTrue(RemoteDataSync.shouldApplyRemote(settings, local))
    }

    @Test
    fun identical_settings_with_older_remote_are_skipped() {
        assertFalse(RemoteDataSync.shouldApplyRemote(settings, settings.copy(updatedAt = 2_000L)))
    }

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    @Test
    fun entry_without_tombstone_is_alive() {
        assertFalse(RemoteDataSync.isTombstone(null, null, null, day))
    }

    @Test
    fun tombstoned_entry_is_deleted() {
        val deletedAt = millisAt(day)
        assertTrue(RemoteDataSync.isTombstone(deletedAt, deletedAt, null, day))
    }

    @Test
    fun entry_rewritten_after_deletion_by_new_version_is_alive() {
        val deletedAt = millisAt(day)
        assertFalse(RemoteDataSync.isTombstone(deletedAt, deletedAt + 1, null, day))
    }

    @Test
    fun legacy_entry_rewritten_a_day_after_deletion_is_alive() {
        // Старые версии не снимали надгробие и не писали updatedAtMillis
        val deletedAt = millisAt(day)
        assertFalse(RemoteDataSync.isTombstone(deletedAt, deletedAt, null, LocalDate(2026, 10, 21)))
    }

    @Test
    fun deletion_day_of_the_deleting_device_wins_over_reader_time_zone() {
        // Удалили 21-го по часам удалившего, а в поясе читателя это ещё 20-е
        val deletedAt = millisAt(day)
        assertTrue(RemoteDataSync.isTombstone(deletedAt, deletedAt, LocalDate(2026, 10, 21), LocalDate(2026, 10, 21)))
    }

    @Test
    fun legacy_rewrite_after_deletion_day_of_deleting_device_is_alive() {
        val deletedAt = millisAt(day)
        assertFalse(RemoteDataSync.isTombstone(deletedAt, deletedAt, day, LocalDate(2026, 10, 21)))
    }
}
