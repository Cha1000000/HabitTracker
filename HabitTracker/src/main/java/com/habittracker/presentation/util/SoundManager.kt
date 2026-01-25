package com.habittracker.presentation.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Менеджер для воспроизведения звуковых эффектов в приложении
 */
object SoundManager {

    private const val TAG = "SoundManager"

    private var toneGenerator: ToneGenerator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var applauseResId: Int = 0
    private const val APPLAUSE_RAW_NAME = "applause"

    /**
     * Воспроизводит звук успешного выполнения (короткий приятный тон)
     */
    fun playSuccessSound(context: Context) {
        try {
            if (tryPlayApplauseFromRaw(context)) {
                playSuccessVibration(context)
                return
            }

            // Fallback: ToneGenerator
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            }

            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)

            playSuccessVibration(context)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "playSuccessSound error")
        }
    }

    @SuppressLint("DiscouragedApi")
    fun preload(context: Context) {
        runCatching {
            val resId = context.resources.getIdentifier(APPLAUSE_RAW_NAME, "raw", context.packageName)
            if (resId == 0) {
                Timber.tag(TAG).d("[preload] raw/$APPLAUSE_RAW_NAME not found")
                return
            }

            applauseResId = resId
            Timber.tag(TAG).d("[preload] raw/$APPLAUSE_RAW_NAME resId=$resId")

            // Создаём MediaPlayer заранее
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                setVolume(0.7f, 0.7f)
                setOnCompletionListener {
                    // После завершения воспроизведения подготавливаем для повторного использования
                    runCatching { seekTo(0) }
                }
                setOnErrorListener { _, what, extra ->
                    Timber.tag(TAG).e("[MediaPlayer] error what=$what extra=$extra")
                    false
                }
            }

            if (mediaPlayer != null) {
                Timber.tag(TAG).d("[preload] MediaPlayer created successfully")
            } else {
                Timber.tag(TAG).e("[preload] MediaPlayer.create returned null")
            }
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "[preload] error")
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun tryPlayApplauseFromRaw(context: Context): Boolean {
        return try {
            val resId = if (applauseResId != 0) {
                applauseResId
            } else {
                context.resources.getIdentifier(APPLAUSE_RAW_NAME, "raw", context.packageName)
            }

            if (resId == 0) {
                Timber.tag(TAG).d("[play] raw/$APPLAUSE_RAW_NAME not found")
                return false
            }

            val player = mediaPlayer
            if (player != null) {
                Timber.tag(TAG).d("[play] using existing MediaPlayer")
                runCatching {
                    player.seekTo(0)
                    player.start()
                    Timber.tag(TAG).d("[play] MediaPlayer started")
                    return true
                }.onFailure { e ->
                    Timber.tag(TAG).e(e, "[play] MediaPlayer.start error, recreating")
                    releaseMediaPlayer()
                }
            }

            // Создаём новый MediaPlayer если нет или произошла ошибка
            Timber.tag(TAG).d("[play] creating new MediaPlayer")
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                setVolume(0.7f, 0.7f)
                setOnCompletionListener {
                    runCatching { seekTo(0) }
                }
                start()
                Timber.tag(TAG).d("[play] new MediaPlayer started")
            }

            mediaPlayer != null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "[play] error")
            false
        }
    }
    
    /**
     * Воспроизводит паттерн вибрации для успешного действия
     */
    private fun playSuccessVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService<VibratorManager>()?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
            
            vibrator?.let {
                // minSdk = 26, поэтому используем только VibrationEffect
                // Паттерн: короткая-пауза-короткая (как "та-да!")
                val timings = longArrayOf(0, 50, 50, 100)
                val amplitudes = intArrayOf(0, 100, 0, 150)
                it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Exception) {
            // Игнорируем ошибки вибрации
        }
    }
    
    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }
}
