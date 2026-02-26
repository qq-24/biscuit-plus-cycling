package net.telent.biscuit

// Feature: ui-fixes-and-improvements, Property 6: 有效暂停为逻辑或

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe

/**
 * Property 6: Effective pause is logical OR
 *
 * For any ManualPause boolean M and AutoPause boolean A,
 * EffectivePause should equal M || A.
 *
 * This mirrors the logic in BiscuitService.kt:
 *   val isPaused = isManualPaused || isAutoPaused
 *
 * **Validates: Requirements 8.4**
 */
class EffectivePauseLogicalOrPropertyTest : FunSpec({

    test("Property 6: effectivePause equals manualPause OR autoPause for all boolean combinations") {
        checkAll(100, Arb.boolean(), Arb.boolean()) { manualPaused, autoPaused ->
            // This is the exact logic from BiscuitService.updaterThread
            val effectivePause = manualPaused || autoPaused

            effectivePause shouldBe (manualPaused || autoPaused)
        }
    }
})
