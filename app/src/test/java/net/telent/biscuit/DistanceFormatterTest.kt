package net.telent.biscuit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DistanceFormatterTest {

    // --- format() tests ---

    @Test
    fun `format returns meters for distance below 1000`() {
        val (value, unit) = DistanceFormatter.format(856f)
        assertEquals("856", value)
        assertEquals("m", unit)
    }

    @Test
    fun `format returns km for distance at exactly 1000`() {
        val (value, unit) = DistanceFormatter.format(1000f)
        assertEquals("1.00", value)
        assertEquals("km", unit)
    }

    @Test
    fun `format returns meters for 999m`() {
        val (value, unit) = DistanceFormatter.format(999f)
        assertEquals("999", value)
        assertEquals("m", unit)
    }

    @Test
    fun `format returns correct km for large distance`() {
        val (value, unit) = DistanceFormatter.format(12345f)
        assertEquals("12.35", value)
        assertEquals("km", unit)
    }

    @Test
    fun `format handles zero`() {
        val (value, unit) = DistanceFormatter.format(0f)
        assertEquals("0", value)
        assertEquals("m", unit)
    }

    @Test
    fun `format handles negative by taking absolute value`() {
        val (value, unit) = DistanceFormatter.format(-500f)
        assertEquals("500", value)
        assertEquals("m", unit)
    }

    @Test
    fun `format handles negative km range`() {
        val (value, unit) = DistanceFormatter.format(-2500f)
        assertEquals("2.50", value)
        assertEquals("km", unit)
    }

    @Test
    fun `format returns placeholder for NaN`() {
        val (value, unit) = DistanceFormatter.format(Float.NaN)
        assertEquals("--", value)
        assertEquals("m", unit)
    }

    @Test
    fun `format returns placeholder for positive infinity`() {
        val (value, unit) = DistanceFormatter.format(Float.POSITIVE_INFINITY)
        assertEquals("--", value)
        assertEquals("m", unit)
    }

    @Test
    fun `format returns placeholder for negative infinity`() {
        val (value, unit) = DistanceFormatter.format(Float.NEGATIVE_INFINITY)
        assertEquals("--", value)
        assertEquals("m", unit)
    }

    // --- formatFull() tests ---

    @Test
    fun `formatFull returns full string for meters`() {
        assertEquals("856 m", DistanceFormatter.formatFull(856f))
    }

    @Test
    fun `formatFull returns full string for km`() {
        assertEquals("1.23 km", DistanceFormatter.formatFull(1230f))
    }

    @Test
    fun `formatFull returns placeholder for NaN`() {
        assertEquals("-- m", DistanceFormatter.formatFull(Float.NaN))
    }
}
