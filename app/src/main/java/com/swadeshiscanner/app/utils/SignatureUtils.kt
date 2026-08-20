package com.swadeshiscanner.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object SignatureUtils {

    data class SignatureData(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rotation: Float
    )

    fun removeWhiteBackground(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Tolerance for white
            if (r > 200 && g > 200 && b > 200) {
                pixels[i] = Color.TRANSPARENT
            } else {
                pixels[i] = pixel
            }
        }
        bmOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmOut
    }

    fun applyHighContrast(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmOut = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val l = (0.299 * r + 0.587 * g + 0.114 * b)
            pixels[i] = if (l < 128) Color.BLACK else Color.WHITE
        }
        bmOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmOut
    }

    /**
     * UPDATED: Returns the combined full-page bitmap so it can be used immediately.
     */
    fun saveSignatureLayer(context: Context, pageId: Long, newSigBmp: Bitmap, data: SignatureData, basePageWidth: Int, basePageHeight: Int): Bitmap? {
        return try {
            val sigFile = File(context.filesDir, "sig_layer_$pageId.png")

            // 1. Create a full-size transparent canvas
            val combinedBmp = Bitmap.createBitmap(basePageWidth, basePageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combinedBmp)

            // 2. If an old layer exists, draw it first
            if (sigFile.exists()) {
                val oldLayer = BitmapFactory.decodeFile(sigFile.absolutePath)
                if (oldLayer != null) {
                    val scaleX = basePageWidth.toFloat() / oldLayer.width
                    val scaleY = basePageHeight.toFloat() / oldLayer.height
                    val m = Matrix().apply { postScale(scaleX, scaleY) }
                    canvas.drawBitmap(oldLayer, m, null)
                    oldLayer.recycle()
                }
            }

            // 3. Draw the NEW signature
            val matrix = Matrix()
            val sx = data.width / newSigBmp.width.toFloat()
            val sy = data.height / newSigBmp.height.toFloat()
            matrix.postScale(sx, sy)

            // Rotation pivot logic
            val scaledW = newSigBmp.width * sx
            val scaledH = newSigBmp.height * sy
            matrix.postRotate(data.rotation, scaledW / 2f, scaledH / 2f)
            matrix.postTranslate(data.x - (scaledW / 2f), data.y - (scaledH / 2f))

            canvas.drawBitmap(newSigBmp, matrix, null)

            // 4. Save to file
            FileOutputStream(sigFile).use { out ->
                combinedBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 5. Save metadata
            val metaFile = File(context.filesDir, "sig_meta_$pageId.txt")
            metaFile.writeText("0,0,$basePageWidth,$basePageHeight,0")

            // Return the full layer so we can merge it immediately without reloading
            combinedBmp

        } catch (e: Exception) {
            Log.e("SignatureUtils", "Save failed: ${e.message}")
            null
        }
    }

    fun getSignatureLayer(context: Context, pageId: Long): Pair<Bitmap, SignatureData>? {
        val sigFile = File(context.filesDir, "sig_layer_$pageId.png")
        val metaFile = File(context.filesDir, "sig_meta_$pageId.txt")
        if (!sigFile.exists() || !metaFile.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(sigFile.absolutePath)
            val p = metaFile.readText().split(",")
            if (p.size >= 5) {
                val data = SignatureData(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat())
                Pair(bitmap, data)
            } else null
        } catch (e: Exception) { null }
    }

    fun rotateLayerFile(context: Context, pageId: Long) {
        val sigFile = File(context.filesDir, "sig_layer_$pageId.png")
        val metaFile = File(context.filesDir, "sig_meta_$pageId.txt")
        if (!sigFile.exists()) return

        try {
            val bmp = BitmapFactory.decodeFile(sigFile.absolutePath) ?: return
            val matrix = Matrix().apply { postRotate(90f) }
            val rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

            FileOutputStream(sigFile).use { out ->
                rotatedBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (metaFile.exists()) {
                val p = metaFile.readText().split(",")
                if (p.size >= 5) {
                    metaFile.writeText("0,0,${p[3]},${p[2]},0")
                }
            }

            bmp.recycle()
            if (rotatedBmp != bmp) rotatedBmp.recycle()

        } catch (e: Exception) { e.printStackTrace() }
    }

    fun applySignatureToImage(baseImage: Bitmap, layer: Bitmap, data: SignatureData?): Bitmap {
        val result = baseImage.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Ensure the layer covers the entire base image
        val r = Rect(0, 0, layer.width, layer.height)
        val d = Rect(0, 0, baseImage.width, baseImage.height)

        canvas.drawBitmap(layer, r, d, null)
        return result
    }
}