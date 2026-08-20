package com.swadeshiscanner.app.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swadeshiscanner.app.ScanUtils
import com.swadeshiscanner.app.SmartFilterUtils
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.utils.SignatureUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SinglePageViewModel(application: Application) : AndroidViewModel(application) {
    var currentPage by mutableStateOf<PageEntity?>(null)
    var allPages by mutableStateOf<List<PageEntity>>(emptyList())
    var currentIndex by mutableIntStateOf(0)
    var docName by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun loadData(pageId: Long, docName: String) {
        this.docName = docName
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(getApplication()).dao()
            val targetPage = dao.getPageById(pageId) ?: return@launch
            val pages = dao.getPagesList(targetPage.docId).sortedBy { it.orderIndex }
            withContext(Dispatchers.Main) {
                allPages = pages
                currentPage = targetPage
                currentIndex = pages.indexOfFirst { it.id == pageId }.coerceAtLeast(0)
            }

            if (targetPage.processedPath == null) {
                processPage(targetPage)
            }
        }
    }

    private fun processPage(page: PageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                val newPath = applyFullProcessingPipeline(page.originalPath, page.cropData ?: "", getFilterIdFromData(page.cropData))
                val updated = page.copy(processedPath = newPath)
                AppDatabase.get(getApplication()).dao().updatePage(updated)
                reapplySignatureIfExist(updated.id, newPath)
                withContext(Dispatchers.Main) {
                    updatePage(updated)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message
                    isLoading = false
                }
            }
        }
    }

    fun movePage(delta: Int) {
        val newIndex = currentIndex + delta
        if (newIndex in allPages.indices) {
            currentIndex = newIndex
            currentPage = allPages[newIndex]
        }
    }

    fun updatePage(page: PageEntity) {
        val index = allPages.indexOfFirst { it.id == page.id }
        if (index != -1) {
            val newList = allPages.toMutableList()
            newList[index] = page
            allPages = newList
            if (currentIndex == index) {
                currentPage = page
            }
        }
    }

    fun performRotation() {
        val page = currentPage ?: return
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SignatureUtils.rotateLayerFile(getApplication(), page.id)

                val db = AppDatabase.get(getApplication()).dao()
                val oldData = page.cropData ?: ""
                val parts = oldData.split("#")
                val currentRot = if (parts.size > 1) parts[1].toFloatOrNull() ?: 0f else 0f
                val filterId = if (parts.size > 2) parts[2].toIntOrNull() ?: 1 else 1

                val newRot = (currentRot + 90f) % 360f
                val currentPoints = parsePoints(parts[0])
                var newPointsStr = parts[0]

                if (currentPoints != null) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(page.originalPath, opts)

                    val isCurrentlyFlipped = (currentRot / 90f).roundToInt() % 2 != 0
                    val currentW = if (isCurrentlyFlipped) opts.outHeight.toFloat() else opts.outWidth.toFloat()
                    val currentH = if (isCurrentlyFlipped) opts.outWidth.toFloat() else opts.outHeight.toFloat()

                    val newPoints = mutableMapOf<Int, PointF>()
                    currentPoints.forEach { (k, p) ->
                        newPoints[(k + 1) % 4] = PointF(currentH - p.y, p.x)
                    }
                    newPointsStr = cropPointsToString(newPoints)
                }

                val newCropData = "$newPointsStr#$newRot#$filterId"
                val newPath = applyFullProcessingPipeline(page.originalPath, newCropData, filterId)
                
                deleteOldProcessedFile(page.processedPath, page.originalPath)

                val updatedPage = page.copy(cropData = newCropData, processedPath = newPath)
                db.updatePage(updatedPage)
                
                reapplySignatureIfExist(updatedPage.id, newPath)

                withContext(Dispatchers.Main) {
                    updatePage(updatedPage)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message
                    isLoading = false
                }
            }
        }
    }

    fun saveNotes(notes: String) {
        val page = currentPage ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(getApplication()).dao()
            val updated = page.copy(notes = notes)
            db.updatePage(updated)
            withContext(Dispatchers.Main) {
                updatePage(updated)
            }
        }
    }

    private suspend fun reapplySignatureIfExist(pageId: Long, currentPath: String) {
        val layerData = SignatureUtils.getSignatureLayer(getApplication(), pageId) ?: return

        if (File(currentPath).exists()) {
            try {
                val baseBmp = BitmapFactory.decodeFile(currentPath) ?: return
                val mergedBmp = SignatureUtils.applySignatureToImage(baseBmp, layerData.first, layerData.second)
                FileOutputStream(File(currentPath)).use { out ->
                    mergedBmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                if (baseBmp != mergedBmp) baseBmp.recycle()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun applyFullProcessingPipeline(originalPath: String, cropData: String, filterId: Int): String {
        val context = getApplication<Application>()
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bitmap = BitmapFactory.decodeFile(originalPath, options) ?: return originalPath

        val parts = cropData.split("#")
        val rotation = if (parts.size > 1) parts[1].toFloatOrNull() ?: 0f else 0f
        
        if (rotation != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotation)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (bitmap != rotated) bitmap.recycle()
            bitmap = rotated
        }

        val pts = parsePoints(parts[0])
        if (pts != null) {
            val cropped = ScanUtils.warpImage(bitmap, pts)
            if (cropped != bitmap) bitmap.recycle()
            bitmap = cropped
        }

        val filteredBmp = when (filterId) {
            2 -> SmartFilterUtils.applyGrayScale(bitmap)
            1 -> SmartFilterUtils.applyMagicColor(bitmap)
            else -> bitmap
        }

        val file = File(context.filesDir, "Proc_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            filteredBmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        
        if (filteredBmp != bitmap) bitmap.recycle()
        
        return file.absolutePath
    }


    private fun getFilterIdFromData(cropData: String?): Int {
        if (cropData == null) return 1
        val parts = cropData.split("#")
        return if (parts.size >= 3) parts[2].toIntOrNull() ?: 1 else 1
    }

    private fun getRotationFromCropData(cropData: String?): Float {
        if (cropData == null) return 0f
        val parts = cropData.split("#")
        return if (parts.size >= 2) parts[1].toFloatOrNull() ?: 0f else 0f
    }

    private fun parsePoints(cropData: String): Map<Int, PointF>? {
        try {
            val ptsPart = cropData.split("#")[0]
            val points = mutableMapOf<Int, PointF>()
            ptsPart.split(";").forEachIndexed { i, s ->
                val coords = s.split(",")
                points[i] = PointF(coords[0].toFloat(), coords[1].toFloat())
            }
            return if (points.size == 4) points else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun cropPointsToString(points: Map<Int, PointF>): String {
        val sb = StringBuilder()
        for (i in 0 until 4) {
            val p = points[i] ?: PointF(0f, 0f)
            sb.append("${p.x},${p.y}")
            if (i < 3) sb.append(";")
        }
        return sb.toString()
    }

    private fun deleteOldProcessedFile(processedPath: String?, originalPath: String) {
        if (processedPath != null && processedPath != originalPath) {
            val file = File(processedPath)
            if (file.exists()) file.delete()
        }
    }
}
