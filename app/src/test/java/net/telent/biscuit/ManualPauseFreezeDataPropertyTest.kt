package net.telent.biscuit

// Feature: ui-fixes-and-improvements, Property 2: 手动暂停冻结数据

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe

/**
 * Property 2: Manual pause freezes data
 *
 * For any GPS location update sequence, when ManualPause is active,
 * pauseAwareDistance should not increase, and no new trackpoints
 * should be persisted to database.
 *
 * Validates: Requirements 5.1, 5.2
 */
class ManualPauseFreezeDataPropertyTest : FunSpec({

    test("Property 2: pauseAwareDistance does not increase and no trackpoints persist while manual pause is active") {
        // Generate random raw distance sequences (monotonically increasing, simulating GPS sensor)
        val distanceIncrementArb = Arb.double(0.0, 100.0)
        val distanceListArb = Arb.list(distanceIncrementArb, 1..50)
        val initialDistanceArb = Arb.double(0.0, 10000.0)
        val initialPauseAwareArb = Arb.double(0.0, 5000.0)
        val autoPausedArb = Arb.boolean()

        checkAll(
            100,
            distanceListArb,
            initialDistanceArb,
            initialPauseAwareArb,
            autoPausedArb
        ) { increments, initialRawDistance, initialPauseAware, isAutoPaused ->
            val tracker = PauseAwareDistanceTracker(
                pauseAwareDistance = initialPauseAware,
                lastDistanceSnapshot = initialRawDistance,
                wasPausedLastTick = false
            )

            val distanceBefore = tracker.pauseAwareDistance

            // Build monotonically increasing raw distances from the initial value
            var cumulativeRaw = initialRawDistance
            increments.forEach { increment ->
                cumulativeRaw += increment

                val shouldPersist = tracker.processTick(
                    rawDistance = cumulativeRaw,
                    isManualPaused = true, // Manual pause is always active
                    isAutoPaused = isAutoPaused
                )

                // Requirement 5.1: distance should not increase while manually paused
                tracker.pauseAwareDistance shouldBe distanceBefore

                // Requirement 5.2: no trackpoints should be persisted while paused
                shouldPersist shouldBe false
            }
        }
    }
})
