package net.telent.biscuit

/**
 * 距离格式化工具类
 *
 * 根据距离值自动在米/千米之间切换显示：
 * - 距离 < 1000m：显示为整数米，如 "856"，单位 "m"
 * - 距离 >= 1000m：显示为两位小数千米，如 "1.23"，单位 "km"
 *
 * 边界处理：
 * - 负数取绝对值
 * - NaN/Infinity 返回 ("--", "m")
 * - 0 正常显示 "0 m"
 */
object DistanceFormatter {
    /**
     * 格式化距离值为带单位的显示字符串。
     *
     * @param distanceMeters 距离（米）
     * @return Pair<String, String> (数值字符串, 单位字符串)
     */
    fun format(distanceMeters: Float): Pair<String, String> {
        if (distanceMeters.isNaN() || distanceMeters.isInfinite()) return Pair("--", "m")
        val abs = Math.abs(distanceMeters)
        return if (abs < 1000f) {
            Pair(String.format("%.0f", abs), "m")
        } else {
            Pair(String.format("%.2f", abs / 1000f), "km")
        }
    }

    /**
     * 格式化为完整字符串（用于迷你仪表盘等场景）。
     *
     * @param distanceMeters 距离（米）
     * @return 格式化后的完整字符串，如 "856 m" 或 "1.23 km"
     */
    fun formatFull(distanceMeters: Float): String {
        val (value, unit) = format(distanceMeters)
        return "$value $unit"
    }
}
