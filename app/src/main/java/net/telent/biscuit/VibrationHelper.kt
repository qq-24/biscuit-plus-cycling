package net.telent.biscuit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class VibrationHelper(private val vibrator: Vibrator, private val context: Context? = null) {

    companion object {
        private const val TAG = "VibrationHelper"
        // Resume tap feedback
        const val RESUME_DURATION_MS = 500L
        const val RESUME_AMPLITUDE = 255
        // Button press feedback (generic)
        const val BUTTON_DURATION_MS = 100L
        val BUTTON_AMPLITUDE = VibrationEffect.DEFAULT_AMPLITUDE
        // Long-press confirmation vibration (after 3s hold completes)
        const val CONFIRMATION_DURATION_MS = 200L
        const val CONFIRMATION_AMPLITUDE = 255
        // Low-speed alert: single short vibration
        const val LOW_SPEED_ALERT_DURATION_MS = 300L
        const val LOW_SPEED_ALERT_AMPLITUDE = 128  // medium amplitude
        // Vibration pulse duration for amplitude-based vibration
        const val PULSE_DURATION_MS = 80L
        // Power function exponent for alpha-to-amplitude mapping
        const val AMPLITUDE_EXPONENT = 0.3
        // Auto-pause/resume alert: 5 seconds total, periodic
        const val ALERT_VIBRATE_MS = 800L
        const val ALERT_PAUSE_MS = 200L
        const val ALERT_CYCLES = 5
        const val ALERT_TOTAL_MS = ALERT_CYCLES * (ALERT_VIBRATE_MS + ALERT_PAUSE_MS) // 5000ms
    }

    private val alertHandler = Handler(Looper.getMainLooper())

    fun triggerResumeVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(RESUME_DURATION_MS, RESUME_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(RESUME_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration not available", e)
        }
    }

    fun triggerButtonVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(BUTTON_DURATION_MS, BUTTON_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(BUTTON_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration not available", e)
        }
    }

    /**
     * Start continuous vibration at constant max amplitude.
     * Used during long-press hold. No pulsing - just solid vibration.
     */
    fun startContinuousVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10_000L, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration not available", e)
        }
    }

    fun stopVibration() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Vibration cancel not available", e)
        }
    }

    fun triggerConfirmationVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(CONFIRMATION_DURATION_MS, CONFIRMATION_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(CONFIRMATION_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration not available", e)
        }
    }

    /**
     * Trigger a single short vibration for low-speed alert.
     * 300ms at medium amplitude (128) — distinctly different from the auto-pause
     * alert which uses 800ms×5 cycle + notification sound.
     */
    fun triggerLowSpeedAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(LOW_SPEED_ALERT_DURATION_MS, LOW_SPEED_ALERT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(LOW_SPEED_ALERT_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Low speed alert vibration not available", e)
        }
    }

    /**
     * Trigger a low-speed alert vibration pulse.
     * Uses same parameters as resume vibration: 500ms at max amplitude (255).
     */
    fun triggerLowSpeedAlertPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(RESUME_DURATION_MS, RESUME_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(RESUME_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Low speed alert pulse not available", e)
        }
    }

    /**
     * 将 alpha [0.0, 1.0] 映射到振幅 [0, 255]，使用幂函数曲线。
     * amplitude = (255 * alpha^0.3).toInt()
     * 特性：单调递增，alpha=0→0，alpha=1→255，alpha=0.5→207(>200)
     */
    fun mapAlphaToAmplitude(alpha: Float): Int {
        if (alpha <= 0f) return 0
        if (alpha >= 1f) return 255
        return (255 * Math.pow(alpha.toDouble(), AMPLITUDE_EXPONENT)).toInt().coerceIn(0, 255)
    }

    /**
     * Trigger a short vibration pulse with amplitude proportional to the given alpha.
     * Uses power function mapping for non-linear curve.
     * alpha: 0.0 (no vibration) to 1.0 (max amplitude 255)
     */
    fun vibrateWithAmplitude(alpha: Float) {
        val amplitude = mapAlphaToAmplitude(alpha)
        if (amplitude == 0) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(PULSE_DURATION_MS, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(PULSE_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration pulse not available", e)
        }
    }

    /**
     * Trigger a strong 5-second periodic alert for auto-pause/auto-resume.
     * Pattern: 800ms vibrate at max + notification sound, 200ms pause, repeat 5 times.
     * Sound plays even in silent/DND mode using USAGE_ALARM.
     */
    fun triggerAutoAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = LongArray(ALERT_CYCLES * 2) { i ->
                    if (i % 2 == 0) ALERT_VIBRATE_MS else ALERT_PAUSE_MS
                }
                val fullTimings = longArrayOf(0L) + timings
                val amplitudes = IntArray(ALERT_CYCLES * 2) { i ->
                    if (i % 2 == 0) 255 else 0
                }
                val fullAmplitudes = intArrayOf(0) + amplitudes
                vibrator.vibrate(VibrationEffect.createWaveform(fullTimings, fullAmplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                val timings = LongArray(ALERT_CYCLES * 2) { i ->
                    if (i % 2 == 0) ALERT_VIBRATE_MS else ALERT_PAUSE_MS
                }
                val fullTimings = longArrayOf(0L) + timings
                vibrator.vibrate(fullTimings, -1)
            }
            playAlertSounds()
        } catch (e: Exception) {
            Log.w(TAG, "Auto alert vibration not available", e)
        }
    }

    private fun playAlertSounds() {
        val ctx = context ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 使用 USAGE_NOTIFICATION_EVENT + AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        // 让其他媒体应用降低音量而非静音
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        var focusRequest: AudioFocusRequest? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttrs)
                .build()
            // 即使请求失败也继续播放
            audioManager.requestAudioFocus(focusRequest)
        }

        for (i in 0 until ALERT_CYCLES) {
            val delay = i * (ALERT_VIBRATE_MS + ALERT_PAUSE_MS)
            alertHandler.postDelayed({
                try {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = RingtoneManager.getRingtone(ctx, uri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone?.audioAttributes = audioAttrs
                    }
                    ringtone?.play()
                } catch (e: Exception) {
                    Log.w(TAG, "Alert sound not available", e)
                }
            }, delay)
        }

        // 所有提示音播放完成后释放音频焦点
        if (focusRequest != null) {
            val fr = focusRequest
            alertHandler.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(fr)
                }
            }, ALERT_TOTAL_MS + 500L)
        }
    }
}
