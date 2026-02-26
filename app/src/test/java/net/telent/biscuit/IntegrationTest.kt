package net.telent.biscuit

import android.graphics.Color
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Simulates BikeActivity's switchFragment logic including status bar color decision.
 * Uses menu IDs to determine which fragment is the "track" fragment, mirroring
 * the production code's `fragment is TrackFragment` check.
 *
 * This avoids Android Fragment dependencies while testing the core logic:
 * TrackFragment → TRANSPARENT, everything else → theme default.
 */
private class FragmentSwitcherWithStatusBar {
    var activeMenuId: Int = -1

    // Tracks the current status bar color (simulates window.statusBarColor)
    var statusBarColor: Int = THEME_DEFAULT_COLOR

    fun switchFragment(menuItemId: Int) {
        activeMenuId = menuItemId

        // Status bar color logic — mirrors BikeActivity.switchFragment
        statusBarColor = if (menuItemId == MENU_TRACK) {
            Color.TRANSPARENT
        } else {
            THEME_DEFAULT_COLOR
        }
    }

    companion object {
        const val MENU_HOME = 1
        const val MENU_TRACK = 2
        const val MENU_SENSORS = 3
        const val MENU_HISTORY = 4
        const val MENU_SETTINGS = 5
        // Simulates the resolved colorPrimaryDark from theme
        const val THEME_DEFAULT_COLOR: Int = 0xFF1B1B1B.toInt()
    }
}

/**
 * Integration tests for cross-component flows.
 *
 * 1. Manual pause → auto-pause blocked → manual resume → auto-pause detection resumes (full flow)
 * 2. Fragment switching with correct status bar color switching
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 10.3, 10.4
 */
class IntegrationTest {

    // =========================================================================
    // Part 1: Full pause flow integration using AutoPauseLogic
    // =========================================================================

    @Nested
    @DisplayName("Pause flow: manual pause → auto-pause blocked → manual resume → auto-pause resumes")
    inner class PauseFlowIntegration {

        private lateinit var logic: AutoPauseLogic
        private val autoPauseSpeed = 3.0   // km/h threshold
        private val autoPauseDelay = 5     // seconds
        private lateinit var baseTime: Instant

        @BeforeEach
        fun setUp() {
            logic = AutoPauseLogic(
                isAutoPaused = false,
                isManualPaused = false,
                lowSpeedStartTime = null
            )
            baseTime = Instant.parse("2024-01-01T12:00:00Z")
        }

        @Test
        @DisplayName("Full flow: manual pause blocks auto-pause, manual resume re-enables it")
        fun fullPauseFlow() {
            // Step 1: Start with both pauses off
            assertFalse(logic.isManualPaused, "Initial: manual pause should be off")
            assertFalse(logic.isAutoPaused, "Initial: auto pause should be off")

            // Step 2: Activate manual pause
            logic.isManualPaused = true

            // Step 3: Feed low-speed ticks for longer than autoPauseDelay
            // Auto-pause should NOT trigger while manual pause is active
            for (i in 0..10) {
                val now = baseTime.plusSeconds(i.toLong())
                logic.processTick(
                    autoPauseEnabled = true,
                    currentSpeed = 1.0, // well below threshold
                    autoPauseSpeed = autoPauseSpeed,
                    autoPauseDelay = autoPauseDelay,
                    now = now
                )
                assertFalse(logic.isAutoPaused,
                    "Auto-pause must NOT trigger while manual pause is active (tick $i)")
            }

            // Step 4: Manual resume
            logic.isManualPaused = false

            // Step 5: Feed low-speed ticks again — auto-pause should eventually trigger
            val resumeBase = baseTime.plusSeconds(20)
            var autoPauseTriggered = false
            for (i in 0..10) {
                val now = resumeBase.plusSeconds(i.toLong())
                logic.processTick(
                    autoPauseEnabled = true,
                    currentSpeed = 1.0, // still below threshold
                    autoPauseSpeed = autoPauseSpeed,
                    autoPauseDelay = autoPauseDelay,
                    now = now
                )
                if (logic.isAutoPaused) {
                    autoPauseTriggered = true
                    break
                }
            }
            assertTrue(autoPauseTriggered,
                "Auto-pause should trigger after manual resume + sustained low speed")

            // Step 6: Feed a high-speed tick — auto-resume
            val highSpeedTime = resumeBase.plusSeconds(20)
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = 20.0, // well above threshold
                autoPauseSpeed = autoPauseSpeed,
                autoPauseDelay = autoPauseDelay,
                now = highSpeedTime
            )
            assertFalse(logic.isAutoPaused,
                "Auto-pause should clear (auto-resume) after high-speed tick")
        }

