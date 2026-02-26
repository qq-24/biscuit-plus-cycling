package net.telent.biscuit

import net.telent.biscuit.ui.home.HomeFragment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for accumulated duration formatting.
 * The timer now freezes when paused (no prefix, no counting).
 * formatAccumulatedDuration simply formats a millisecond value as h:mm:ss.
 */
class PauseDurationTextTest {

    @Test
    @DisplayName("Zero duration formats as 0:00:00")
    fun zeroDuration() {
        val text = HomeFragment.formatAccumulatedDuration(0L)
        assertEquals("0:00:00", text)
    }

    @Test
    @DisplayName("300 seconds formats as 0:05:00")
    fun fiveMinutes() {
        val text = HomeFragment.formatAccumulatedDuration(300_000L)
        assertEquals("0:05:00", text)
    }

    @Test
    @DisplayName("3661 seconds formats as 1:01:01")
    fun oneHourOneMinuteOneSecond() {
        val text = HomeFragment.formatAccumulatedDuration(3_661_000L)
        assertEquals("1:01:01", text)
    }

    @Test
    @DisplayName("Duration text never contains ⏸ prefix")
    fun noPausePrefix() {
        val text = HomeFragment.formatAccumulatedDuration(120_000L)
        assertFalse(text.startsWith("⏸"), "Duration text should not contain ⏸ prefix, got: $text")
    }

    @Test
    @DisplayName("Duration text never contains Chinese pause text")
    fun noChinesePauseText() {
        val text = HomeFragment.formatAccumulatedDuration(120_000L)
        assertFalse(text.contains("手动暂停"), "Text should not contain '手动暂停', got: $text")
        assertFalse(text.contains("自动暂停"), "Text should not contain '自动暂停', got: $text")
    }

    @Test
    @DisplayName("Large duration formats correctly")
    fun largeDuration() {
        // 10 hours, 30 minutes, 45 seconds
        val ms = (10 * 3600 + 30 * 60 + 45) * 1000L
        val text = HomeFragment.formatAccumulatedDuration(ms)
        assertEquals("10:30:45", text)
    }
}
