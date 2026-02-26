package net.telent.biscuit

// Feature: low-speed-alert-and-font-weight, Property 1: 低速提醒触发正确性
// Feature: low-speed-alert-and-font-weight, Property 2: 低速提醒频率限制

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Property 1: 低速提醒触发正确性
 *
 * For any combination of speed, thresholds, auto-pause state, pause state,
 * and recording state, processTick() should return correct shouldAlert:
 * - When not enabled, not recording, or paused → shouldAlert = false
 * - When auto-pause enabled and speed < autoPauseSpeed → shouldAlert = false
 * - When auto-pause not enabled and speed <= 0 → shouldAlert = false
 * - When speed >= lowSpeedThreshold → shouldAlert = false
 * - Only when speed is in the valid alert zone → shouldAlert = true
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6
 *
 * Property 2: 低速提醒频率限制
 *
 * For any consecutive tick sequence, when two ticks are less than
 * minIntervalSeconds apart, even if shouldAlert = true, alertTriggered
 * should be false. alertTriggered can only be true when enough time
 * has elapsed since the last trigger.
 *
 * Validates: Requirements 2.5
 */
class LowSpeedAlertLogicPropertyTest : FunSpec({

    // Feature: low-speed-alert-and-font-weight, Property 1: 低速提醒触发正确性
    test("Property 1: processTick shouldAlert correctness for any state combination") {
        val speedArb = Arb.double(0.0, 60.0)
        val thresholdArb = Arb.double(1.0, 50.0)
        val autoPauseSpeedArb = Arb.double(0.5, 15.0)
        val boolArb = Arb.boolean()

        checkAll(
            100,
            speedArb,
            thresholdArb,
            autoPauseSpeedArb,
            boolArb,
            boolArb,
            boolArb,
            boolArb
        ) { speed, lowSpeedThreshold, autoPauseSpeed, lowSpeedAlertEnabled,
            autoPauseEnabled, isPaused, isRecording ->

            val logic = LowSpeedAlertLogic()
            val now = Instant.parse("2024-01-01T00:00:00Z")

            val result = logic.processTick(
                lowSpeedAlertEnabled = lowSpeedAlertEnabled,
                autoPauseEnabled = autoPauseEnabled,
                currentSpeed = speed,
                lowSpeedThreshold = lowSpeedThreshold,
                autoPauseSpeed = autoPauseSpeed,
                isPaused = isPaused,
                isRecording = isRecording,
                now = now
            )

            // Compute expected shouldAlert
            val expected = when {
                !lowSpeedAlertEnabled || !isRecording || isPaused -> false
                autoPauseEnabled && speed < autoPauseSpeed -> false
                !autoPauseEnabled && speed <= 0.0 -> false
                speed >= lowSpeedThreshold -> false
                else -> true
            }

            result.shouldAlert shouldBe expected
        }
    }

    // Feature: low-speed-alert-and-font-weight, Property 2: 低速提醒频率限制
    test("Property 2: alertTriggered respects minIntervalSeconds frequency limit") {
        val intervalArb = Arb.long(5, 30)
        val tickCountArb = Arb.int(2, 20)
        val tickGapArb = Arb.list(Arb.long(1, 25), 1..19)

        checkAll(100, intervalArb, tickCountArb, tickGapArb) { minInterval, tickCount, gaps ->
            val logic = LowSpeedAlertLogic()
            val baseTime = Instant.parse("2024-01-01T00:00:00Z")

            // Use a fixed scenario where shouldAlert is always true:
            // enabled, recording, not paused, speed in alert zone
            val lowSpeedThreshold = 15.0
            val speed = 5.0 // below threshold, above 0
            val autoPauseSpeed = 2.0

            var currentTime = baseTime
            var lastTriggeredTime: Instant? = null
            val actualTickCount = tickCount.coerceAtMost(gaps.size + 1)

            for (i in 0 until actualTickCount) {
                if (i > 0 && i - 1 < gaps.size) {
                    currentTime = currentTime.plusSeconds(gaps[i - 1])
                }

                val result = logic.processTick(
                    lowSpeedAlertEnabled = true,
                    autoPauseEnabled = true,
                    currentSpeed = speed,
                    lowSpeedThreshold = lowSpeedThreshold,
                    autoPauseSpeed = autoPauseSpeed,
                    isPaused = false,
                    isRecording = true,
                    now = currentTime,
                    minIntervalSeconds = minInterval
                )

                // shouldAlert should always be true in this scenario
                result.shouldAlert shouldBe true

                if (result.alertTriggered) {
                    // If triggered, enough time must have elapsed since last trigger
                    if (lastTriggeredTime != null) {
                        val elapsed = java.time.Duration.between(lastTriggeredTime, currentTime).seconds
                        (elapsed >= minInterval) shouldBe true
                    }
                    lastTriggeredTime = currentTime
                } else {
                    // If not triggered but shouldAlert is true, not enough time elapsed
                    if (lastTriggeredTime != null) {
                        val elapsed = java.time.Duration.between(lastTriggeredTime, currentTime).seconds
                        (elapsed < minInterval) shouldBe true
                    }
                    // Note: first tick always triggers (lastAlertTime = EPOCH), so
                    // lastTriggeredTime == null should not happen after first tick
                }
            }
        }
    }
})