        @Test
        @DisplayName("Manual pause resets low-speed timer so auto-pause delay restarts on resume")
        fun manualPauseResetsLowSpeedTimer() {
            // Accumulate some low-speed time (but not enough to trigger auto-pause)
            for (i in 0..3) {
                logic.processTick(
                    autoPauseEnabled = true,
                    currentSpeed = 1.0,
                    autoPauseSpeed = autoPauseSpeed,
                    autoPauseDelay = autoPauseDelay,
                    now = baseTime.plusSeconds(i.toLong())
                )
            }
            assertFalse(logic.isAutoPaused, "Not enough time for auto-pause yet")

            // Manual pause — should reset the timer
            logic.isManualPaused = true
            logic.processTick(
                autoPauseEnabled = true,
                currentSpeed = 1.0,
                autoPauseSpeed = autoPauseSpeed,
                autoPauseDelay = autoPauseDelay,
                now = baseTime.plusSeconds(4)
            )
            assertNull(logic.lowSpeedStartTime,
                "Manual pause should reset lowSpeedStartTime")

            // Manual resume — timer starts fresh
            logic.isManualPaused = false
            val resumeBase = baseTime.plusSeconds(10)

            // Feed low-speed ticks for less than autoPauseDelay
            for (i in 0..3) {
                logic.processTick(
                    autoPauseEnabled = true,
                    currentSpeed = 1.0,
                    autoPauseSpeed = autoPauseSpeed,
                    autoPauseDelay = autoPauseDelay,
                    now = resumeBase.plusSeconds(i.toLong())
                )
            }
            assertFalse(logic.isAutoPaused,
                "Timer restarted from zero after manual resume, so auto-pause should not trigger yet")

            // Now exceed the delay
            for (i in 4..10) {
                logic.processTick(
                    autoPauseEnabled = true,
                    currentSpeed = 1.0,
                    autoPauseSpeed = autoPauseSpeed,
                    autoPauseDelay = autoPauseDelay,
                    now = resumeBase.plusSeconds(i.toLong())
                )
            }
            assertTrue(logic.isAutoPaused,
                "Auto-pause should trigger after full delay from resume point")
        }

