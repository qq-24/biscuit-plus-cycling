package net.telent.biscuit

// Feature: p0-security-crash-fixes, Property 2: Session.duration() 空安全

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

/**
 * Property 2 (Fix Verification): Session.duration() null-safety
 *
 * For any Session instance, when session.end == null,
 * session.duration() SHALL return Duration.ZERO and NOT throw NullPointerException.
 *
 * Validates: Requirements 2.2
 */
class SessionDurationNullEndPropertyTest : FunSpec({

    test("Property 2: duration() returns Duration.ZERO for any Session with end=null") {
        // Generate random epoch seconds covering a wide range of valid Instant values
        val epochSecondArb = Arb.long(
            min = Instant.MIN.epochSecond + 1,
            max = Instant.MAX.epochSecond - 1
        )

        checkAll(200, epochSecondArb) { epochSecond ->
            val start = Instant.ofEpochSecond(epochSecond)
            val session = Session(start = start, end = null)

            // Must not throw any exception and must return Duration.ZERO
            val result = session.duration()
            result shouldBe Duration.ZERO
        }
    }
})
