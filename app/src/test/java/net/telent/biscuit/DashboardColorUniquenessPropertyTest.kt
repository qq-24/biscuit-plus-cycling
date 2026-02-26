package net.telent.biscuit

// Feature: ui-fixes-and-improvements, Property 1: 仪表盘颜色唯一性

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import net.telent.biscuit.ui.home.HomeFragment

/**
 * Property 1: Dashboard color uniqueness
 *
 * For any two different DisplayItem types, their assigned color values
 * SHALL not be the same. No duplicate values in the color mapping.
 *
 * Uses DISPLAY_COLOR_HEX (raw hex strings) to avoid Android Color.parseColor()
 * returning default values in unit test environment.
 *
 * **Validates: Requirements 4.1**
 */
class DashboardColorUniquenessPropertyTest : FunSpec({

    test("Property 1: all color values in DISPLAY_COLOR_HEX are unique — no two keys share the same color") {
        val colorHex = HomeFragment.DISPLAY_COLOR_HEX

        // Structural check: the number of distinct values must equal the number of entries
        colorHex.values.toSet().size shouldBe colorHex.size

        // Property-based: generate all pairs of distinct keys and verify their colors differ
        val keys = colorHex.keys.toList()
        val distinctPairs = keys.flatMap { a -> keys.filter { b -> a < b }.map { b -> a to b } }

        checkAll(100, Arb.of(distinctPairs)) { (key1, key2) ->
            val color1 = colorHex[key1]
            val color2 = colorHex[key2]
            color1 shouldNotBe color2
        }
    }
})
