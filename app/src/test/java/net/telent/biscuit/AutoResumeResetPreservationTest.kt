package net.telent.biscuit

// Feature: auto-pause-resume-reset, Property 2: Preservation
// 非 bug 条件下行为不变

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Preservation Property Test
 *
 * 验证非 bug 条件下（非"自动恢复后速度再次降低"场景）的行为保持不变。
 * 此测试在未修复代码上应通过，修复后也应继续通过。
 *
 * 覆盖场景:
 * - 首次低速→等待延迟→自动暂停（3.1）
 * - 持续高速运行（3.2）
 * - 手动暂停期间的状态（3.5）
 * - 自动暂停功能关闭（3.4）
 * - 冷却期内的行为（3.3）
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 */
class AutoResumeResetPreservationTest : FunSpec({

    /**
     * Property 2a: 首次低速持续超过 autoPauseDelay 后正确触发自动暂停
     *
     * 非 bug 条件: 从未触发过自动暂停，首次低速
     * 观察行为: lowSpeedStartTime 被设置，持续低速超过 delay 后 isAutoPaused = true
     */
    test("Property 2a: first low-speed triggers auto-pause after delay") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)

        checkAll(100, thresholdArb, delayArb) { threshold, delay ->
            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )

            val baseTime = Instant.parse("2024-01-01T00:00:00Z")
            val lowSpeed = threshold * 0.5

            // 第一个低速 tick
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = baseTime
            )
            logic.lowSpeedStartTime shouldBe baseTime
            logic.isAutoPaused shouldBe false

            // 等待超过 delay 的时间
            val afterDelay = baseTime.plusSeconds(delay.toLong() + 1)
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = afterDelay
            )
            logic.isAutoPaused shouldBe true
        }
    }

    /**
     * Property 2b: 持续高速运行时不触发自动暂停
     *
     * 非 bug 条件: 速度始终高于阈值
     * 观察行为: isAutoPaused 保持 false，lowSpeedStartTime 保持 null
     */
    test("Property 2b: continuous high speed never triggers auto-pause") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)
        val highSpeedOffsetArb = Arb.double(0.1, 50.0)

        checkAll(100, thresholdArb, delayArb, highSpeedOffsetArb) { threshold, delay, offset ->
            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )

            val baseTime = Instant.parse("2024-01-01T00:00:00Z")
            val highSpeed = threshold + offset

            // 多个高速 tick
            for (i in 0..10) {
                val now = baseTime.plusSeconds(i * 5L)
                logic.processTick(
                    autoPauseEnabled = true,
                    currentSpeed = highSpeed,
                    autoPauseSpeed = threshold,
                    autoPauseDelay = delay,
                    now = now
                )
                logic.isAutoPaused shouldBe false
                logic.lowSpeedStartTime shouldBe null
            }
        }
    }

    /**
     * Property 2c: 手动暂停期间（低速时）lowSpeedStartTime 被重置，isAutoPaused 不变
     *
     * 非 bug 条件: isManualPaused = true，速度低于阈值（不触发自动恢复）
     * 观察行为: lowSpeedStartTime = null，isAutoPaused 不改变
     *
     * 注意: 当 autoPauseEnabled=true 且速度>=阈值时，系统会自动恢复手动暂停，
     * 这是预期的新行为，不属于此保持性测试的范围。
     */
    test("Property 2c: manual pause resets lowSpeedStartTime and preserves isAutoPaused") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)
        val initialAutoPausedArb = Arb.boolean()

        checkAll(100, thresholdArb, delayArb, initialAutoPausedArb) { threshold, delay, initialAP ->
            // 使用低于阈值的速度，确保不触发手动暂停的自动恢复
            val lowSpeed = threshold * 0.5

            val logic = AutoPauseLogic(
                isAutoPaused = initialAP,
                isManualPaused = true,
                lowSpeedStartTime = Instant.parse("2024-01-01T00:00:00Z") // 有旧值
            )

            val now = Instant.parse("2024-01-01T01:00:00Z")
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = now
            )

            logic.lowSpeedStartTime shouldBe null
            logic.isAutoPaused shouldBe initialAP
        }
    }

    /**
     * Property 2d: 自动暂停功能关闭时，isAutoPaused 保持 false
     *
     * 非 bug 条件: autoPauseEnabled = false
     * 观察行为: isAutoPaused = false，lowSpeedStartTime = null
     */
    test("Property 2d: auto-pause disabled keeps isAutoPaused false") {
        val speedArb = Arb.double(0.0, 50.0)
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)

        checkAll(100, speedArb, thresholdArb, delayArb) { speed, threshold, delay ->
            val logic = AutoPauseLogic(
                isAutoPaused = true, // 即使初始为 true
                isManualPaused = false,
                lowSpeedStartTime = Instant.parse("2024-01-01T00:00:00Z")
            )

            val now = Instant.parse("2024-01-01T01:00:00Z")
            logic.processTick(
                autoPauseEnabled = false,
                currentSpeed = speed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = now
            )

            logic.isAutoPaused shouldBe false
            logic.lowSpeedStartTime shouldBe null
        }
    }

    /**
     * Property 2e: 冷却期内不触发自动暂停
     *
     * 非 bug 条件: now < resumeCooldownUntil
     * 观察行为: lowSpeedStartTime = null，不触发自动暂停
     */
    test("Property 2e: cooldown period prevents auto-pause") {
        val speedArb = Arb.double(0.0, 2.0) // 低速
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)

        checkAll(100, speedArb, thresholdArb, delayArb) { speed, threshold, delay ->
            val now = Instant.parse("2024-01-01T00:00:30Z")
            val cooldownUntil = Instant.parse("2024-01-01T00:01:00Z") // 冷却期未结束

            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null,
                resumeCooldownUntil = cooldownUntil
            )

            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = speed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = now
            )

            logic.lowSpeedStartTime shouldBe null
            logic.isAutoPaused shouldBe false
        }
    }

    /**
     * Property 2f: 低速持续时间不足 autoPauseDelay 时不触发自动暂停
     *
     * 非 bug 条件: 首次低速但持续时间不足
     * 观察行为: isAutoPaused 保持 false
     */
    test("Property 2f: low speed duration less than delay does not trigger auto-pause") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(5, 60)

        checkAll(100, thresholdArb, delayArb) { threshold, delay ->
            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )

            val baseTime = Instant.parse("2024-01-01T00:00:00Z")
            val lowSpeed = threshold * 0.5

            // 第一个低速 tick
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = baseTime
            )

            // 等待不足 delay 的时间（delay - 2 秒，最少 1 秒）
            val shortWait = baseTime.plusSeconds(maxOf(1, delay - 2).toLong())
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = shortWait
            )

            logic.isAutoPaused shouldBe false
        }
    }
})
