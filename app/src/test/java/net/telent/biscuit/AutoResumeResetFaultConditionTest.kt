package net.telent.biscuit

// Feature: auto-pause-resume-reset, Property 1: Fault Condition
// 自动恢复后 lowSpeedStartTime 未正确重置

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import java.time.Instant

/**
 * Bug Condition Exploration Test
 *
 * Bug 条件: 自动暂停已触发 → 速度回升超过阈值（自动恢复）→ 速度再次降低到阈值以下
 * 期望行为: lowSpeedStartTime 应被设置为当前时刻（now），isAutoPaused 应保持 false
 *
 * 此测试在未修复代码上运行时预期失败，以确认 bug 存在。
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3**
 */
class AutoResumeResetFaultConditionTest : FunSpec({

    /**
     * Property 1a: 自动恢复后，lowSpeedStartTime 应在恢复 tick 被重置为 null
     *
     * 模拟: 低速持续 → 自动暂停触发 → 高速 tick（自动恢复）
     * 断言: 恢复后 lowSpeedStartTime == null
     */
    test("Property 1a: lowSpeedStartTime is null after auto-resume") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(1, 30)
        val highSpeedOffsetArb = Arb.double(0.1, 30.0)

        checkAll(100, thresholdArb, delayArb, highSpeedOffsetArb) { threshold, delay, highOffset ->
            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )

            val baseTime = Instant.parse("2024-01-01T00:00:00Z")
            val lowSpeed = threshold * 0.5 // 明确低于阈值

            // Phase 1: 低速持续直到自动暂停触发
            // 第一个 tick 设置 lowSpeedStartTime
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = baseTime
            )

            // 跳过足够时间以满足 autoPauseDelay
            val pauseTime = baseTime.plusSeconds(delay.toLong() + 1)
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = pauseTime
            )
            // 确认自动暂停已触发
            logic.isAutoPaused shouldBe true

            // Phase 2: 速度回升超过阈值 → 自动恢复
            val resumeTime = pauseTime.plusSeconds(2)
            val highSpeed = threshold + highOffset
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = highSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = resumeTime
            )

            // 断言: 恢复后 lowSpeedStartTime 应为 null
            logic.isAutoPaused shouldBe false
            logic.lowSpeedStartTime.shouldBeNull()
        }
    }

    /**
     * Property 1b: 自动恢复后速度再次降低时，lowSpeedStartTime 应设置为当前时刻
     *
     * 模拟: 自动暂停 → 高速恢复 → 再次低速
     * 断言: lowSpeedStartTime == 再次低速的时刻（now），isAutoPaused == false
     *
     * 这是核心 bug 条件测试。
     */
    test("Property 1b: lowSpeedStartTime resets to current time after auto-resume then low speed") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(2, 30)
        val highSpeedOffsetArb = Arb.double(0.1, 30.0)

        checkAll(100, thresholdArb, delayArb, highSpeedOffsetArb) { threshold, delay, highOffset ->
            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )

            val baseTime = Instant.parse("2024-01-01T00:00:00Z")
            val lowSpeed = threshold * 0.5

            // Phase 1: 触发自动暂停
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = baseTime
            )
            val pauseTime = baseTime.plusSeconds(delay.toLong() + 1)
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = pauseTime
            )
            logic.isAutoPaused shouldBe true

            // Phase 2: 速度回升 → 自动恢复
            val resumeTime = pauseTime.plusSeconds(2)
            val highSpeed = threshold + highOffset
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = highSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = resumeTime
            )
            logic.isAutoPaused shouldBe false

            // Phase 3: 速度再次降低到阈值以下（在冷却期之后）
            val reDropTime = resumeTime.plusSeconds(5) // 超过 3 秒冷却期
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = reDropTime
            )

            // 核心断言: lowSpeedStartTime 应为 reDropTime（当前时刻），不是旧值
            logic.lowSpeedStartTime shouldBe reDropTime
            // 不应立即再次暂停
            logic.isAutoPaused shouldBe false
        }
    }

    /**
     * Property 1c: 自动恢复后不应立即再次暂停（即使低速持续时间加上旧计时超过 delay）
     *
     * 模拟: 自动暂停 → 短暂高速恢复 → 再次低速 → 等待不足 autoPauseDelay 的时间
     * 断言: 不应触发自动暂停（因为应从新时间起点计时）
     */
    test("Property 1c: no immediate re-pause after auto-resume even with short high-speed gap") {
        val thresholdArb = Arb.double(2.0, 15.0)
        val delayArb = Arb.int(5, 30)

        checkAll(100, thresholdArb, delayArb) { threshold, delay ->
            val logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )

            val baseTime = Instant.parse("2024-01-01T00:00:00Z")
            val lowSpeed = threshold * 0.5
            val highSpeed = threshold + 1.0

            // Phase 1: 触发自动暂停
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = baseTime
            )
            val pauseTime = baseTime.plusSeconds(delay.toLong() + 1)
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = pauseTime
            )
            logic.isAutoPaused shouldBe true

            // Phase 2: 短暂高速恢复（仅 1 秒）
            val resumeTime = pauseTime.plusSeconds(1)
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = highSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = resumeTime
            )
            logic.isAutoPaused shouldBe false

            // Phase 3: 速度再次降低（在冷却期之后）
            val reDropTime = resumeTime.plusSeconds(5) // 超过 3 秒冷却期
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = reDropTime
            )

            // Phase 4: 等待不足 autoPauseDelay 的时间（delay/2 秒）
            // 如果 bug 存在（使用旧计时），这里可能已经超过 delay 导致立即暂停
            val shortWaitTime = reDropTime.plusSeconds((delay / 2).toLong())
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = lowSpeed,
                autoPauseSpeed = threshold,
                autoPauseDelay = delay,
                now = shortWaitTime
            )

            // 核心断言: 不应暂停，因为从 reDropTime 开始计时，只过了 delay/2 秒
            logic.isAutoPaused shouldBe false
        }
    }
})
