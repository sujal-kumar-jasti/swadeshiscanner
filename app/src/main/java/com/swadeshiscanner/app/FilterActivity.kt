package com.swadeshiscanner.app

import android.graphics.*
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.ui.screens.FilterScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max

class FilterActivity : AppCompatActivity() {
    private var pageId: Long = -1
    private var baseBitmap = mutableStateOf<Bitmap?>(null)
    private var previewBitmap = mutableStateOf<Bitmap?>(null)
    private var currentFilterId = mutableIntStateOf(0)
    private var isProcessing = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pageId = intent.getLongExtra("page_id", -1)
        if (pageId == -1L) {
            finish()
            return
        }

        prepareImage()

        setContent {
            SwadeshiScannerTheme {
                FilterScreen(
                    previewBitmap = previewBitmap.value,
                    currentFilterId = currentFilterId.intValue,
                    isProcessing = isProcessing.value,
                    onBack = { finish() },
                    onSave = { saveAndFinish() },
                    onFilterSelected = { id -> applyFilter(id) }
                )
            }
        }
    }

    private fun prepareImage() {
        isProcessing.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext)
            val page = db.dao().getPageById(pageId)

            var bitmap = BitmapFactory.decodeFile(page.originalPath)
            if (bitmap == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            val parsed = parseCropData(page.cropData)

            if (parsed.rotation != 0f) {
                val rotated = rotateBitmap(bitmap, parsed.rotation)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            val cropped = warpImage(bitmap, parsed.points)
            if (cropped != bitmap) {
                bitmap.recycle()
                bitmap = cropped
            }

            baseBitmap.value = bitmap
            
            withContext(Dispatchers.Main) {
                currentFilterId.intValue = parsed.filterId
                applyFilter(parsed.filterId)
            }
        }
    }

    private fun applyFilter(id: Int) {
        val base = baseBitmap.value ?: return
        currentFilterId.intValue = id
        isProcessing.value = true
        
        lifecycleScope.launch(Dispatchers.Default) {
            val result = when(id) {
                1 -> SmartFilterUtils.applyMagicColor(base)
                2 -> SmartFilterUtils.applyGrayScale(base)
                else -> base
            }
            withContext(Dispatchers.Main) {
                previewBitmap.value = result
                isProcessing.value = false
            }
        }
    }

    private fun saveAndFinish() {
        val result = previewBitmap.value ?: return
        isProcessing.value = true

        lifecycleScope.launch(Dispatchers.IO) {
            val outFile = File(filesDir, "Proc_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                result.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            val db = AppDatabase.get(applicationContext)
            val page = db.dao().getPageById(pageId)
            val oldData = page.cropData ?: ""
            val parts = oldData.split("#").toMutableList()

            val pointsStr = if (parts.isNotEmpty()) parts[0] else ""
            val rotStr = if (parts.size > 1) parts[1] else "0"
            val newCropData = "$pointsStr#$rotStr#${currentFilterId.intValue}"

            db.dao().updatePage(page.copy(
                processedPath = outFile.absolutePath,
                cropData = newCropData
            ))

            withContext(Dispatchers.Main) {
                isProcessing.value = false
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    // --- HELPER LOGIC (Copied from previous version) ---

    data class CropData(val points: Map<Int, PointF>?, val rotation: Float, val filterId: Int)

    private fun parseCropData(data: String?): CropData {
        if (data.isNullOrEmpty()) return CropData(null, 0f, 1)
        val parts = data.split("#")
        val map = mutableMapOf<Int, PointF>()
        if (parts[0].isNotEmpty()) {
            parts[0].split(";").forEachIndexed { i, s ->
                val c = s.split(",")
                if (c.size == 2) map[i] = PointF(c[0].toFloat(), c[1].toFloat())
            }
        }
        val rot = if (parts.size > 1) parts[1].toFloatOrNull() ?: 0f else 0f
        val fid = if (parts.size > 2) parts[2].toIntOrNull() ?: 1 else 1
        return CropData(if(map.size == 4) map else null, rot, fid)
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        if (angle % 360 == 0f) return source
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun warpImage(img: Bitmap, points: Map<Int, PointF>?): Bitmap {
        if (points == null || points.size != 4) return img

        val w = img.width.toFloat()
        val h = img.height.toFloat()

        fun clamp(p: PointF) = PointF(p.x.coerceIn(0f, w), p.y.coerceIn(0f, h))

        val p0 = clamp(points[0]!!)
        val p1 = clamp(points[1]!!)
        val p2 = clamp(points[2]!!)
        val p3 = clamp(points[3]!!)

        val w1 = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble())
        val w2 = hypot((p2.x - p3.x).toDouble(), (p2.y - p3.y).toDouble())
        val h1 = hypot((p3.x - p0.x).toDouble(), (p3.y - p0.y).toDouble())
        val h2 = hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble())

        val finalW = max(w1, w2).toInt()
        val finalH = max(h1, h2).toInt()

        if (finalW <= 0 || finalH <= 0) return img

        val output = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)

        val src = floatArrayOf(
            p0.x, p0.y,
            p1.x, p1.y,
            p3.x, p3.y,
            p2.x, p2.y
        )

        val dst = floatArrayOf(
            0f, 0f,
            finalW.toFloat(), 0f,
            0f, finalH.toFloat(),
            finalW.toFloat(), finalH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(img, matrix, paint)

        return output
    }
}
