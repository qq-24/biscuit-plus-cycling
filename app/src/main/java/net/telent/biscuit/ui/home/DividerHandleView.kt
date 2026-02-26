package net.telent.biscuit.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/**
 * 面板之间可拖动的分隔条 View。
 * 视觉高度 4dp 灰色横线，触摸区域通过 padding 扩展到 ≥ 24dp。
 * 使用固定高度 wrap_content，不参与权重分配。
 */
class DividerHandleView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** 拖动过程中每次 MOVE 事件的像素偏移回调 */
    var onDragListener: ((deltaPixels: Float) -> Unit)? = null

    /** 拖动结束（ACTION_UP）时的回调，用于保存权重 */
    var onDragEndListener: (() -> Unit)? = null

    companion object {
        private const val VISUAL_HEIGHT_DP = 4f
        private const val TOUCH_HEIGHT_DP = 24f
        private const val DEFAULT_COLOR = 0xFF9E9E9E.toInt()    // Material Grey 500
        private const val ACTIVATED_COLOR = 0xFF616161.toInt()   // Material Grey 700
    }

    private val density = resources.displayMetrics.density
    private val visualHeightPx = (VISUAL_HEIGHT_DP * density).toInt()
    private val touchHeightPx = (TOUCH_HEIGHT_DP * density).toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_COLOR
        style = Paint.Style.FILL
    }

    private var lastTouchY = 0f

    init {
        // Padding expands touch area: total height = visualHeight + top/bottom padding ≥ 24dp
        val verticalPadding = ((touchHeightPx - visualHeightPx) / 2).coerceAtLeast(0)
        setPadding(0, verticalPadding, 0, verticalPadding)

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalHeight = visualHeightPx + paddingTop + paddingBottom
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            totalHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the visual line centered within the padding area
        val left = 0f
        val right = width.toFloat()
        val top = paddingTop.toFloat()
        val bottom = top + visualHeightPx
        canvas.drawRect(left, top, right, bottom, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.rawY
                isActivated = true
                paint.color = ACTIVATED_COLOR
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - lastTouchY
                lastTouchY = event.rawY
                onDragListener?.invoke(deltaY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isActivated = false
                paint.color = DEFAULT_COLOR
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.action == MotionEvent.ACTION_UP) {
                    onDragEndListener?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
