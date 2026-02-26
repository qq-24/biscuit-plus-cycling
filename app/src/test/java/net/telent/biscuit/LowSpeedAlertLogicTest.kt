package net.telent.biscuit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class LowSpeedAlertLogicTest {

    private val baseTime: Instant = Instant.parse("2024-01-01T08:00:00Z")

    private fun alert(
        currentSpeed: Double,
        lowSpeedThreshold: Double = 10.0,
        autoPauseEnabled: Boolean = false,
        autoPauseSpeed: Double = 3.0,
        lowSpeedAlertEnabled: Boolean = true,
        isRecording: Boolean = true,
        isPaused: Boolean = false,
        now: Instant = baseTime
    ): LowSpeedAlertLogic.AlertResult {
        val logic = LowSpeedAlertLogic(lastAlertTime = Instant.EPOCH)
        return logic.processTick(
            lowSpeedAlertEnabled = lowSpeedAlertEnabled,
            autoPauseEnabled = autoPauseEnabled,
            currentSpeed = currentSpeed,
            lowSpeedThreshold = lowSpeedThreshold,
            autoPauseSpeed = autoPauseSpeed,
            isPaused = isPaused,
            isRecording = isRecording,
            now = now
        )
    }

    // --- Boundary: speed exactly equals lowSpeedThreshold ---

    @Test
    fun `speed exactly equals lowSpeedThreshold should not alert`() {
        val result = alert(currentSpeed = 10.0, lowSpeedThreshold = 10.0)
        assertFalse(result.shouldAlert)
    }

    // --- Boundary: speed is 0 with auto-pause disabled ---

    @Test
    fun `speed is 0 with auto-pause disabled should not alert`() {
        val result = alert(currentSpeed = 0.0, autoPauseEnabled = false)
        assertFalse(result.shouldAlert)
    }

    // --- Boundary: speed is negative ---

    @Test
    fun `negative speed should not alert`() {
        val result = alert(currentSpeed = -5.0, autoPauseEnabled = false)
        assertFalse(result.shouldAlert)
    }

    // --- Boundary: lowSpeedThreshold <= autoPauseSpeed (empty alert zone) ---

    @Test
    fun `lowSpeedThreshold equals autoPauseSpeed should not alert for speed in range`() {
        val result = alert(
            currentSpeed = 5.0,
            lowSpeedThreshold = 5.0,
            autoPauseEnabled = true,
            autoPauseSpeed = 5.0
        )
        assertFalse(result.shouldAlert)
    }

    @Test
    fun `lowSpeedThreshold below autoPauseSpeed should not alert for speed between them`() {
        // lowSpeedThreshold=3, autoPauseSpeed=5, speed=4 → below autoPause → no alert
        val result = alert(
            currentSpeed = 4.0,
            lowSpeedThreshold = 3.0,
            autoPauseEnabled = true,
            autoPauseSpeed = 5.0
        )
        assertFalse(result.shouldAlert)
    }

    // --- Boundary: speed just below lowSpeedThreshold ---

    @Test
    fun `speed just below lowSpeedThreshold should alert`() {
        val result = alert(
            currentSpeed = 9.999,
            lowSpeedThreshold = 10.0,
            autoPauseEnabled = false
        )
        assertTrue(result.shouldAlert)
    }

    // --- Boundary: speed just above autoPauseSpeed with auto-pause enabled ---

    @Test
    fun `speed just above autoPauseSpeed with auto-pause enabled should alert`() {
        val result = alert(
            currentSpeed = 3.001,
            lowSpeedThreshold = 10.0,
            autoPauseEnabled = true,
            autoPauseSpeed = 3.0
        )
        assertTrue(result.shouldAlert)
    }
}
