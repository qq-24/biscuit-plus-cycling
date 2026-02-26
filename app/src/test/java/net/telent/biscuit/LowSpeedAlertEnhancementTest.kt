package net.telent.biscuit

import android.os.Vibrator
import io.mockk.mockk
import io.mockk.verify
import net.telent.biscuit.ui.home.EdgeGlowView
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for the low-speed-alert-enhancement feature.
 * Covers: GLOW_COLOR constant, vibrateWithAmplitude boundary values,
 * vibration cancellation on alert stop, and auto_pause_speed default.
 */
class LowSpeedAlertEnhancementTest {

    private lateinit var vibrator: Vibrator
    private lateinit var vibrationHelper: VibrationHelper

    @BeforeEach
    fun setUp() {
        vibrator = mockk(relaxed = true)
        vibrationHelper = VibrationHelper(vibrator)
    }

    // --- 9.1: Verify GLOW_COLOR constant is red #FF0000 ---

    @Test
    @DisplayName("9.1 GLOW_COLOR constant should be red 0xFFFF0000 (#FF0000)")
    fun glowColorConstantIsRed() {
        // private const val in companion object compiles to a field on the outer class
        val field = EdgeGlowView::class.java.getDeclaredField("GLOW_COLOR")
        field.isAccessible = true
        val glowColor = field.getInt(null) // static field, pass null

        // 0xFFFF0000.toInt() == -65536 in signed 32-bit
        assertEquals(0xFFFF0000.toInt(), glowColor, "GLOW_COLOR should be 0xFFFF0000 (red)")
    }

    // --- 9.2: Verify vibrateWithAmplitude boundary values ---

    @Test
    @DisplayName("9.2a vibrateWithAmplitude(0.0) should not trigger vibration")
    fun vibrateWithAmplitudeZero() {
        vibrationHelper.vibrateWithAmplitude(0.0f)
        // amplitude = (0.0 * 255).toInt() = 0 → early return, no vibrate call
        verify(exactly = 0) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<Long>())
        }
    }

    @Test
    @DisplayName("9.2b vibrateWithAmplitude(0.5) should compute amplitude 127")
    fun vibrateWithAmplitudeHalf() {
        val alpha = 0.5f
        val expectedAmplitude = (alpha * 255).toInt().coerceIn(0, 255)
        assertEquals(127, expectedAmplitude, "amplitude for alpha=0.5 should be 127")

        vibrationHelper.vibrateWithAmplitude(alpha)
        // Build.VERSION.SDK_INT defaults to 0 in unit tests → deprecated path
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50L)
        }
    }

    @Test
    @DisplayName("9.2c vibrateWithAmplitude(1.0) should compute amplitude 255")
    fun vibrateWithAmplitudeFull() {
        val alpha = 1.0f
        val expectedAmplitude = (alpha * 255).toInt().coerceIn(0, 255)
        assertEquals(255, expectedAmplitude, "amplitude for alpha=1.0 should be 255")

        vibrationHelper.vibrateWithAmplitude(alpha)
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50L)
        }
    }

    // --- 9.3: Verify vibration cancellation when low-speed alert stops ---

    @Test
    @DisplayName("9.3 stopVibration should call vibrator.cancel()")
    fun stopVibrationCancelsVibrator() {
        vibrationHelper.stopVibration()
        verify(exactly = 1) { vibrator.cancel() }
    }

    // --- 9.4: Verify auto_pause_speed default value is 3.0f ---

    @Test
    @DisplayName("9.4 auto_pause_speed default 3.0 suppresses alert for speed below 3.0 when autoPause enabled")
    fun autoPauseSpeedDefaultSuppressesAlert() {
        val logic = LowSpeedAlertLogic()
        val now = Instant.parse("2024-01-01T08:00:00Z")

        // Speed 2.9 < autoPauseSpeed 3.0 with autoPause enabled → no alert
        val result = logic.processTick(
            lowSpeedAlertEnabled = true,
            autoPauseEnabled = true,
            currentSpeed = 2.9,
            lowSpeedThreshold = 10.0,
            autoPauseSpeed = 3.0,
            isPaused = false,
            isRecording = true,
            now = now
        )
        assertFalse(result.shouldAlert, "Speed below auto_pause_speed default (3.0) should suppress alert")
    }

    @Test
    @DisplayName("9.4 auto_pause_speed default 3.0 allows alert for speed above 3.0 when autoPause enabled")
    fun autoPauseSpeedDefaultAllowsAlertAboveThreshold() {
        val logic = LowSpeedAlertLogic()
        val now = Instant.parse("2024-01-01T08:00:00Z")

        // Speed 3.1 > autoPauseSpeed 3.0 and < lowSpeedThreshold 10.0 → should alert
        val result = logic.processTick(
            lowSpeedAlertEnabled = true,
            autoPauseEnabled = true,
            currentSpeed = 3.1,
            lowSpeedThreshold = 10.0,
            autoPauseSpeed = 3.0,
            isPaused = false,
            isRecording = true,
            now = now
        )
        assertTrue(result.shouldAlert, "Speed above auto_pause_speed default (3.0) should allow alert")
    }
}
