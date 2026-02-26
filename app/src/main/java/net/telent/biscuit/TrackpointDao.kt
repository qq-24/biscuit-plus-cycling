package net.telent.biscuit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface TrackpointDao {
    @Query("SELECT * from trackpoint")
    fun getAll(): List<Trackpoint>

    @Query("SELECT * from trackpoint where ts >= :startTime and ts < :endTime ORDER BY ts ASC")
    fun getBetween(startTime: Instant, endTime: Instant): List<Trackpoint>

    @Query("SELECT * from trackpoint where ts >= :startTime and ts < :endTime ORDER BY ts ASC")
    fun getTrackpointsForSession(startTime: Instant, endTime: Instant): List<Trackpoint>

    @Insert
    fun addPoint(point: Trackpoint)

    @Query("DELETE FROM trackpoint WHERE ts >= :startTime AND ts < :endTime")
    fun deleteByTimeRange(startTime: Instant, endTime: Instant)
}