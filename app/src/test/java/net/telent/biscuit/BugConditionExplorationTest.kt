package net.telent.biscuit

// Feature: p1-stability-and-build-fixes, Property 1: Fault Condition
// Bug 2 - versionName 日期格式错误 & Bug 4 - Trackpoint 删除缺失

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Bug Condition Exploration Tests (Pre-fix)
 *
 * These tests are EXPECTED TO FAIL on unfixed code.
 * Failure confirms the bugs exist.
 *
 * Validates: Requirements 1.2, 1.4, 2.2, 2.4
 */
class BugConditionExplorationTest : FunSpec({

    /**
     * Bug 2 - versionName 格式 PBT
     *
     * Uses the CURRENT buggy format 'yyyymmddHHMMSS' to format random dates,
     * then asserts that month, minute, and second fields are correct.
     *
     * This SHOULD FAIL because:
     * - 'mm' produces minutes, not months
     * - 'MM' produces months, not minutes
     * - 'SS' is not a valid SimpleDateFormat pattern (produces literal "SS" or milliseconds depending on impl)
     *
     * **Validates: Requirements 1.2, 2.2**
     */
    test("Bug 2: versionName buggy format 'yyyymmddHHMMSS' should produce correct month, minute, second fields") {
        // Use the BUGGY format string from build.gradle
        val buggyFormat = SimpleDateFormat("yyyymmddHHMMSS", Locale.US)

        // Generate random timestamps within a reasonable range (2020-2030)
        val minMs = SimpleDateFormat("yyyy", Locale.US).parse("2020")!!.time
        val maxMs = SimpleDateFormat("yyyy", Locale.US).parse("2030")!!.time

        checkAll(200, Arb.long(minMs, maxMs)) { epochMs ->
            val date = Date(epochMs)
            val formatted = buggyFormat.format(date)

            // Extract the input date's actual month, minute, second
            val cal = Calendar.getInstance()
            cal.time = date
            val expectedMonth = String.format("%02d", cal.get(Calendar.MONTH) + 1) // Calendar.MONTH is 0-based
            val expectedMinute = String.format("%02d", cal.get(Calendar.MINUTE))
            val expectedSecond = String.format("%02d", cal.get(Calendar.SECOND))

            // In the format 'yyyymmddHHMMSS':
            // positions 0-3: yyyy (year)
            // positions 4-5: mm (should be month, but buggy format gives minutes)
            // positions 6-7: dd (day)
            // positions 8-9: HH (hour)
            // positions 10-11: MM (should be minutes, but buggy format gives months)
            // positions 12-13: SS (should be seconds, but SS is invalid)

            val monthField = formatted.substring(4, 6)   // positions 4-5
            val minuteField = formatted.substring(10, 12) // positions 10-11
            val secondField = formatted.substring(12, 14) // positions 12-13

            // These assertions SHOULD FAIL on buggy code:
            // monthField will contain minutes (mm=minutes), not months
            // minuteField will contain months (MM=months), not minutes
            // secondField will contain "SS" literal or wrong value, not seconds
            monthField shouldBe expectedMonth
            minuteField shouldBe expectedMinute
            secondField shouldBe expectedSecond
        }
    }

    /**
     * Bug 4 - Trackpoint 删除 PBT
     *
     * Verifies that TrackpointDao has a deleteByTimeRange method.
     * This SHOULD FAIL on unfixed code because the method doesn't exist yet.
     *
     * We use reflection to check for the method's existence, which is a valid
     * approach since the bug is that the method is missing entirely.
     *
     * **Validates: Requirements 1.4, 2.4**
     */
    test("Bug 4: TrackpointDao should have deleteByTimeRange method") {
        val daoClass = TrackpointDao::class.java

        // Check that deleteByTimeRange method exists
        val deleteMethod = daoClass.methods.find { it.name == "deleteByTimeRange" }

        // This SHOULD FAIL on unfixed code because the method doesn't exist
        (deleteMethod != null) shouldBe true

        // If method exists, verify it takes two Instant parameters
        if (deleteMethod != null) {
            deleteMethod.parameterCount shouldBe 2
        }
    }
})
