package com.swadeshiscanner.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

@Suppress("SameParameterValue")
object ScanUtils {

    /**
     * "White Blob" Detection Algorithm.
     * 1. Scans the entire image for pixels that look like paper (Near White).
     * 2. Collects them into a "body".
     * 3. Calculates the 4 extreme corners of this body using Min/Max Sum & Difference logic.
     */
    fun detectCorners(bitmap: Bitmap): Map<Int, PointF> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // 1. Downscale significantly to filter noise and speed up processing
        // Using a smaller scale (e.g., 150px) naturally ignores small white specs (dust).
        val smallW = 150
        val scaleFactor = smallW / w
        val smallH = (h * scaleFactor).toInt()

        if (smallH <= 0) return getDefaultCorners(w, h)

        val small = bitmap.scale(smallW, smallH)
        val pixels = IntArray(smallW * smallH)
        small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)

        // Variables to track the "Extreme" points of the white body
        // We initialize them with values that will definitely be overwritten by the first white pixel found
        var minSum = Int.MAX_VALUE  // For Top-Left (x + y)
        var maxSum = Int.MIN_VALUE  // For Bottom-Right (x + y)
        var minDiff = Int.MAX_VALUE // For Bottom-Left (x - y)
        var maxDiff = Int.MIN_VALUE // For Top-Right (x - y)

        var tl = PointF(0f, 0f)
        var br = PointF(smallW.toFloat(), smallH.toFloat())
        var bl = PointF(0f, smallH.toFloat())
        var tr = PointF(smallW.toFloat(), 0f)

        var foundWhitePixels = false

        // 2. Scan every single pixel to find the "White Body"
        for (y in 0 until smallH) {
            for (x in 0 until smallW) {
                val pixel = pixels[y * smallW + x]

                // CHECK: Is this pixel part of the "Continuous White Body"?
                if (isNearWhite(pixel)) {
                    foundWhitePixels = true

                    val sum = x + y
                    val diff = x - y

                    // Top-Left: Minimal (x + y)
                    if (sum < minSum) {
                        minSum = sum
                        tl = PointF(x.toFloat(), y.toFloat())
                    }
                    // Bottom-Right: Maximal (x + y)
                    if (sum > maxSum) {
                        maxSum = sum
                        br = PointF(x.toFloat(), y.toFloat())
                    }
                    // Bottom-Left: Minimal (x - y) -> Small X, Big Y gives very negative number
                    if (diff < minDiff) {
                        minDiff = diff
                        bl = PointF(x.toFloat(), y.toFloat())
                    }
                    // Top-Right: Maximal (x - y) -> Big X, Small Y gives very positive number
                    if (diff > maxDiff) {
                        maxDiff = diff
                        tr = PointF(x.toFloat(), y.toFloat())
                    }
                }
            }
        }

        // 3. Fallback: If no white body found (e.g., purely black image), use defaults
        if (!foundWhitePixels) {
            return getDefaultCorners(w, h)
        }

        // 4. Map Coordinates Back to Original Size & Apply Padding
        // We add padding so the dots aren't exactly on the edge of the color, giving the user room to touch.
        val padding = 10f / scaleFactor

        // Helper to scale and clamp a point
        fun fix(p: PointF, offsetX: Float, offsetY: Float): PointF {
            val finalX = (p.x / scaleFactor) + offsetX
            val finalY = (p.y / scaleFactor) + offsetY
            return PointF(finalX.coerceIn(0f, w), finalY.coerceIn(0f, h))
        }

        // Apply specific directional padding (move corners slightly inside the white body)
        return mapOf(
            0 to fix(tl, padding, padding),      // TL: Move Right/Down
            1 to fix(tr, -padding, padding),     // TR: Move Left/Down
            2 to fix(br, -padding, -padding),    // BR: Move Left/Up
            3 to fix(bl, padding, -padding)      // BL: Move Right/Up
        )
    }

    /**
     * Determines if a pixel is "Near White".
     * Criteria:
     * 1. High Brightness (R, G, and B are all high)
     * 2. Low Saturation (Difference between R, G, B is small)
     */
    private fun isNearWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        // 1. Brightness Check: All channels must be reasonably bright (> 140/255)
        // Adjust this threshold: Lower = detects darker grey papers. Higher = strictly white only.
        val minBrightness = 140
        if (r < minBrightness || g < minBrightness || b < minBrightness) return false

        // 2. Saturation Check: The color must be "grey-ish" or "white-ish".
        // If R is 200 but B is 100, that's orange/yellow, not paper white.
        val maxDiff = 40
        val diff = abs(r - g) + abs(g - b) + abs(b - r)

        // If the components are too different, it's a color, not white/grey paper
        return diff < maxDiff
    }

    private fun getDefaultCorners(w: Float, h: Float): Map<Int, PointF> {
        val m = 0.20f
        return mapOf(
            0 to PointF(w * m, h * m),
            1 to PointF(w * (1 - m), h * m),
            2 to PointF(w * (1 - m), h * (1 - m)),
            3 to PointF(w * m, h * (1 - m))
        )
    }

    // ----------------------------------------------------------------
    // STANDARD WARP & SAVE UTILS (Unchanged)
    // ----------------------------------------------------------------
    fun warpImage(bitmap: Bitmap, points: Map<Int, PointF>): Bitmap {
        val tl = points[0] ?: PointF(0f, 0f)
        val tr = points[1] ?: PointF(bitmap.width.toFloat(), 0f)
        val br = points[2] ?: PointF(bitmap.width.toFloat(), bitmap.height.toFloat())
        val bl = points[3] ?: PointF(0f, bitmap.height.toFloat())

        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val maxWidth = max(widthTop, widthBottom).toInt()

        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        val maxHeight = max(heightLeft, heightRight).toInt()

        val dst = floatArrayOf(
            0f, 0f,
            maxWidth.toFloat(), 0f,
            maxWidth.toFloat(), maxHeight.toFloat(),
            0f, maxHeight.toFloat()
        )
        val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val output = createBitmap(maxWidth, maxHeight)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return output
    }

    fun saveCompressedBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }
}