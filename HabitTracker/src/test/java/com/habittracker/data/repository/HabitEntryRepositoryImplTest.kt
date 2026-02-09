package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.HabitEntryDao
import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HabitEntryRepositoryImplTest {

    @Test
    fun markHabitCompleted_clearsRestDayFlag() = runTest {
        val habitEntryDao: HabitEntryDao = mock()
        val firestore: FirebaseFirestore = mock()
        val syncManager: SyncManager = mock()
        val syncWorkScheduler: SyncWorkScheduler = mock()

        val repo = HabitEntryRepositoryImpl(
            habitEntryDao = habitEntryDao,
            firestore = firestore,
            syncManager = syncManager,
            syncWorkScheduler = syncWorkScheduler
        )

        val habitId = "habit-1"
        val userId = "user-1"
        val date = LocalDate(2026, 2, 9)

        val existing = HabitEntryEntity(
            habitId = habitId,
            userId = userId,
            date = date,
            completed = false,
            completedAt = null,
            value = 0,
            note = null,
            isRestDay = true,
            streakBonusAwardedDays = null,
            photoUrl = null,
            createdAt = date,
            updatedAt = date
        )

        whenever(habitEntryDao.getEntry(habitId, userId, date)).thenReturn(existing)

        repo.markHabitCompleted(habitId, userId, date, note = null)

        val captor = argumentCaptor<HabitEntryEntity>()
        verify(habitEntryDao).updateEntry(captor.capture())
        assertFalse(captor.firstValue.isRestDay)
    }
}
