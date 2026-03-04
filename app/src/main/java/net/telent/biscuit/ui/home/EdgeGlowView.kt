package net.telent.biscuit.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 屏幕四边红色渐变光晕 View，用于低速提醒视觉反馈。
 * 使用 LinearGradient 绘制 20dp 宽的半透明红色渐变，
 * ValueAnimator 控制淡入淡出动画（1秒周期）。
 */
class EdgeGlowView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface OnAlphaUpdateListener {
        fun onAlphaUpdate(alpha: Float)
    }

    interface OnGlowFinishedListener {
        fun onGlowFinished()
    }

    var alphaUpdateListener: OnAlphaUpdateListener? = null
    var glowFinishedListener: OnGlowFinishedListener? = null

    companion object {
        private const val TAG = "EdgeGlowView"
        private const val GLOW_WIDTH_DP = 20f
        private const val GLOW_COLOR = 0xFFFF0000.toInt() // #FF0000 red
        private const val ANIMATION_DURATION_MS = 1000L
    }

    private var glowWidthPx: Float = run {
        val prefs = context.getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        val widthDp = prefs.getInt("glow_width_dp", 20).toFloat()
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp, resources.displayMetrics)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentAlpha = 0f
    private var animator: ValueAnimator? = null
    private var glowing = false

    // Gradient shaders for each edge, recreated on size change
    private var topShader: LinearGradient? = null
    private var bottomShader: LinearGradient? = null
    private var leftShader: LinearGradient? = null
    private var rightShader: LinearGradient? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recreateShaders(w, h)
    }

    private fun recreateShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        val colorStart = GLOW_COLOR
        val colorEnd = Color.TRANSPARENT

        // Top edge: gradient from top down
        topShader = LinearGradient(
            0f, 0f, 0f, glowWidthPx,
            colorStart, colorEnd, Shader.TileMode.CLAMP
        )
        // Bottom edge: gradient from bottom up
        bottomShader = LinearGradient(
            0f, h.toFloat(), 0f, h - glowWidthPx,
            colorStart, colorEnd, Shader.TileMode.CLAMP
        )
        // Left edge: gradient from left to right
        leftShader = LinearGradient(
            0f, 0f, glowWidthPx, 0f,
            colorStart, colorEnd, Shader.TileMode.CLAMP
        )
        // Right edge: gradient from right to left
        rightShader = LinearGradient(
            w.toFloat(), 0f, w - glowWidthPx, 0f,
            colorStart, colorEnd, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentAlpha <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.alpha = (currentAlpha * 255).toInt().coerceIn(0, 255)

        try {
            // Top edge
            topShader?.let {
                paint.shader = it
                canvas.drawRect(0f, 0f, w, glowWidthPx, paint)
            }
            // Bottom edge
            bottomShader?.let {
                paint.shader = it
                canvas.drawRect(0f, h - glowWidthPx, w, h, paint)
            }
            // Left edge
            leftShader?.let {
                paint.shader = it
                canvas.drawRect(0f, 0f, glowWidthPx, h, paint)
            }
            // Right edge
            rightShader?.let {
                paint.shader = it
                canvas.drawRect(w - glowWidthPx, 0f, w, h, paint)
            }
        } finally {
            paint.shader = null
        }
    }

    fun startGlow(cycleDurationMs: Long = ANIMATION_DURATION_MS, durationMs: Long = 0L) {
        if (glowing) return
        glowing = true
        visibility = VISIBLE

        try {
            animator?.cancel()
            val effectiveCycle = if (durationMs > 0L) durationMs else cycleDurationMs
            animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = effectiveCycle / 2  // half-cycle for fade in/out
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                if (durationMs > 0L) {
                    // Single cycle: fade in then fade out, then finish
                    repeatCount = 1
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            stopGlow()
                            glowFinishedListener?.onGlowFinished()
                        }
                    })
                } else {
                    // Infinite loop for non-timed glow
                    repeatCount = ValueAnimator.INFINITE
                }
                addUpdateListener { anim ->
                    currentAlpha = anim.animatedValue as Float
                    alphaUpdateListener?.onAlphaUpdate(currentAlpha)
                    invalidate()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start glow animation", e)
            currentAlpha = 0f
            glowing = false
            visibility = GONE
        }
    }

    fun stopGlow() {
        if (!glowing) return
        animator?.removeAllListeners()
        animator?.cancel()
        currentAlpha = 0f
        glowing = false
        invalidate()
        visibility = GONE
    }

    fun isGlowing(): Boolean = glowing

    fun setGlowWidth(widthDp: Float) {
        glowWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp, resources.displayMetrics)
        recreateShaders(width, height)
        invalidate()
    }
}
