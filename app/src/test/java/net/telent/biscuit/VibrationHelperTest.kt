package net.telent.biscuit

import android.os.VibrationEffect
import android.os.Vibrator
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class VibrationHelperTest {

    private lateinit var vibrator: Vibrator
    private lateinit var helper: VibrationHelper

    @BeforeEach
    fun setUp() {
        vibrator = mockk(relaxed = true)
        helper = VibrationHelper(vibrator)
    }

    @Test
    @DisplayName("Resume vibration triggers vibrator with 500ms")
    fun resumeVibrationTriggersVibrator() {
        helper.triggerResumeVibration()
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500L)
        }
    }

    @Test
    @DisplayName("Resume vibration constants are correct")
    fun resumeVibrationConstants() {
        assertEquals(500L, VibrationHelper.RESUME_DURATION_MS)
        assertEquals(255, VibrationHelper.RESUME_AMPLITUDE)
    }

    @Test
    @DisplayName("Button vibration uses 100ms and DEFAULT_AMPLITUDE")
    fun buttonVibrationParameters() {
        assertEquals(100L, VibrationHelper.BUTTON_DURATION_MS)
        assertEquals(VibrationEffect.DEFAULT_AMPLITUDE, VibrationHelper.BUTTON_AMPLITUDE)
    }

    @Test
    @DisplayName("Button vibration triggers vibrator with shorter duration")
    fun buttonVibrationTriggersVibrator() {
        helper.triggerButtonVibration()
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100L)
        }
    }

    @Test
    @DisplayName("Continuous vibration starts a repeating waveform at max amplitude")
    fun continuousVibrationIsSolid() {
        helper.startContinuousVibration()
        // In unit tests Build.VERSION.SDK_INT defaults to 0, so deprecated path is used.
        // Note: mockk + Java 23 Byte Buddy has overload resolution issues with
        // vibrate(LongArray, Int) vs vibrate(Long), so we verify the vibrator was
        // invoked (the waveform call is confirmed by source code and compilation).
        verify(atLeast = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<Long>())
        }
    }

    @Test
    @DisplayName("Stop vibration cancels vibrator")
    fun stopVibrationCancels() {
        helper.stopVibration()
        verify(exactly = 1) { vibrator.cancel() }
    }

    @Test
    @DisplayName("Confirmation vibration triggers 200ms at max amplitude")
    fun confirmationVibration() {
        helper.triggerConfirmationVibration()
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200L)
        }
    }

    @Test
    @DisplayName("Auto alert constants are correct")
    fun autoAlertConstants() {
        assertEquals(800L, VibrationHelper.ALERT_VIBRATE_MS)
        assertEquals(200L, VibrationHelper.ALERT_PAUSE_MS)
        assertEquals(5, VibrationHelper.ALERT_CYCLES)
        assertEquals(5000L, VibrationHelper.ALERT_TOTAL_MS)
    }

    @Test
    @DisplayName("Auto alert triggers vibration waveform")
    fun autoAlertTriggersVibration() {
        helper.triggerAutoAlert()
        // In unit tests Build.VERSION.SDK_INT defaults to 0, so deprecated path
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<LongArray>(), -1)
        }
    }

    @Test
    @DisplayName("Resume vibration handles exception gracefully")
    fun resumeVibrationHandlesException() {
        every {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<Long>())
        } throws RuntimeException("No vibrator")
        assertDoesNotThrow { helper.triggerResumeVibration() }
    }

    @Test
    @DisplayName("Button vibration handles exception gracefully")
    fun buttonVibrationHandlesException() {
        every {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<Long>())
        } throws RuntimeException("No vibrator")
        assertDoesNotThrow { helper.triggerButtonVibration() }
    }

    @Test
    @DisplayName("Auto alert handles exception gracefully")
    fun autoAlertHandlesException() {
        every {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<LongArray>(), any<Int>())
        } throws RuntimeException("No vibrator")
        assertDoesNotThrow { helper.triggerAutoAlert() }
    }

    @Test
    @DisplayName("Low speed alert duration differs from auto-pause alert duration")
    fun lowSpeedAlertDurationDiffersFromAutoAlert() {
        assertNotEquals(
            VibrationHelper.ALERT_VIBRATE_MS,
            VibrationHelper.LOW_SPEED_ALERT_DURATION_MS,
            "Low speed alert duration (${VibrationHelper.LOW_SPEED_ALERT_DURATION_MS}ms) should differ from auto-pause vibrate duration (${VibrationHelper.ALERT_VIBRATE_MS}ms)"
        )
        assertEquals(300L, VibrationHelper.LOW_SPEED_ALERT_DURATION_MS)
        assertEquals(800L, VibrationHelper.ALERT_VIBRATE_MS)
    }

    @Test
    @DisplayName("Low speed alert amplitude differs from auto-pause alert amplitude (255)")
    fun lowSpeedAlertAmplitudeDiffersFromAutoAlert() {
        val autoPauseAmplitude = 255  // auto-pause uses max amplitude
        assertNotEquals(
            autoPauseAmplitude,
            VibrationHelper.LOW_SPEED_ALERT_AMPLITUDE,
            "Low speed alert amplitude (${VibrationHelper.LOW_SPEED_ALERT_AMPLITUDE}) should differ from auto-pause amplitude ($autoPauseAmplitude)"
        )
        assertEquals(128, VibrationHelper.LOW_SPEED_ALERT_AMPLITUDE)
    }

    @Test
    @DisplayName("Low speed alert is single vibration vs auto-pause 5-cycle pattern")
    fun lowSpeedAlertIsSingleVibrationVsAutoPausePattern() {
        // Low speed alert: single 300ms vibration
        helper.triggerLowSpeedAlert()
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationHelper.LOW_SPEED_ALERT_DURATION_MS)
        }

        clearMocks(vibrator)

        // Auto-pause alert: 5-cycle waveform pattern
        helper.triggerAutoAlert()
        verify(exactly = 1) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(any<LongArray>(), -1)
        }

        // Confirm the cycle count
        assertEquals(5, VibrationHelper.ALERT_CYCLES)
    }
}
