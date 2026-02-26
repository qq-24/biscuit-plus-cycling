package net.telent.biscuit

/**
 * 卡路里消耗计算工具类
 *
 * 公式：卡路里 = 骑行时间（小时）× 平均速度（km/h）× 体重（kg）× 系数
 */
object CalorieCalculator {
    /**
     * 计算骑行卡路里消耗
     * @param ridingTimeHours 骑行时间（小时）
     * @param avgSpeedKmh 平均速度（km/h）
     * @param weightKg 体重（kg），默认 65
     * @param factor 系数（kcal/kg/km），默认 0.4
     * @return 卡路里消耗（kcal）
     */
    fun calculate(
        ridingTimeHours: Double,
        avgSpeedKmh: Double,
        weightKg: Double = 65.0,
        factor: Double = 0.4
    ): Double {
        return ridingTimeHours * avgSpeedKmh * weightKg * factor
    }
}
