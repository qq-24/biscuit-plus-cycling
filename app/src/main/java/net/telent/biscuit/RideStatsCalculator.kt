package net.telent.biscuit

/**
 * 骑行统计数据
 */
data class RideStats(
    val totalDistanceKm: Double,
    val totalTimeSeconds: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double
)

/**
 * 每公里分段数据
 */
data class KmSplit(
    val kmNumber: Int,
    val speedKmh: Double,
    val cumulativeTimeFormatted: String
)

/**
 * 骑行统计计算工具
 */
object RideStatsCalculator {

    /**
     * 计算骑行统计数据
     *
     * @param trackpoints 按时间排序的轨迹点列表
     * @return RideStats 包含总距离(km)、总时间(s)、平均速度(km/h)、最大速度(km/h)
     */
    fun calculateStats(trackpoints: List<Trackpoint>): RideStats {
        if (trackpoints.isEmpty()) {
            return RideStats(0.0, 0, 0.0, 0.0)
        }

        val first = trackpoints.first()
        val last = trackpoints.last()

        // distance is stored in meters, convert to km
        val totalDistanceKm = (last.distance - first.distance).toDouble() / 1000.0

        // Use movingTime from last trackpoint (excludes paused time)
        val movingTimeSeconds = last.movingTime.seconds

        // Fall back to wall-clock time if movingTime is zero
        val totalTimeSeconds = if (movingTimeSeconds > 0) {
            movingTimeSeconds
        } else {
            java.time.Duration.between(first.timestamp, last.timestamp).seconds
        }

        // avg speed = distance(km) / time(hours)
        val avgSpeedKmh = if (totalTimeSeconds > 0) {
            totalDistanceKm / (totalTimeSeconds / 3600.0)
        } else {
            0.0
        }

        // speed is already stored in km/h, find max (ignore negative/sentinel values)
        val maxSpeedKmh = trackpoints
            .filter { it.speed >= 0f }
            .maxOfOrNull { it.speed.toDouble() } ?: 0.0

        return RideStats(totalDistanceKm, totalTimeSeconds, avgSpeedKmh, maxSpeedKmh)
    }

    /**
     * 计算每公里配速（分钟/公里）
     *
     * @param trackpoints 按时间排序的轨迹点列表
     * @return 每公里配速列表，每个元素表示该公里段所用的分钟数
     */
    fun calculatePacePerKm(trackpoints: List<Trackpoint>): List<Double> {
        if (trackpoints.size < 2) return emptyList()

        val first = trackpoints.first()
        val baseDistance = first.distance.toDouble() // meters
        val totalDistanceM = (trackpoints.last().distance - first.distance).toDouble()

        if (totalDistanceM <= 0) return emptyList()

        val totalKm = (totalDistanceM / 1000.0).toInt() + 
            if (totalDistanceM % 1000.0 > 0) 1 else 0

        val paces = mutableListOf<Double>()
        var tpIndex = 0

        for (km in 1..totalKm) {
            val segmentEndM = if (km * 1000.0 <= totalDistanceM) {
                km * 1000.0
            } else {
                totalDistanceM
            }
            val segmentStartM = (km - 1) * 1000.0

            // Find the timestamp at segmentStartM (relative to base)
            val startTime = interpolateTime(trackpoints, baseDistance + segmentStartM)
            val endTime = interpolateTime(trackpoints, baseDistance + segmentEndM)

            if (startTime != null && endTime != null) {
                val elapsedSeconds = java.time.Duration.between(startTime, endTime).seconds.toDouble()
                val segmentDistanceKm = (segmentEndM - segmentStartM) / 1000.0

                if (segmentDistanceKm > 0) {
                    val paceMinPerKm = (elapsedSeconds / 60.0) / segmentDistanceKm
                    paces.add(paceMinPerKm)
                }
            }
        }

        return paces
    }

