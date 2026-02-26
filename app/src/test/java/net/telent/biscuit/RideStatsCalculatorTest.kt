package net.telent.biscuit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RideStatsCalculatorTest {

    private fun trackpoint(
        timestamp: Instant,
        distance: Float,
        speed: Float = 20.0f,
        lat: Double? = 39.9,
        lng: Double? = 116.4
    ) = Trackpoint(
        timestamp = timestamp,
        lat = lat,
        lng = lng,
        speed = speed,
        distance = distance
    )

    private val baseTime: Instant = Instant.parse("2024-01-01T08:00:00Z")

    // --- calculateStats tests ---

    @Test
    fun `calculateStats returns zeros for empty list`() {
        val stats = RideStatsCalculator.calculateStats(emptyList())
        assertEquals(0.0, stats.totalDistanceKm)
        assertEquals(0L, stats.totalTimeSeconds)
        assertEquals(0.0, stats.avgSpeedKmh)
        assertEquals(0.0, stats.maxSpeedKmh)
    }

    @Test
    fun `calculateStats returns zeros for single trackpoint`() {
        val stats = RideStatsCalculator.calculateStats(
            listOf(trackpoint(baseTime, 1000f, 15.0f))
        )
        assertEquals(0.0, stats.totalDistanceKm)
        assertEquals(0L, stats.totalTimeSeconds)
        assertEquals(0.0, stats.avgSpeedKmh)
        assertEquals(15.0, stats.maxSpeedKmh)
    }

    @Test
    fun `calculateStats computes correct distance in km`() {
        val points = listOf(
            trackpoint(baseTime, 0f),
            trackpoint(baseTime.plusSeconds(3600), 10000f) // 10km in meters
        )
        val stats = RideStatsCalculator.calculateStats(points)
        assertEquals(10.0, stats.totalDistanceKm, 0.001)
    }

    @Test
    fun `calculateStats computes correct time in seconds`() {
        val points = listOf(
            trackpoint(baseTime, 0f),
            trackpoint(baseTime.plusSeconds(1800), 5000f) // 30 minutes
        )
        val stats = RideStatsCalculator.calculateStats(points)
        assertEquals(1800L, stats.totalTimeSeconds)
    }

    @Test
    fun `calculateStats computes correct avg speed`() {
        // 10km in 1 hour = 10 km/h
        val points = listOf(
            trackpoint(baseTime, 0f),
            trackpoint(baseTime.plusSeconds(3600), 10000f)
        )
        val stats = RideStatsCalculator.calculateStats(points)
        assertEquals(10.0, stats.avgSpeedKmh, 0.001)
    }

    @Test
    fun `calculateStats finds max speed ignoring negative sentinel values`() {
        val points = listOf(
            trackpoint(baseTime, 0f, speed = -1.0f),
            trackpoint(baseTime.plusSeconds(60), 500f, speed = 25.0f),
            trackpoint(baseTime.plusSeconds(120), 1000f, speed = 35.0f),
            trackpoint(baseTime.plusSeconds(180), 1500f, speed = 20.0f)
        )
        val stats = RideStatsCalculator.calculateStats(points)
        assertEquals(35.0, stats.maxSpeedKmh, 0.001)
    }

    @Test
    fun `calculateStats handles all speeds as sentinel`() {
        val points = listOf(
            trackpoint(baseTime, 0f, speed = -1.0f),
            trackpoint(baseTime.plusSeconds(60), 500f, speed = -1.0f)
        )
        val stats = RideStatsCalculator.calculateStats(points)
        assertEquals(0.0, stats.maxSpeedKmh)
    }

    // --- calculatePacePerKm tests ---

    @Test
    fun `calculatePacePerKm returns empty for empty list`() {
        assertTrue(RideStatsCalculator.calculatePacePerKm(emptyList()).isEmpty())
    }

    @Test
    fun `calculatePacePerKm returns empty for single point`() {
        assertTrue(RideStatsCalculator.calculatePacePerKm(
            listOf(trackpoint(baseTime, 0f))
        ).isEmpty())
    }

    @Test
    fun `calculatePacePerKm returns empty for zero distance`() {
        val points = listOf(
            trackpoint(baseTime, 100f),
            trackpoint(baseTime.plusSeconds(60), 100f)
        )
        assertTrue(RideStatsCalculator.calculatePacePerKm(points).isEmpty())
    }

    @Test
    fun `calculatePacePerKm computes correct pace for exact kilometers`() {
        // 2km at constant speed: each km takes 5 minutes (300 seconds)
        val points = listOf(
            trackpoint(baseTime, 0f),
            trackpoint(baseTime.plusSeconds(150), 500f),
            trackpoint(baseTime.plusSeconds(300), 1000f),
            trackpoint(baseTime.plusSeconds(450), 1500f),
            trackpoint(baseTime.plusSeconds(600), 2000f)
        )
        val paces = RideStatsCalculator.calculatePacePerKm(points)
        assertEquals(2, paces.size)
        assertEquals(5.0, paces[0], 0.1) // 5 min/km
        assertEquals(5.0, paces[1], 0.1) // 5 min/km
    }

    @Test
    fun `calculatePacePerKm handles partial last kilometer`() {
        // 1.5km: first km in 300s, last 0.5km in 150s
        val points = listOf(
            trackpoint(baseTime, 0f),
            trackpoint(baseTime.plusSeconds(300), 1000f),
            trackpoint(baseTime.plusSeconds(450), 1500f)
        )
        val paces = RideStatsCalculator.calculatePacePerKm(points)
        assertEquals(2, paces.size)
        assertEquals(5.0, paces[0], 0.1) // 5 min/km for first km
        assertEquals(5.0, paces[1], 0.1) // 150s / 0.5km = 300s/km = 5 min/km
    }

    // --- formatDuration tests ---

    @Test
    fun `formatDuration returns 0_00_00 when start equals current`() {
        assertEquals("0:00:00", RideStatsCalculator.formatDuration(baseTime, baseTime))
    }

    @Test
    fun `formatDuration formats seconds correctly`() {
        assertEquals("0:00:05", RideStatsCalculator.formatDuration(baseTime, baseTime.plusSeconds(5)))
    }

    @Test
    fun `formatDuration formats minutes and seconds correctly`() {
        // 5 minutes 30 seconds
        assertEquals("0:05:30", RideStatsCalculator.formatDuration(baseTime, baseTime.plusSeconds(330)))
    }

    @Test
    fun `formatDuration formats hours minutes seconds correctly`() {
        // 1 hour 5 minutes 30 seconds = 3930 seconds
        assertEquals("1:05:30", RideStatsCalculator.formatDuration(baseTime, baseTime.plusSeconds(3930)))
    }

    @Test
    fun `formatDuration handles exact hour`() {
        assertEquals("1:00:00", RideStatsCalculator.formatDuration(baseTime, baseTime.plusSeconds(3600)))
    }

    @Test
    fun `formatDuration handles over 24 hours`() {
        // 25 hours 0 minutes 1 second
        val seconds = 25L * 3600 + 1
        assertEquals("25:00:01", RideStatsCalculator.formatDuration(baseTime, baseTime.plusSeconds(seconds)))
    }
}
