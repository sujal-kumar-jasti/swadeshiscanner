package com.swadeshiscanner.app.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PassportOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { color = Color.parseColor("#99000000"); style = Paint.Style.FILL }
    private val holePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f }

    // 35mm : 45mm = 7:9 ratio
    var aspectRatio = 7f / 9f
    var cropRect: RectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Calculate Box Size (70% of screen width)
        val boxWidth = w * 0.7f
        val boxHeight = boxWidth / aspectRatio

        val left = (w - boxWidth) / 2
        val top = (h - boxHeight) / 2
        cropRect.set(left, top, left + boxWidth, top + boxHeight)

        // Draw Dark Background with Hole
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, paint)
        canvas.drawRect(cropRect, holePaint)
        canvas.drawRect(cropRect, borderPaint)
        canvas.restoreToCount(layer)
    }
}