    /**
     * 格式化录制时长为 "H:MM:SS" 字符串
     *
     * @param startTime 录制开始时间
     * @param currentTime 当前时间
     * @return 格式化的时长字符串，如 "1:05:30"、"0:00:05"
     */
    fun formatDuration(startTime: java.time.Instant, currentTime: java.time.Instant): String {
        val totalSeconds = java.time.Duration.between(startTime, currentTime).seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    /**
     * 计算每公里速度（km/h）
     *
     * @param trackpoints 按时间排序的轨迹点列表
     * @return 每公里速度列表，每个元素表示该公里段的速度（km/h）
     */
    fun calculateSpeedPerKm(trackpoints: List<Trackpoint>): List<Double> {
        if (trackpoints.size < 2) return emptyList()

        val first = trackpoints.first()
        val baseDistance = first.distance.toDouble()
        val totalDistanceM = (trackpoints.last().distance - first.distance).toDouble()

        if (totalDistanceM <= 0) return emptyList()

        val totalKm = (totalDistanceM / 1000.0).toInt() +
            if (totalDistanceM % 1000.0 > 0) 1 else 0

        val speeds = mutableListOf<Double>()

        for (km in 1..totalKm) {
            val segmentEndM = if (km * 1000.0 <= totalDistanceM) {
                km * 1000.0
            } else {
                totalDistanceM
            }
            val segmentStartM = (km - 1) * 1000.0

            val startTime = interpolateTime(trackpoints, baseDistance + segmentStartM)
            val endTime = interpolateTime(trackpoints, baseDistance + segmentEndM)

            if (startTime != null && endTime != null) {
                val elapsedSeconds = java.time.Duration.between(startTime, endTime).seconds.toDouble()
                val segmentDistanceKm = (segmentEndM - segmentStartM) / 1000.0

                if (segmentDistanceKm > 0 && elapsedSeconds > 0) {
                    val speedKmh = segmentDistanceKm / (elapsedSeconds / 3600.0)
                    speeds.add(speedKmh)
                } else {
                    speeds.add(0.0)
                }
            }
        }

        return speeds
    }

    /**
     * 计算每公里分段数据（速度 + 累计用时）
     *
     * @param trackpoints 按时间排序的轨迹点列表
     * @return KmSplit 列表，包含公里数、速度、累计用时
     */
    fun calculateKmSplits(trackpoints: List<Trackpoint>): List<KmSplit> {
        if (trackpoints.size < 2) return emptyList()

        val first = trackpoints.first()
        val baseDistance = first.distance.toDouble()
        val totalDistanceM = (trackpoints.last().distance - first.distance).toDouble()

        if (totalDistanceM <= 0) return emptyList()

        val totalKm = (totalDistanceM / 1000.0).toInt() +
            if (totalDistanceM % 1000.0 > 0) 1 else 0

        val splits = mutableListOf<KmSplit>()
        val rideStartTime = first.timestamp

        for (km in 1..totalKm) {
            val segmentEndM = if (km * 1000.0 <= totalDistanceM) {
                km * 1000.0
            } else {
                totalDistanceM
            }
            val segmentStartM = (km - 1) * 1000.0

            val startTime = interpolateTime(trackpoints, baseDistance + segmentStartM)
            val endTime = interpolateTime(trackpoints, baseDistance + segmentEndM)

            if (startTime != null && endTime != null) {
                val elapsedSeconds = java.time.Duration.between(startTime, endTime).seconds.toDouble()
                val segmentDistanceKm = (segmentEndM - segmentStartM) / 1000.0

                val speedKmh = if (segmentDistanceKm > 0 && elapsedSeconds > 0) {
                    segmentDistanceKm / (elapsedSeconds / 3600.0)
                } else {
                    0.0
                }

                // Cumulative time from ride start to end of this km segment
                val cumulativeSeconds = java.time.Duration.between(rideStartTime, endTime).seconds
                val h = cumulativeSeconds / 3600
                val m = (cumulativeSeconds % 3600) / 60
                val s = cumulativeSeconds % 60
                val cumulativeFormatted = "%d:%02d:%02d".format(h, m, s)

                splits.add(KmSplit(km, speedKmh, cumulativeFormatted))
            }
        }

        return splits
    }

    /**
     * 在轨迹点列表中，通过线性插值找到到达指定距离时的时间
     */
    internal fun interpolateTime(trackpoints: List<Trackpoint>, targetDistanceM: Double): java.time.Instant? {
        if (trackpoints.isEmpty()) return null

        // If target is at or before first point
        if (targetDistanceM <= trackpoints.first().distance.toDouble()) {
            return trackpoints.first().timestamp
        }

        // If target is at or after last point
        if (targetDistanceM >= trackpoints.last().distance.toDouble()) {
            return trackpoints.last().timestamp
        }

        // Find the two points that bracket the target distance
        for (i in 0 until trackpoints.size - 1) {
            val curr = trackpoints[i]
            val next = trackpoints[i + 1]
            val currDist = curr.distance.toDouble()
            val nextDist = next.distance.toDouble()

            if (targetDistanceM in currDist..nextDist) {
                if (nextDist == currDist) {
                    return curr.timestamp
                }
                // Linear interpolation
                val fraction = (targetDistanceM - currDist) / (nextDist - currDist)
                val currEpoch = curr.timestamp.toEpochMilli()
                val nextEpoch = next.timestamp.toEpochMilli()
                val interpolatedEpoch = currEpoch + ((nextEpoch - currEpoch) * fraction).toLong()
                return java.time.Instant.ofEpochMilli(interpolatedEpoch)
            }
        }

        return trackpoints.last().timestamp
    }
}
