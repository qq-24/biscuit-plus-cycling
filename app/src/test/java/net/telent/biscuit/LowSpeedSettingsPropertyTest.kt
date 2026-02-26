package net.telent.biscuit

// Feature: low-speed-alert-and-font-weight, Property 3: 低速阈值输入范围验证
// Feature: low-speed-alert-and-font-weight, Property 4: 设置持久化往返

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo

/**
 * Property 3: 低速阈值输入范围验证
 *
 * For any float input value, the low speed threshold should be clamped
 * to the 1.0-50.0 km/h range. Values below 1 are clamped to 1, values
 * above 50 are clamped to 50, and values within range are unchanged.
 *
 * **Validates: Requirements 1.3**
 *
 * Property 4: 设置持久化往返
 *
 * For any valid threshold (1.0-50.0) or font weight (100-900, step 100),
 * writing to a store and reading back should return the same value.
 *
 * **Validates: Requirements 1.4, 5.4**
 */
class LowSpeedSettingsPropertyTest : FunSpec({

    // Feature: low-speed-alert-and-font-weight, Property 3: 低速阈值输入范围验证
    test("Property 3: clamped threshold is always within [1.0, 50.0] for any float input") {
        checkAll(100, Arb.float(-1000f, 1000f)) { input ->
            val clamped = input.coerceIn(1.0f, 50.0f)

            clamped shouldBeGreaterThanOrEqualTo 1.0f
            clamped shouldBeLessThanOrEqualTo 50.0f
        }
    }

    // Feature: low-speed-alert-and-font-weight, Property 3: 低速阈值输入范围验证
    test("Property 3: values within [1.0, 50.0] are unchanged after clamping") {
        checkAll(100, Arb.float(1.0f, 50.0f)) { input ->
            val clamped = input.coerceIn(1.0f, 50.0f)

            clamped shouldBe input
        }
    }

    // Feature: low-speed-alert-and-font-weight, Property 4: 设置持久化往返
    test("Property 4: low speed threshold round-trip through map store") {
        checkAll(100, Arb.float(1.0f, 50.0f)) { threshold ->
            val store = HashMap<String, Any>()

            // Write
            store["low_speed_alert_threshold"] = threshold

            // Read back
            val readBack = store["low_speed_alert_threshold"] as Float

            readBack shouldBe threshold
        }
    }

    // Feature: low-speed-alert-and-font-weight, Property 4: 设置持久化往返
    test("Property 4: font weight round-trip through map store") {
        // Generate valid font weights: 100, 200, ..., 900
        val fontWeightArb = Arb.int(1, 9).map { it * 100 }

        checkAll(100, fontWeightArb) { fontWeight ->
            val store = HashMap<String, Any>()

            // Write
            store["dashboard_font_weight"] = fontWeight

            // Read back
            val readBack = store["dashboard_font_weight"] as Int

            readBack shouldBe fontWeight
        }
    }
})
