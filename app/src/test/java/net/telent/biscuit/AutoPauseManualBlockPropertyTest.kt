package net.telent.biscuit

// Feature: ui-fixes-and-improvements, Property 5: 手动暂停阻断自动暂停

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Property 5: Manual pause blocks auto-pause
 *
 * For any speed sequence with speeds BELOW auto-pause threshold,
 * when ManualPause is active, isAutoPaused state should not change
 * (no auto-pause trigger, no auto-resume).
 *
 * Note: When autoPauseEnabled=true and speed >= threshold, the system
 * auto-resumes from manual pause (consolidated from BiscuitService logic).
 * This test covers the case where speed stays below threshold.
 *
 * Validates: Requirements 8.1, 8.2
 */
class AutoPauseManualBlockPropertyTest : FunSpec({

    test("Property 5: isAutoPaused never changes while manual pause is active and speed below threshold") {
        val initialAutoPausedArb = Arb.boolean()
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)

        checkAll(100, initialAutoPausedArb, thresholdArb, delayArb) { initialAutoPaused, threshold, delay ->
            // Generate speeds that are always below threshold to keep manual pause active
            val speedArb = Arb.double(0.0, threshold * 0.99)
            val speedListArb = Arb.list(speedArb, 1..50)

            checkAll(20, speedListArb) { speeds ->
                val logic = AutoPauseLogic(
                    isAutoPaused = initialAutoPaused,
                    isManualPaused = true, // Manual pause is active
                    lowSpeedStartTime = null
                )

                val capturedInitial = logic.isAutoPaused
                val baseTime = Instant.parse("2024-01-01T00:00:00Z")

                // Process all speed ticks while manual pause is active
                speeds.forEachIndexed { index, speed ->
                    val now = baseTime.plusSeconds((index + 1) * 60L)
                    logic.processTick(
                        autoPauseEnabled = true,
                        currentSpeed = speed,
                        autoPauseSpeed = threshold,
                        autoPauseDelay = delay,
                        now = now
                    )

                    // isAutoPaused must remain unchanged throughout
                    logic.isAutoPaused shouldBe capturedInitial
                }
            }
        }
    }
})
