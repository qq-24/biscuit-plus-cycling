package net.telent.biscuit

// Feature: p0-security-crash-fixes, Property 6: Preservation - 正常 Session.duration() 行为

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

/**
 * Property 6 (Preservation): Normal Session.duration() behavior
 *
 * For any Session instance, when session.end != null and end >= start,
 * session.duration() SHALL continue to return Duration.between(start, end) correctly.
 *
 * Validates: Requirements 3.2
 */
class SessionDurationPreservationPropertyTest : FunSpec({

    test("Property 6: duration() returns Duration.between(start, end) for any Session with non-null end >= start") {
        // Use a safe range for epoch seconds to avoid overflow in Duration.between
        val safeMin = -31557014167219200L  // Instant.MIN.epochSecond + 1
        val safeMax = 31556889864403199L   // Instant.MAX.epochSecond - 1

        val startEpochArb = Arb.long(min = safeMin, max = safeMax - 1)

        checkAll(200, startEpochArb) { startEpoch ->
            val start = Instant.ofEpochSecond(startEpoch)

            // Generate end >= start, clamped to safe max
            val endEpoch = Arb.long(min = startEpoch, max = safeMax)
            checkAll(1, endEpoch) { ep ->
                val end = Instant.ofEpochSecond(ep)
                val session = Session(start = start, end = end)

                val result = session.duration()
                val expected = Duration.between(start, end)

                result shouldBe expected
            }
        }
    }
})
