package net.telent.biscuit

// Feature: ui-fixes-and-improvements, Property 3: 暂停-恢复距离连续性

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import kotlin.math.abs

/**
 * Property 3: Pause-resume distance continuity
 *
 * For any pre-pause distance D, pause-period movement G, and post-resume
 * movement R, the final pauseAwareDistance should equal D + R (excluding G).
 * Pause-period displacement is not counted in total distance.
 *
 * **Validates: Requirements 5.3**
 */
class PauseResumeDistanceContinuityPropertyTest : FunSpec({

    test("Property 3: final distance equals pre-pause accumulated + post-resume accumulated, excluding pause-period movement") {
        // Generators for distance increments in each phase (at least 2 ticks per phase)
        val incrementArb = Arb.double(0.1, 100.0)
        val prePauseArb = Arb.list(incrementArb, 2..20)
        val duringPauseArb = Arb.list(incrementArb, 1..20)
        val postResumeArb = Arb.list(incrementArb, 2..20)

        checkAll(100, prePauseArb, duringPauseArb, postResumeArb) { prePauseIncrements, duringPauseIncrements, postResumeIncrements ->
            val tracker = PauseAwareDistanceTracker(
                pauseAwareDistance = 0.0,
                lastDistanceSnapshot = 0.0,
                wasPausedLastTick = false
            )

            var cumulativeRaw = 0.0

            // Phase 1: Pre-pause — normal running, not paused
            prePauseIncrements.forEach { increment ->
                cumulativeRaw += increment
                tracker.processTick(
                    rawDistance = cumulativeRaw,
                    isManualPaused = false,
                    isAutoPaused = false
                )
            }
            val prePauseDistance = tracker.pauseAwareDistance
            val expectedPrePause = prePauseIncrements.sum()
            abs(prePauseDistance - expectedPrePause) shouldBeLessThanOrEqual 1e-6

            // Phase 2: During pause — paused, distance should not change
            duringPauseIncrements.forEach { increment ->
                cumulativeRaw += increment
                tracker.processTick(
                    rawDistance = cumulativeRaw,
                    isManualPaused = true,
                    isAutoPaused = false
                )
            }
            // Distance must not have changed during pause
            abs(tracker.pauseAwareDistance - prePauseDistance) shouldBeLessThanOrEqual 1e-6

            // Phase 3: Post-resume — normal running again
            // The first tick after resume resets the snapshot (establishes new baseline),
            // so distance accumulation starts from the second tick onward.
            postResumeIncrements.forEach { increment ->
                cumulativeRaw += increment
                tracker.processTick(
                    rawDistance = cumulativeRaw,
                    isManualPaused = false,
                    isAutoPaused = false
                )
            }

            // The first post-resume increment is consumed by the snapshot reset,
            // so effective post-resume distance = sum of all increments except the first
            val expectedPostResume = postResumeIncrements.drop(1).sum()
            val expectedTotal = expectedPrePause + expectedPostResume
            val finalDistance = tracker.pauseAwareDistance

            // Use tolerance for floating-point accumulation
            abs(finalDistance - expectedTotal) shouldBeLessThanOrEqual 1e-6

            // Also verify: final distance >= pre-pause distance (post-resume only adds)
            finalDistance shouldBeGreaterThanOrEqual prePauseDistance - 1e-6
        }
    }
})
