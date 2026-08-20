package com.swadeshiscanner.app

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object SmartFilterUtils {

    fun applyMagicColor(src: Bitmap): Bitmap {
        // 1. Normalize Lighting (Whitens the paper)
        val flatImage = normalizeLighting(src)

        // 2. Apply "Natural Punch" + Yellow Fix
        // Saturation 1.4 makes logos/stamps look vibrant
        return applyTextBoost(flatImage, satBoost = 1.4f)
    }

    fun applyGrayScale(src: Bitmap): Bitmap {
        val flatImage = normalizeLighting(src)
        return applyTextBoost(flatImage, satBoost = 0f)
    }

    private fun normalizeLighting(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height

        // 80px blur allows logos/stamps to survive background removal
        val smallW = 80
        val smallH = (h.toFloat() / w * smallW).toInt().coerceAtLeast(1)

        val small = src.scale(smallW, smallH, filter = true)
        val backgroundTiny = fastBoxBlur(small, radius = 10)
        val background = backgroundTiny.scale(w, h, filter = true)

        val pixels = IntArray(w * h)
        val bgPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        background.getPixels(bgPixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val bg = bgPixels[i]

            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val bgR = (bg shr 16) and 0xFF
            val bgG = (bg shr 8) and 0xFF
            val bgB = bg and 0xFF

            // Standard Division Normalization (Whitens background)
            var newR = (r * 255) / (bgR.coerceAtLeast(1))
            var newG = (g * 255) / (bgG.coerceAtLeast(1))
            var newB = (b * 255) / (bgB.coerceAtLeast(1))

            if (newR > 255) newR = 255
            if (newG > 255) newG = 255
            if (newB > 255) newB = 255

            pixels[i] = -0x1000000 or (newR shl 16) or (newG shl 8) or newB
        }

        val result = createBitmap(w, h)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyTextBoost(src: Bitmap, satBoost: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. PUNCHY CURVE (Gamma 1.6)
        // This fixes the "Dull/Unnatural" look.
        // It makes text dark and crisp.
        val curveLut = IntArray(256)
        for (i in 0..255) {
            val input = i / 255.0
            var output = input.pow(1.6)
            // Tiny toe lift to keep deep blacks solid
            if (i < 50) output *= 0.9
            curveLut[i] = (output * 255.0).toInt().coerceIn(0, 255)
        }

        for (i in pixels.indices) {
            val c = pixels[i]
            var r = (c shr 16) and 0xFF
            var g = (c shr 8) and 0xFF
            var b = c and 0xFF

            // Calculate Brightness & Color Saturation
            val lum = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val sat = maxC - minC

            if (satBoost <= 0f) {
                // GRAYSCALE MODE (Simple)
                val gray = min(r, min(g, b))
                val out = curveLut[gray]
                pixels[i] = -0x1000000 or (out shl 16) or (out shl 8) or out
            } else {
                // COLOR MODE - SMART LOGIC

                // A. THE YELLOW KILLER
                // If the pixel is dark (lum < 100), we assume its color is just "Sensor Noise" (Yellow).
                // We force it to be Grayscale.
                var isShadow = false
                if (lum < 100) {
                    isShadow = true
                }

                if (isShadow) {
                    // Force Shadow to Grayscale (Removes muddy yellow tint)
                    // Apply the contrast curve so it becomes Black, not Grey.
                    val darkVal = curveLut[lum]
                    r = darkVal; g = darkVal; b = darkVal
                } else {
                    // B. THE GLOW EFFECT (Bright Colors)
                    // If it's NOT a shadow, we check for color.

                    if (sat > 15) {
                        // It has color (Pink lines, Blue stamp)

                        // 1. Boost Saturation
                        r = (lum + (r - lum) * satBoost).toInt().coerceIn(0, 255)
                        g = (lum + (g - lum) * satBoost).toInt().coerceIn(0, 255)
                        b = (lum + (b - lum) * satBoost).toInt().coerceIn(0, 255)

                        // 2. Light Curve (Glow)
                        // Do NOT use the dark curveLut[]. That makes pink look muddy red.
                        // Instead, we use a lighter curve (0.95) to make it "pop".
                        r = (r * 0.95).toInt()
                        g = (g * 0.95).toInt()
                        b = (b * 0.95).toInt()

                    } else {
                        // It is Paper (White/Grey)
                        // Use standard curve to clean up noise
                        r = curveLut[r]
                        g = curveLut[g]
                        b = curveLut[b]
                    }
                }

                pixels[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        val result = createBitmap(w, h)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h); src.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1; val hm = h - 1; val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh); val g = IntArray(wh); val b = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int
        var p: Int; var yp: Int; var yi: Int
        val vmin = IntArray(max(w, h))
        var divsum = (div + 1) shr 1; divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) { dv[i] = (i / divsum) }
        var yw = 0; yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int; var stackstart: Int
        var sir: IntArray
        var rbs: Int; val r1 = radius + 1
        var routsum: Int; var goutsum: Int; var boutsum: Int
        var rinsum: Int; var ginsum: Int; var binsum: Int
        var y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = (p and 0x0000ff)
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            }
            stackpointer = radius
            for (x in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                stackstart = stackpointer - radius + div; sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (y == 0) vmin[x] = min(x + radius + 1, wm)
                p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = (p and 0x0000ff)
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div; sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi++
            }
            yw += w; y++
        }
        var x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
                if (i < hm) yp += w
            }
            yi = x; stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                stackstart = stackpointer - radius + div; sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (x == 0) vmin[y] = min(y + r1, hm) * w
                p = x + vmin[y]
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div; sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi += w
            }
            x++
        }
        val res = createBitmap(w, h)
        res.setPixels(pix, 0, w, 0, 0, w, h)
        return res
    }
}