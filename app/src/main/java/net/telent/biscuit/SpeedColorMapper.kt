package net.telent.biscuit

import androidx.core.graphics.ColorUtils

/**
 * 速度颜色映射工具类。
 * 根据速度值在绿色（快）到红色（慢）之间线性插值。
 */
object SpeedColorMapper {
    const val COLOR_FAST = 0xFF4CAF50.toInt()  // #4CAF50 绿色（最快）
    const val COLOR_SLOW = 0xFFF44336.toInt()  // #F44336 红色（最慢）

    /**
     * 根据速度值返回对应颜色。
     * @param speed 当前速度
     * @param minSpeed 最小有效速度（对应红色）
     * @param maxSpeed 最大速度（对应绿色）
     * @return ARGB 颜色值
     */
    fun getColor(speed: Float, minSpeed: Float, maxSpeed: Float): Int {
        if (maxSpeed <= minSpeed) return COLOR_FAST
        val ratio = ((speed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)
        return ColorUtils.blendARGB(COLOR_SLOW, COLOR_FAST, ratio)
    }
}
