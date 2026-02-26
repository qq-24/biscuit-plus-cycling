package net.telent.biscuit

import java.time.Duration
import java.time.Instant

/**
 * Extracted auto-pause state machine logic from BiscuitService.updaterThread.
 * Enables unit and property-based testing of pause state transitions.
 */
class AutoPauseLogic(
    var isAutoPaused: Boolean = false,
    var isManualPaused: Boolean = false,
    var lowSpeedStartTime: Instant? = null,
    var resumeCooldownUntil: Instant = Instant.EPOCH,
    var manualPauseCooldownUntil: Instant? = null
) {
    /**
     * Result of a processTick call, indicating what changed.
     */
    data class TickResult(
        val autoPauseChanged: Boolean,
        val manualPauseChanged: Boolean = false
    )

    fun processTick(
        autoPauseEnabled: Boolean,
        currentSpeed: Double,
        autoPauseSpeed: Double,
        autoPauseDelay: Int,
        now: Instant
    ): Boolean {
        return processTickFull(autoPauseEnabled, currentSpeed, autoPauseSpeed, autoPauseDelay, now).autoPauseChanged
    }

    /**
     * Full processTick with detailed result. Handles auto-pause, auto-resume,
     * and manual-pause auto-resume logic.
     */
    fun processTickFull(
        autoPauseEnabled: Boolean,
        currentSpeed: Double,
        autoPauseSpeed: Double,
        autoPauseDelay: Int,
        now: Instant,
        manualPauseCooldownSeconds: Int = 0
    ): TickResult {
        val previousAutoPaused = isAutoPaused
        val previousManualPaused = isManualPaused

        if (autoPauseEnabled && !isManualPaused) {
            // Skip auto-pause during cooldown after resume (manual or auto)
            if (now.isBefore(resumeCooldownUntil)) {
                lowSpeedStartTime = null
            } else if (currentSpeed < autoPauseSpeed) {
                if (lowSpeedStartTime == null) {
                    lowSpeedStartTime = now
                }
                val lowSpeedDuration = Duration.between(lowSpeedStartTime, now).seconds
                if (lowSpeedDuration >= autoPauseDelay && !isAutoPaused) {
                    isAutoPaused = true
                }
            } else {
                // Speed >= threshold: clear low speed timer and auto-resume if paused
                lowSpeedStartTime = null
                if (isAutoPaused) {
                    isAutoPaused = false
                    // Set cooldown after auto-resume to prevent rapid re-pause
                    resumeCooldownUntil = now.plusSeconds(3)
                }
            }
        } else if (isManualPaused) {
            // During manual pause with auto-pause enabled:
            // Auto-resume when speed exceeds threshold, but respect cooldown
            if (autoPauseEnabled && currentSpeed >= autoPauseSpeed) {
                // Check if manual pause cooldown is still active
                if (manualPauseCooldownUntil != null && now.isBefore(manualPauseCooldownUntil!!)) {
                    // Cooldown active: ignore auto-resume condition
                    lowSpeedStartTime = null
                } else {
                    // Cooldown expired or not set: proceed with auto-resume
                    isManualPaused = false
                    isAutoPaused = false
                    lowSpeedStartTime = null
                    resumeCooldownUntil = now.plusSeconds(3)
                }
            } else {
                // Always reset low speed timer during manual pause
                lowSpeedStartTime = null
            }
        } else {
            // Auto-pause disabled, ensure not paused
            isAutoPaused = false
            lowSpeedStartTime = null
        }

        return TickResult(
            autoPauseChanged = isAutoPaused != previousAutoPaused,
            manualPauseChanged = isManualPaused != previousManualPaused
        )
    }
}
