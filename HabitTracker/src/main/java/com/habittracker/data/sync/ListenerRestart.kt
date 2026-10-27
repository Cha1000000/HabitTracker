package com.habittracker.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Держит [block] запущенным, пока корутину не отменят. Слушатель Firestore после ошибки больше
 * ничего не присылает, поэтому упавший слушатель (или запись его данных в Room) поднимаем заново
 * с растущей паузой: 5 с, 10 с, … до 5 минут.
 */
internal suspend fun keepRestarting(tag: String, label: String, block: suspend () -> Unit) {
    var failures = 0
    while (true) {
        val startedAt = System.nanoTime()
        try {
            block()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Слушатель долго проработал — это новый сбой, паузы начинаем сначала
            if (System.nanoTime() - startedAt > STABLE_RUN_NANOS) failures = 0
            val pause = restartDelayMillis(failures++)
            Timber.tag(tag).w(e, "[$label] failed, restart in ${pause / 1000}s")
            delay(pause)
        }
    }
}

internal fun restartDelayMillis(failures: Int): Long =
    (FIRST_RESTART_DELAY_MS shl failures.coerceIn(0, 6)).coerceAtMost(MAX_RESTART_DELAY_MS)

private const val FIRST_RESTART_DELAY_MS = 5_000L
private const val MAX_RESTART_DELAY_MS = 5 * 60_000L
private const val STABLE_RUN_NANOS = 10 * 60 * 1_000_000_000L
