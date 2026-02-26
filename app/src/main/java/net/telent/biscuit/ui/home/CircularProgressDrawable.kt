package net.telent.biscuit.ui.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class CircularProgressDrawable(
    private val color: Int,
    private val strokeWidth: Float
) : Drawable() {

    /** 进度值 0f..1f */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = this@CircularProgressDrawable.color
        strokeWidth = this@CircularProgressDrawable.strokeWidth
    }

    private val arcBounds = RectF()

    override fun draw(canvas: Canvas) {
        if (progress <= 0f) return

        val inset = strokeWidth / 2f
        arcBounds.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )

        val sweepAngle = progress * 360f
        canvas.drawArc(arcBounds, -90f, sweepAngle, false, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
