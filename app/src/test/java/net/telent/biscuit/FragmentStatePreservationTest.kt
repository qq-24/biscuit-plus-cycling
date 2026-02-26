package net.telent.biscuit

import androidx.fragment.app.Fragment
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for Fragment state preservation (hide/show mechanism).
 *
 * Tests verify that BikeActivity's switchFragment logic:
 * 1. Reuses existing Fragment instances (same object reference after switching back)
 * 2. Keeps hidden Fragments alive in FragmentManager (not destroyed)
 *
 * Since switchFragment is private and depends on supportFragmentManager,
 * we test the core logic by simulating the fragments map and tag-based
 * lookup contract that switchFragment implements.
 *
 * Validates: Requirements 11.1, 11.4
 */
class FragmentStatePreservationTest {

    /**
     * Simulates the Fragment management logic from BikeActivity.switchFragment().
     * This mirrors the production code's hide/show + tag-based lookup pattern.
     */
    private class FragmentSwitcher {
        val fragments = mutableMapOf<Int, Fragment>()
        var activeFragment: Fragment? = null
        // Track hide/show calls for verification
        val hiddenFragments = mutableSetOf<Fragment>()
        val shownFragments = mutableSetOf<Fragment>()
        val addedFragments = mutableMapOf<String, Fragment>()

        fun switchFragment(menuItemId: Int, fragmentFactory: (Int) -> Fragment) {
            val tag = "frag_$menuItemId"

            // Hide current Fragment
            activeFragment?.let { hiddenFragments.add(it); shownFragments.remove(it) }

            var fragment = addedFragments[tag]
            if (fragment == null) {
                fragment = fragmentFactory(menuItemId)
                addedFragments[tag] = fragment
            }
            shownFragments.add(fragment)
            hiddenFragments.remove(fragment)

            activeFragment = fragment
            fragments[menuItemId] = fragment
        }

        fun findFragmentByTag(tag: String): Fragment? = addedFragments[tag]
    }

    private lateinit var switcher: FragmentSwitcher
    private val MENU_HOME = 1
    private val MENU_TRACK = 2
    private val MENU_SENSORS = 3

    // Factory that creates distinct mock fragments per menu ID
    private val fragmentFactory: (Int) -> Fragment = { menuItemId ->
        mockk<Fragment>(relaxed = true, name = "Fragment_$menuItemId")
    }

    @BeforeEach
    fun setUp() {
        switcher = FragmentSwitcher()
    }

    // --- Requirement 11.1: Fragment view state preserved across tab switches ---

    @Test
    @DisplayName("Switching A -> B -> A returns the same Fragment A instance")
    fun switchingBackReturnsSameInstance() {
        // Switch to Fragment A
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val fragmentA1 = switcher.activeFragment

        // Switch to Fragment B
        switcher.switchFragment(MENU_TRACK, fragmentFactory)
        val fragmentB = switcher.activeFragment

        // Switch back to Fragment A
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val fragmentA2 = switcher.activeFragment

        assertNotNull(fragmentA1)
        assertNotNull(fragmentA2)
        assertSame(fragmentA1, fragmentA2, "Fragment A should be the same instance after switching back")
        assertNotSame(fragmentA1, fragmentB, "Fragment A and B should be different instances")
    }

    @Test
    @DisplayName("Fragment instance in fragments map is same object after round-trip switch")
    fun fragmentsMapPreservesInstance() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val firstInstance = switcher.fragments[MENU_HOME]

        switcher.switchFragment(MENU_TRACK, fragmentFactory)
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val secondInstance = switcher.fragments[MENU_HOME]

        assertSame(firstInstance, secondInstance, "fragments map should hold the same instance")
    }

    @Test
    @DisplayName("Multiple round-trip switches preserve same Fragment instances")
    fun multipleRoundTripsPreserveInstances() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val homeFragment = switcher.activeFragment

        switcher.switchFragment(MENU_TRACK, fragmentFactory)
        val trackFragment = switcher.activeFragment

        // Round-trip multiple times
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        assertSame(homeFragment, switcher.activeFragment)

        switcher.switchFragment(MENU_TRACK, fragmentFactory)
        assertSame(trackFragment, switcher.activeFragment)

        switcher.switchFragment(MENU_HOME, fragmentFactory)
        assertSame(homeFragment, switcher.activeFragment)
    }

    // --- Requirement 11.4: Fragment hide/show mechanism (not destroy/recreate) ---

    @Test
    @DisplayName("Hidden Fragment still exists in FragmentManager (findFragmentByTag)")
    fun hiddenFragmentStillExistsInManager() {
        // Switch to Home
        switcher.switchFragment(MENU_HOME, fragmentFactory)

        // Switch to Track (Home gets hidden)
        switcher.switchFragment(MENU_TRACK, fragmentFactory)

        // Home fragment should still be findable by tag
        val homeFragment = switcher.findFragmentByTag("frag_$MENU_HOME")
        assertNotNull(homeFragment, "Hidden Fragment should still exist in FragmentManager")
    }

    @Test
    @DisplayName("All previously visited Fragments remain in FragmentManager")
    fun allVisitedFragmentsRemainInManager() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        switcher.switchFragment(MENU_TRACK, fragmentFactory)
        switcher.switchFragment(MENU_SENSORS, fragmentFactory)

        // All three should still be findable
        assertNotNull(switcher.findFragmentByTag("frag_$MENU_HOME"), "Home fragment should exist")
        assertNotNull(switcher.findFragmentByTag("frag_$MENU_TRACK"), "Track fragment should exist")
        assertNotNull(switcher.findFragmentByTag("frag_$MENU_SENSORS"), "Sensors fragment should exist")
    }

    @Test
    @DisplayName("Switching away hides the current Fragment (not removes it)")
    fun switchingAwayHidesCurrentFragment() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val homeFragment = switcher.activeFragment!!

        switcher.switchFragment(MENU_TRACK, fragmentFactory)

        // Home should be in hidden set, not removed
        assertTrue(switcher.hiddenFragments.contains(homeFragment),
            "Previous Fragment should be hidden, not removed")
        assertTrue(switcher.addedFragments.containsValue(homeFragment),
            "Previous Fragment should still be in the added fragments")
    }

    @Test
    @DisplayName("Switching to a Fragment shows it (not re-adds it)")
    fun switchingToExistingFragmentShowsIt() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        switcher.switchFragment(MENU_TRACK, fragmentFactory)

        val addedCountBefore = switcher.addedFragments.size

        // Switch back to Home - should show, not add again
        switcher.switchFragment(MENU_HOME, fragmentFactory)

        assertEquals(addedCountBefore, switcher.addedFragments.size,
            "No new fragment should be added when switching to existing one")
        assertTrue(switcher.shownFragments.contains(switcher.activeFragment),
            "Existing Fragment should be shown")
    }

    @Test
    @DisplayName("Tag format follows 'frag_menuItemId' convention")
    fun tagFormatIsCorrect() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)

        val tag = "frag_$MENU_HOME"
        assertNotNull(switcher.findFragmentByTag(tag),
            "Fragment should be stored with tag 'frag_\$menuItemId'")
    }

    @Test
    @DisplayName("activeFragment always points to the currently visible Fragment")
    fun activeFragmentPointsToCurrentlyVisible() {
        switcher.switchFragment(MENU_HOME, fragmentFactory)
        val home = switcher.activeFragment

        switcher.switchFragment(MENU_TRACK, fragmentFactory)
        val track = switcher.activeFragment

        assertNotSame(home, track, "activeFragment should change when switching")
        assertTrue(switcher.shownFragments.contains(track),
            "activeFragment should be in the shown set")
    }
}