        @Test
        @DisplayName("Multiple manual pause/resume cycles work correctly")
        fun multiplePauseResumeCycles() {
            // Cycle 1: manual pause → low speed → manual resume → auto-pause triggers
            logic.isManualPaused = true
            for (i in 0..10) {
                logic.processTick(true, 0.5, autoPauseSpeed, autoPauseDelay, baseTime.plusSeconds(i.toLong()))
                assertFalse(logic.isAutoPaused, "Cycle 1: auto-pause blocked during manual pause")
            }
            logic.isManualPaused = false
            val t1 = baseTime.plusSeconds(20)
            for (i in 0..10) {
                logic.processTick(true, 0.5, autoPauseSpeed, autoPauseDelay, t1.plusSeconds(i.toLong()))
            }
            assertTrue(logic.isAutoPaused, "Cycle 1: auto-pause should trigger after resume")

            // Auto-resume with high speed
            logic.processTick(true, 25.0, autoPauseSpeed, autoPauseDelay, t1.plusSeconds(15))
            assertFalse(logic.isAutoPaused, "Cycle 1: auto-resume on high speed")

            // Cycle 2: same pattern should work again
            logic.isManualPaused = true
            val t2 = t1.plusSeconds(20)
            for (i in 0..10) {
                logic.processTick(true, 0.5, autoPauseSpeed, autoPauseDelay, t2.plusSeconds(i.toLong()))
                assertFalse(logic.isAutoPaused, "Cycle 2: auto-pause blocked during manual pause")
            }
            logic.isManualPaused = false
            val t3 = t2.plusSeconds(20)
            for (i in 0..10) {
                logic.processTick(true, 0.5, autoPauseSpeed, autoPauseDelay, t3.plusSeconds(i.toLong()))
            }
            assertTrue(logic.isAutoPaused, "Cycle 2: auto-pause should trigger after second resume")
        }
    }

    // =========================================================================
    // Part 2: Fragment switching with status bar color logic
    // =========================================================================

    @Nested
    @DisplayName("Fragment switching with status bar color")
    inner class StatusBarColorIntegration {

        private lateinit var switcher: FragmentSwitcherWithStatusBar

        @BeforeEach
        fun setUp() {
            switcher = FragmentSwitcherWithStatusBar()
        }

        @Test
        @DisplayName("TrackFragment sets status bar to TRANSPARENT")
        fun trackFragmentSetsTransparentStatusBar() {
            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_TRACK)

            assertEquals(Color.TRANSPARENT, switcher.statusBarColor,
                "Status bar should be TRANSPARENT when TrackFragment is active")
        }

        @Test
        @DisplayName("Non-TrackFragment sets status bar to theme default")
        fun nonTrackFragmentSetsThemeDefaultStatusBar() {
            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_HOME)

            assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor,
                "Status bar should be theme default when non-TrackFragment is active")
        }

        @Test
        @DisplayName("Switching from TrackFragment to HomeFragment restores theme default")
        fun switchFromTrackToHomeRestoresDefault() {
            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_TRACK)
            assertEquals(Color.TRANSPARENT, switcher.statusBarColor)

            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_HOME)
            assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor,
                "Switching away from TrackFragment should restore theme default status bar color")
        }

        @Test
        @DisplayName("Switching from HomeFragment to TrackFragment sets transparent")
        fun switchFromHomeToTrackSetsTransparent() {
            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_HOME)
            assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor)

            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_TRACK)
            assertEquals(Color.TRANSPARENT, switcher.statusBarColor,
                "Switching to TrackFragment should set status bar to TRANSPARENT")
        }

        @Test
        @DisplayName("Multiple fragment switches maintain correct status bar color")
        fun multipleFragmentSwitchesMaintainCorrectColor() {
            // Home → Track → Sensors → Track → Home
            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_HOME)
            assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor,
                "Home: theme default")

            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_TRACK)
            assertEquals(Color.TRANSPARENT, switcher.statusBarColor,
                "Track: transparent")

            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_SENSORS)
            assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor,
                "Sensors: theme default")

            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_TRACK)
            assertEquals(Color.TRANSPARENT, switcher.statusBarColor,
                "Track again: transparent")

            switcher.switchFragment(FragmentSwitcherWithStatusBar.MENU_HOME)
            assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor,
                "Home again: theme default")
        }

        @Test
        @DisplayName("All non-track fragments use theme default color")
        fun allNonTrackFragmentsUseThemeDefault() {
            val nonTrackMenuIds = listOf(
                FragmentSwitcherWithStatusBar.MENU_HOME,
                FragmentSwitcherWithStatusBar.MENU_SENSORS,
                FragmentSwitcherWithStatusBar.MENU_HISTORY,
                FragmentSwitcherWithStatusBar.MENU_SETTINGS
            )
            for (menuId in nonTrackMenuIds) {
                switcher.switchFragment(menuId)
                assertEquals(FragmentSwitcherWithStatusBar.THEME_DEFAULT_COLOR, switcher.statusBarColor,
                    "Menu ID $menuId should use theme default status bar color")
            }
        }
    }
}
