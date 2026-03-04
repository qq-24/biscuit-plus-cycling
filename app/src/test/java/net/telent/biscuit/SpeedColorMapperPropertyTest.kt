package net.telent.biscuit

// Feature: cycling-ux-enhancements, Property 2: SpeedColorMapper 单调性
// Feature: cycling-ux-enhancements, Property 3: SpeedColorMapper 边界值

import android.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.mockk.every
import io.mockk.mockkStatic

/**
 * Property 2: SpeedColorMapper 单调性
 * 速度越高颜色越接近绿色，速度越低越接近红色。
 *
 * Property 3: SpeedColorMapper 边界值
 * speed == minSpeed 时返回 COLOR_SLOW，speed == maxSpeed 时返回 COLOR_FAST。
 */
class SpeedColorMapperPropertyTest : FunSpec({

    beforeSpec {
        // Mock ColorUtils.blendARGB，因为单元测试中 Android 框架不可用
        mockkStatic(androidx.core.graphics.ColorUtils::class)
        every { androidx.core.graphics.ColorUtils.blendARGB(any(), any(), any()) } answers {
            val color1 = firstArg<Int>()
            val color2 = secondArg<Int>()
            val ratio = thirdArg<Float>()
            // 简单线性插值实现
            val r1 = (color1 shr 16) and 0xFF
            val g1 = (color1 shr 8) and 0xFF
            val b1 = color1 and 0xFF
            val r2 = (color2 shr 16) and 0xFF
            val g2 = (color2 shr 8) and 0xFF
            val b2 = color2 and 0xFF
            val r = (r1 + (r2 - r1) * ratio).toInt().coerceIn(0, 255)
            val g = (g1 + (g2 - g1) * ratio).toInt().coerceIn(0, 255)
            val b = (b1 + (b2 - b1) * ratio).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    test("Property 2: 速度越高红色分量越小（单调性）") {
        val speedArb = Arb.float(0.1f, 60.0f)
        val minSpeedArb = Arb.float(0.1f, 10.0f)

        checkAll(100, speedArb, speedArb, minSpeedArb) { speed1Raw, speed2Raw, minSpeed ->
            val maxSpeed = minSpeed + 30f
            // 确保 speed1 < speed2
            val s1 = speed1Raw.coerceIn(minSpeed, maxSpeed)
            val s2 = speed2Raw.coerceIn(minSpeed, maxSpeed)
            if (s1 < s2) {
                val color1 = SpeedColorMapper.getColor(s1, minSpeed, maxSpeed)
                val color2 = SpeedColorMapper.getColor(s2, minSpeed, maxSpeed)
                val red1 = (color1 shr 16) and 0xFF
                val red2 = (color2 shr 16) and 0xFF
                // 速度低 → 更红，速度高 → 更绿
                red1 shouldBeGreaterThanOrEqual red2
            }
        }
    }

    test("Property 3: 边界值 - minSpeed 返回 COLOR_SLOW，maxSpeed 返回 COLOR_FAST") {
        val minSpeedArb = Arb.float(0.1f, 20.0f)
        val rangeArb = Arb.float(1.0f, 50.0f)

        checkAll(50, minSpeedArb, rangeArb) { minSpeed, range ->
            val maxSpeed = minSpeed + range

            val colorAtMin = SpeedColorMapper.getColor(minSpeed, minSpeed, maxSpeed)
            val colorAtMax = SpeedColorMapper.getColor(maxSpeed, minSpeed, maxSpeed)

            colorAtMin shouldBe SpeedColorMapper.COLOR_SLOW
            colorAtMax shouldBe SpeedColorMapper.COLOR_FAST
        }
    }

    test("maxSpeed <= minSpeed 时返回 COLOR_FAST") {
        val speedArb = Arb.float(0.1f, 60.0f)

        checkAll(50, speedArb, speedArb) { speed, minSpeed ->
            val color = SpeedColorMapper.getColor(speed, minSpeed, minSpeed)
            color shouldBe SpeedColorMapper.COLOR_FAST
        }
    }
})
