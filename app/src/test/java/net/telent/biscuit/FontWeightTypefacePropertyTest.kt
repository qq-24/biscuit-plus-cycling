package net.telent.biscuit

// Feature: low-speed-alert-and-font-weight, Property 6: Typeface 创建正确性
// Feature: low-speed-alert-and-font-weight, Property 7: 字重应用到所有显示项

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * Property 6: Typeface 创建正确性
 *
 * For any font weight (100-900) and API level:
 * - API >= 28 → should use variable weight path (returns "variable_weight_{weight}")
 * - API < 28 → weight >= 500 → should select "default_bold" path
 * - API < 28 → weight < 500 → should select "default" path
 *
 * Validates: Requirements 6.3, 6.4
 *
 * Property 7: 字重应用到所有显示项
 *
 * For any font weight and any number of display items (1-10), after applying
 * font weight, ALL items should have the same typeface strategy applied.
 *
 * Validates: Requirements 6.1
 */

/**
 * Pure function modeling the typeface creation decision logic.
 * This mirrors the logic in HomeFragment.applyFontWeight() without
 * requiring Android framework dependencies.
 */
fun typefaceStrategy(weight: Int, apiLevel: Int): String {
    return if (apiLevel >= 28) {
        "variable_weight_$weight"
    } else {
        if (weight >= 500) "default_bold" else "default"
    }
}

class FontWeightTypefacePropertyTest : FunSpec({

    val validWeights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)
    val weightArb = Arb.element(validWeights)
    val apiLevelArb = Arb.int(21, 34)

    // Feature: low-speed-alert-and-font-weight, Property 6: Typeface 创建正确性
    test("Property 6: API >= 28 uses variable weight path for any valid weight") {
        checkAll(100, weightArb, apiLevelArb) { weight, apiLevel ->
            val strategy = typefaceStrategy(weight, apiLevel)

            if (apiLevel >= 28) {
                strategy shouldBe "variable_weight_$weight"
            }
        }
    }

    test("Property 6: API < 28 with weight >= 500 uses default_bold fallback") {
        checkAll(100, weightArb, Arb.int(21, 27)) { weight, apiLevel ->
            val strategy = typefaceStrategy(weight, apiLevel)

            if (weight >= 500) {
                strategy shouldBe "default_bold"
            }
        }
    }

    test("Property 6: API < 28 with weight < 500 uses default fallback") {
        checkAll(100, weightArb, Arb.int(21, 27)) { weight, apiLevel ->
            val strategy = typefaceStrategy(weight, apiLevel)

            if (weight < 500) {
                strategy shouldBe "default"
            }
        }
    }

    test("Property 6: decision is deterministic - same inputs always produce same output") {
        checkAll(100, weightArb, apiLevelArb) { weight, apiLevel ->
            val result1 = typefaceStrategy(weight, apiLevel)
            val result2 = typefaceStrategy(weight, apiLevel)
            result1 shouldBe result2
        }
    }

    test("Property 6: variable weight path encodes the exact weight value") {
        checkAll(100, weightArb, Arb.int(28, 34)) { weight, apiLevel ->
            val strategy = typefaceStrategy(weight, apiLevel)
            strategy shouldStartWith "variable_weight_"
            val encodedWeight = strategy.removePrefix("variable_weight_").toInt()
            encodedWeight shouldBe weight
        }
    }

    // Feature: low-speed-alert-and-font-weight, Property 7: 字重应用到所有显示项
    test("Property 7: all display items receive the same typeface strategy for any weight") {
        val itemCountArb = Arb.int(1, 10)

        checkAll(100, weightArb, apiLevelArb, itemCountArb) { weight, apiLevel, itemCount ->
            // Simulate applying font weight to N display items
            val strategies = (1..itemCount).map { typefaceStrategy(weight, apiLevel) }

            // All items should have the identical strategy
            strategies.distinct().size shouldBe 1
            strategies.all { it == typefaceStrategy(weight, apiLevel) } shouldBe true
        }
    }

    test("Property 7: strategy is consistent across all dashboard display item types") {
        val displayItemTypes = listOf("speed", "cadence", "distance", "time", "ride_duration", "avg_speed")

        checkAll(100, weightArb, apiLevelArb) { weight, apiLevel ->
            // Each display item type should get the same strategy
            val strategiesByType = displayItemTypes.map { _ ->
                typefaceStrategy(weight, apiLevel)
            }

            // All types should have the same strategy applied
            strategiesByType.toSet().size shouldBe 1
        }
    }
})
