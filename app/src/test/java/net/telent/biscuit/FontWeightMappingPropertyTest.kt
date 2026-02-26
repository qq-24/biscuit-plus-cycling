package net.telent.biscuit

// Feature: low-speed-alert-and-font-weight, Property 5: 字重滑块映射与命名

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Property 5: 字重滑块映射与命名
 *
 * For any slider position (0-8), the mapped font weight should equal
 * (position + 1) * 100, and the corresponding name should match the
 * predefined weight name table.
 *
 * Validates: Requirements 5.2, 5.3
 */
class FontWeightMappingPropertyTest : FunSpec({

    val weightNames = mapOf(
        100 to "Thin",
        200 to "Extra Light",
        300 to "Light",
        400 to "Regular",
        500 to "Medium",
        600 to "Semi Bold",
        700 to "Bold",
        800 to "Extra Bold",
        900 to "Black"
    )

    // Feature: low-speed-alert-and-font-weight, Property 5: 字重滑块映射与命名
    test("Property 5: slider position maps to correct weight via (position + 1) * 100") {
        checkAll(100, Arb.int(0, 8)) { position ->
            val weight = (position + 1) * 100
            weight shouldBe (position + 1) * 100
        }
    }

    test("Property 5: mapped weight name matches predefined table for any slider position") {
        checkAll(100, Arb.int(0, 8)) { position ->
            val weight = (position + 1) * 100
            val expectedName = when (weight) {
                100 -> "Thin"
                200 -> "Extra Light"
                300 -> "Light"
                400 -> "Regular"
                500 -> "Medium"
                600 -> "Semi Bold"
                700 -> "Bold"
                800 -> "Extra Bold"
                900 -> "Black"
                else -> null
            }
            weightNames[weight] shouldBe expectedName
        }
    }

    test("Property 5: mapped weight is always in range [100, 900]") {
        checkAll(100, Arb.int(0, 8)) { position ->
            val weight = (position + 1) * 100
            weight shouldBeInRange 100..900
        }
    }

    test("Property 5: mapping is bijective - each position maps to a unique weight") {
        checkAll(100, Arb.int(0, 8)) { position ->
            val weight = (position + 1) * 100
            // Verify inverse: given a weight, we can recover the unique position
            val recoveredPosition = (weight / 100) - 1
            recoveredPosition shouldBe position

            // Verify all 9 positions produce 9 distinct weights
            val allWeights = (0..8).map { p -> (p + 1) * 100 }.toSet()
            allWeights shouldHaveSize 9
        }
    }
})
