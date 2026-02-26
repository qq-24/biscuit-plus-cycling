package net.telent.biscuit

import java.time.Duration
import java.time.Instant

/**
 * Pure Kotlin state machine for low-speed alert logic.
 * No Android dependencies — mirrors the AutoPauseLogic pattern for testability.
 *
 * Determines whether the rider should be alerted about low speed,
 * coordinating with auto-pause to avoid duplicate notifications.
 */
class LowSpeedAlertLogic(
    var lastAlertTime: Instant = Instant.EPOCH
) {
    data class AlertResult(
        val shouldAlert: Boolean,
        val alertTriggered: Boolean  // whether this tick newly triggered an alert
    )

    fun processTick(
        lowSpeedAlertEnabled: Boolean,
        autoPauseEnabled: Boolean,
        currentSpeed: Double,
        lowSpeedThreshold: Double,
        autoPauseSpeed: Double,
        isPaused: Boolean,
        isRecording: Boolean,
        now: Instant,
        minIntervalSeconds: Long = 10
    ): AlertResult {
        // Guard: feature off, not recording, or paused → no alert
        if (!lowSpeedAlertEnabled || !isRecording || isPaused) {
            return AlertResult(shouldAlert = false, alertTriggered = false)
        }

        // Auto-pause enabled and speed below auto-pause threshold → auto-pause handles it
        if (autoPauseEnabled && currentSpeed < autoPauseSpeed) {
            return AlertResult(shouldAlert = false, alertTriggered = false)
        }

        // Auto-pause not enabled and stationary → no alert
        if (!autoPauseEnabled && currentSpeed <= 0.0) {
            return AlertResult(shouldAlert = false, alertTriggered = false)
        }

        // Speed at or above low-speed threshold → no alert needed
        if (currentSpeed >= lowSpeedThreshold) {
            return AlertResult(shouldAlert = false, alertTriggered = false)
        }

        // Speed is in the alert zone — check frequency limit
        val elapsed = Duration.between(lastAlertTime, now).seconds
        val triggered = elapsed >= minIntervalSeconds
        if (triggered) {
            lastAlertTime = now
        }

        return AlertResult(shouldAlert = true, alertTriggered = triggered)
    }
}
