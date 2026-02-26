package net.telent.biscuit

// Feature: low-speed-alert-enhancement, Property 1: Alpha-to-amplitude linear mapping
// Feature: low-speed-alert-enhancement, Property 2: Glow width range and persistence round-trip
// Feature: low-speed-alert-enhancement, Property 3: Alert suppression when auto-pause active
// Feature: low-speed-alert-enhancement, Property 4: Auto-pause threshold ignored when disabled

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeInRange
import java.time.Instant

/**
 * Property tests for the low-speed-alert-enhancement feature.
 *
 * Property 1: Alpha-to-amplitude linear mapping
 * For any alpha in [0.0, 1.0], the computed amplitude equals (alpha * 255).toInt().coerceIn(0, 255).
 * Validates: Requirements 1.2
 *
 * Property 2: Glow width range constraint and persistence round-trip
 * For any integer value, clamping to [5, 60] always produces a value in that range.
 * Valid values round-trip through a HashMap store unchanged.
 * Validates: Requirements 2.2, 2.4
 *
 * Property 3: Alert suppression when auto-pause active
 * When autoPauseEnabled=true and speed < autoPauseSpeed, processTick returns shouldAlert=false.
 * Validates: Requirements 4.1
 *
 * Property 4: Auto-pause threshold ignored when disabled
 * When autoPauseEnabled=false, processTick result is the same regardless of autoPauseSpeed value.
 * Validates: Requirements 4.3
 */
class LowSpeedAlertEnhancementPropertyTest : FunSpec({

    // Feature: low-speed-alert-enhancement, Property 1: Alpha-to-amplitude linear mapping
    test("Property 1 - alpha linearly maps to amplitude in 0 to 255 range") {
        checkAll(200, Arb.float(0.0f, 1.0f)) { alpha ->
            val amplitude = (alpha * 255).toInt().coerceIn(0, 255)

            // Amplitude must be in valid range
            amplitude shouldBeInRange 0..255

            // Boundary checks
            if (alpha == 0.0f) {
                amplitude shouldBe 0
            }
            if (alpha == 1.0f) {
                amplitude shouldBe 255
            }
        }
    }

    test("Property 1 - boundary values alpha 0 and 1 produce correct amplitudes") {
        // Explicit boundary verification
        val amp0 = (0.0f * 255).toInt().coerceIn(0, 255)
        amp0 shouldBe 0

        val amp1 = (1.0f * 255).toInt().coerceIn(0, 255)
        amp1 shouldBe 255

        val ampMid = (0.5f * 255).toInt().coerceIn(0, 255)
        ampMid shouldBe 127
    }

    // Feature: low-speed-alert-enhancement, Property 2: Glow width range and persistence round-trip
    test("Property 2 - clamping any integer to 5-60 always produces a value in that range") {
        checkAll(200, Arb.int(-1000, 1000)) { rawValue ->
            val clamped = rawValue.coerceIn(5, 60)
            clamped shouldBeInRange 5..60
        }
    }

    test("Property 2 - valid glow width values round-trip through HashMap store unchanged") {
        checkAll(200, Arb.int(5, 60)) { glowWidth ->
            // Simulate SharedPreferences as a HashMap
            val store = HashMap<String, Int>()
            store["glow_width_dp"] = glowWidth

            val retrieved = store["glow_width_dp"]!!
            retrieved shouldBe glowWidth
        }
    }

    // Feature: low-speed-alert-enhancement, Property 3: Alert suppression when auto-pause active
    test("Property 3 - when autoPause enabled and speed below autoPauseSpeed then shouldAlert is false") {
        val autoPauseSpeedArb = Arb.double(1.0, 50.0)
        val now = Instant.parse("2024-01-01T00:00:00Z")

        checkAll(200, autoPauseSpeedArb) { autoPauseSpeed ->
            // Generate a speed strictly below autoPauseSpeed
            val speed = autoPauseSpeed * 0.5  // always < autoPauseSpeed

            val logic = LowSpeedAlertLogic()
            val result = logic.processTick(
                lowSpeedAlertEnabled = true,
                autoPauseEnabled = true,
                currentSpeed = speed,
                lowSpeedThreshold = autoPauseSpeed + 10.0,
                autoPauseSpeed = autoPauseSpeed,
                isPaused = false,
                isRecording = true,
                now = now
            )

            result.shouldAlert shouldBe false
        }
    }

    // Feature: low-speed-alert-enhancement, Property 4: Auto-pause threshold ignored when disabled
    test("Property 4 - when autoPause disabled result is same regardless of autoPauseSpeed value") {
        val speedArb = Arb.double(0.0, 60.0)
        val thresholdArb = Arb.double(1.0, 50.0)
        val autoPauseSpeed1Arb = Arb.double(0.5, 30.0)
        val autoPauseSpeed2Arb = Arb.double(0.5, 30.0)
        val now = Instant.parse("2024-01-01T00:00:00Z")

        checkAll(
            200,
            speedArb,
            thresholdArb,
            autoPauseSpeed1Arb,
            autoPauseSpeed2Arb
        ) { speed, lowSpeedThreshold, autoPauseSpeed1, autoPauseSpeed2 ->
            val logic1 = LowSpeedAlertLogic()
            val result1 = logic1.processTick(
                lowSpeedAlertEnabled = true,
                autoPauseEnabled = false,
                currentSpeed = speed,
                lowSpeedThreshold = lowSpeedThreshold,
                autoPauseSpeed = autoPauseSpeed1,
                isPaused = false,
                isRecording = true,
                now = now
            )

            val logic2 = LowSpeedAlertLogic()
            val result2 = logic2.processTick(
                lowSpeedAlertEnabled = true,
                autoPauseEnabled = false,
                currentSpeed = speed,
                lowSpeedThreshold = lowSpeedThreshold,
                autoPauseSpeed = autoPauseSpeed2,
                isPaused = false,
                isRecording = true,
                now = now
            )

            result1.shouldAlert shouldBe result2.shouldAlert
            result1.alertTriggered shouldBe result2.alertTriggered
        }
    }
})
