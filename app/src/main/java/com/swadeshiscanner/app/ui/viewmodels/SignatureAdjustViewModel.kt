package com.swadeshiscanner.app.ui.viewmodels

import android.app.Application
import android.graphics.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.SignatureEntity
import com.swadeshiscanner.app.utils.SignatureUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SignatureAdjustViewModel(application: Application) : AndroidViewModel(application) {
    var pagePath by mutableStateOf<String?>(null)
    var pageId by mutableLongStateOf(-1L)
    
    var currentScale by mutableFloatStateOf(1f)
    var currentRotation by mutableFloatStateOf(0f)
    var offset by mutableStateOf(Offset(0f, 0f))
    
    var signatures = mutableStateListOf<SignatureEntity>()
    var selectedColor by mutableStateOf(Color.Black)
    var isLoading by mutableStateOf(false)
    var savedPath = mutableStateOf<String?>(null)

    fun init(path: String?, id: Long) {
        pagePath = path
        pageId = id
        loadSignatures()
    }

    private fun loadSignatures() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(getApplication()).dao()
            val list = db.getAllSignatures()
            withContext(Dispatchers.Main) {
                signatures.clear()
                signatures.addAll(list)
            }
        }
    }

    fun deleteSignature(signature: SignatureEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(getApplication()).dao()
            db.deleteSignature(signature)
            try { File(signature.path).delete() } catch (e: Exception) {}
            loadSignatures()
        }
    }

    fun saveDrawnSignature(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val processed = SignatureUtils.removeWhiteBackground(bitmap)
            val file = File(getApplication<Application>().filesDir, "sig_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                processed.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val db = AppDatabase.get(getApplication()).dao()
            db.insertSignature(SignatureEntity(path = file.absolutePath, dateAdded = System.currentTimeMillis()))
            loadSignatures()
        }
    }

    fun extractSignatureFromImage(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val processed = SignatureUtils.removeWhiteBackground(bitmap)
                    saveDrawnSignature(processed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addDateAsSignature() {
        viewModelScope.launch(Dispatchers.IO) {
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val bitmap = createTextBitmap(date)
            saveDrawnSignature(bitmap)
        }
    }

    private fun createTextBitmap(text: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 80f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        val baseline = -paint.ascent()
        val width = (paint.measureText(text) + 0.5f).toInt()
        val height = (baseline + paint.descent() + 0.5f).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, baseline, paint)
        return bitmap
    }

    fun saveMerged(
        selectedSignature: SignatureEntity?,
        scale: Float,
        rotation: Float,
        offset: androidx.compose.ui.geometry.Offset,
        viewSize: androidx.compose.ui.unit.IntSize,
        sigBoxSize: androidx.compose.ui.unit.IntSize
    ) {
        if (pagePath == null || selectedSignature == null) return
        
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseBmp = BitmapFactory.decodeFile(pagePath)
                val sigBmp = BitmapFactory.decodeFile(selectedSignature.path)
                
                if (baseBmp != null && sigBmp != null) {
                    val viewWidth = viewSize.width.toFloat()
                    val viewHeight = viewSize.height.toFloat()
                    
                    val bitmapWidth = baseBmp.width.toFloat()
                    val bitmapHeight = baseBmp.height.toFloat()
                    
                    val bitmapRatio = bitmapWidth / bitmapHeight
                    val viewRatio = viewWidth / viewHeight
                    
                    val finalScale: Float = if (bitmapRatio > viewRatio) {
                        viewWidth / bitmapWidth
                    } else {
                        viewHeight / bitmapHeight
                    }
                    
                    // The signature box on screen includes padding (8dp * 2 = 16dp).
                    // We need to account for this to get the exact ink size.
                    val density = getApplication<Application>().resources.displayMetrics.density
                    val paddingPx = 16f * density // Total padding in pixels
                    
                    val inkWidthPx = sigBoxSize.width.toFloat() - paddingPx
                    val inkHeightPx = sigBoxSize.height.toFloat() - paddingPx
                    
                    val mappedX = (bitmapWidth / 2f) + (offset.x / finalScale)
                    val mappedY = (bitmapHeight / 2f) + (offset.y / finalScale)
                    
                    val mappedWidth = (inkWidthPx * scale) / finalScale
                    val mappedHeight = (inkHeightPx * scale) / finalScale
                    
                    val mappedData = SignatureUtils.SignatureData(
                        x = mappedX,
                        y = mappedY,
                        width = mappedWidth,
                        height = mappedHeight,
                        rotation = rotation
                    )
                    
                    val layer = SignatureUtils.saveSignatureLayer(getApplication(), pageId, sigBmp, mappedData, baseBmp.width, baseBmp.height)
                    if (layer != null) {
                        val merged = SignatureUtils.applySignatureToImage(baseBmp, layer, mappedData)
                        val outFile = File(getApplication<Application>().filesDir, "Merged_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(outFile).use { out ->
                            merged.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        
                        // IMPORTANT: Delete old processed file if it exists to prevent data growth
                        val db = AppDatabase.get(getApplication()).dao()
                        val page = db.getPageById(pageId)
                        page.processedPath?.let { oldPath ->
                            if (oldPath != page.originalPath) {
                                val oldFile = File(oldPath)
                                if (oldFile.exists()) oldFile.delete()
                            }
                        }

                        db.updatePage(page.copy(processedPath = outFile.absolutePath))
                        
                        withContext(Dispatchers.Main) {
                            savedPath.value = outFile.absolutePath
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }
}

data class Offset(val x: Float, val y: Float)
