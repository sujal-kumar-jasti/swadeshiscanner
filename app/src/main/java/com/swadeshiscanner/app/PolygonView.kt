package com.swadeshiscanner.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt

class PolygonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // The 4 corners of our cropping tool (Normalized 0..1 coordinates to handle resizing)
    // Default start positions: slightly inside the screen
    private val points = mapOf(
        0 to PointF(200f, 200f), // Top Left
        1 to PointF(800f, 200f), // Top Right
        2 to PointF(800f, 1000f), // Bottom Right
        3 to PointF(200f, 1000f)  // Bottom Left
    )

    // Paint for the Green Lines
    private val linePaint = Paint().apply {
        color = "#00C895".toColorInt() // SwadeshiScanner Green
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Paint for the Corner Dots (White center)
    private val dotPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint for the Corner Dot Border (Green ring)
    private val dotBorderPaint = Paint().apply {
        color = "#00C895".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private var selectedPointId: Int? = null
    private val touchRadius = 80f // How close finger needs to be to grab a dot

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Lines connecting the points (0->1->2->3->0)
        canvas.drawLine(points[0]!!.x, points[0]!!.y, points[1]!!.x, points[1]!!.y, linePaint)
        canvas.drawLine(points[1]!!.x, points[1]!!.y, points[2]!!.x, points[2]!!.y, linePaint)
        canvas.drawLine(points[2]!!.x, points[2]!!.y, points[3]!!.x, points[3]!!.y, linePaint)
        canvas.drawLine(points[3]!!.x, points[3]!!.y, points[0]!!.x, points[0]!!.y, linePaint)

        // 2. Draw the 4 Corner Dots
        for ((_, point) in points) {
            canvas.drawCircle(point.x, point.y, 30f, dotPaint)       // White fill
            canvas.drawCircle(point.x, point.y, 30f, dotBorderPaint) // Green border
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Find which point is closest to finger
                selectedPointId = getNearestPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Move the selected point
                selectedPointId?.let { id ->
                    // Clamp to view bounds so it doesn't go off screen
                    points[id]!!.x = event.x.coerceIn(0f, width.toFloat())
                    points[id]!!.y = event.y.coerceIn(0f, height.toFloat())
                    invalidate() // Redraw the view
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                selectedPointId = null // Drop the point
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getNearestPoint(x: Float, y: Float): Int? {
        // Loop through all 4 points and see if distance < radius
        for ((id, point) in points) {
            val dx = x - point.x
            val dy = y - point.y
            if (dx * dx + dy * dy < touchRadius * touchRadius) {
                return id
            }
        }
        return null
    }
    fun getPoints(): Map<Int, PointF> {
        return points
    }
    fun setPoints(newPoints: Map<Int, PointF>) {
        // Update our internal points
        for ((key, value) in newPoints) {
            // We need to scale the image points back to screen points if needed
            // For now, we assume simple mapping or just set them
            this.points[key]?.x = value.x
            this.points[key]?.y = value.y
        }
        invalidate() // Force the view to redraw with new dots
    }
}