package com.webtoimage.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var enabledSelection = false

    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f

    private var hasRect = false

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000  // نیمه‌شفاف
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val rect = RectF()

    fun setSelectionEnabled(on: Boolean) {
        enabledSelection = on
        if (!on) {
            clearSelection()
        }
        invalidate()
    }

    fun clearSelection() {
        hasRect = false
        rect.setEmpty()
        invalidate()
    }

    fun getSelectionRect(): RectF? = if (hasRect) RectF(rect) else null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledSelection) return false

        // مهم: از event.x/y (مختصات محلی View) استفاده می‌کنیم [web:2743]
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                curX = downX
                curY = downY
                hasRect = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateRect()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                curX = event.x
                curY = event.y
                updateRect()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                curX = event.x
                curY = event.y
                updateRect()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateRect() {
        val l = min(downX, curX).coerceIn(0f, width.toFloat())
        val t = min(downY, curY).coerceIn(0f, height.toFloat())
        val r = max(downX, curX).coerceIn(0f, width.toFloat())
        val b = max(downY, curY).coerceIn(0f, height.toFloat())
        rect.set(l, t, r, b)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!enabledSelection || !hasRect) return

        // تار کردن پشت
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // پاک کردن داخل کادر (نمایش واضح ناحیه انتخابی)
        val clearPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
        canvas.saveLayer(null, null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(rect, clearPaint)
        canvas.restore()

        // کادر سفید
        canvas.drawRect(rect, borderPaint)
    }
}
