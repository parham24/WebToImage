package com.webtoimage.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val shadePaint = Paint().apply {
        color = 0x66000000
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private var selectionEnabled = false
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var dragging = false

    fun setSelectionEnabled(enabled: Boolean) {
        selectionEnabled = enabled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!selectionEnabled) return false
        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                endX = event.x
                endY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                endX = event.x
                endY = event.y
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = getSelectionRect() ?: return

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)

        val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(rect, clearPaint)
        canvas.restoreToCount(saved)

        canvas.drawRect(rect, borderPaint)
    }

    fun clearSelection() {
        startX = 0f; startY = 0f; endX = 0f; endY = 0f
        invalidate()
    }

    fun getSelectionRect(): RectF? {
        if (abs(endX - startX) < 20f || abs(endY - startY) < 20f) return null
        val l = min(startX, endX).coerceIn(0f, width.toFloat())
        val t = min(startY, endY).coerceIn(0f, height.toFloat())
        val r = max(startX, endX).coerceIn(0f, width.toFloat())
        val b = max(startY, endY).coerceIn(0f, height.toFloat()) // مهم: height (نه width)
        return RectF(l, t, r, b)
    }
}
