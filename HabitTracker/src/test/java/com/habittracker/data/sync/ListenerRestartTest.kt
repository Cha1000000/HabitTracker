package com.habittracker.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerRestartTest {

    @Test
    fun restart_delay_doubles_and_stops_at_five_minutes() {
        assertEquals(5_000L, restartDelayMillis(0))
        assertEquals(10_000L, restartDelayMillis(1))
        assertEquals(160_000L, restartDelayMillis(5))
        assertEquals(300_000L, restartDelayMillis(6))
        assertEquals(300_000L, restartDelayMillis(100))
    }
}
