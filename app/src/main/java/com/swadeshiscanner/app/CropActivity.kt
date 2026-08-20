package com.swadeshiscanner.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.screens.CropScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.components.RenameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class CropState(
    val originalPath: String,
    var cropPoints: Map<Int, PointF>? = null,
    var rotationAngle: Float = 0f,
    var pointSourceWidth: Int = 0,
    var pointSourceHeight: Int = 0
)

data class CachedPageData(val points: Map<Int, PointF>?, val rotation: Float)
data class ParsedCropData(val points: Map<Int, PointF>?, val rotation: Float, val filterId: Int)

class CropActivity : AppCompatActivity() {
    private val cropStates = mutableStateListOf<CropState>()
    private val currentIndexState = mutableStateOf(0)
    private val filterTypeState = mutableStateOf(1)
    private val showRenameDialogState = mutableStateOf<Pair<String, (String) -> Unit>?>(null)
    
    private var currentBitmap: Bitmap? = null
    private var imageViewRef: ImageView? = null
    private var polygonViewRef: PolygonView? = null

    private var isEditMode = false
    private var pageIdToUpdate: Long = -1
    private var existingDocId: Long = -1
    private var nextOrderIndex: Int = 0
    private var isOcrMode = false
    private var isIdCardMode = false
    private var isSignatureMode = false
    private var isBookMode = false
    private var isFormulaMode = false
    private var isTranslateMode = false

    companion object {
        val temporaryCropCache = mutableMapOf<String, CachedPageData>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        if (!parseIntentData()) return

        setContent {
            SwadeshiScannerTheme {
                CropScreen(
                    currentBitmap = currentBitmap,
                    thumbnails = cropStates.map { it.originalPath },
                    currentIndex = currentIndexState.value,
                    filterName = getFilterLabel(filterTypeState.value),
                    filterIcon = getFilterIcon(filterTypeState.value),
                    onBack = { returnResultToCamera() },
                    onDone = { handleSave() },
                    onRotate = { rotateCurrent() },
                    onFilterClick = { showBatchFilterDialog() },
                    onDelete = { deleteCurrentPage() },
                    onThumbClick = { index -> 
                        saveCurrentCropPoints()
                        currentIndexState.value = index
                        loadPage(index)
                    },
                    onMovePage = { from, to -> reorderStates(from, to) },
                    onBatchFilter = { filterId -> applyBatchFilter(filterId) },
                    onBatchDelete = { indices -> deleteBatchPages(indices) },
                    onViewCreated = { iv, poly ->
                        imageViewRef = iv
                        polygonViewRef = poly
                        if (isEditMode && pageIdToUpdate != -1L) {
                            loadInitialEditPage()
                        } else {
                            loadPage(0)
                        }
                    }
                )

                showRenameDialogState.value?.let { (prefill, onPos) ->
                    RenameDialog(
                        title = "Save Document",
                        prefill = prefill,
                        onDismiss = { showRenameDialogState.value = null },
                        onPositive = {
                            showRenameDialogState.value = null
                            onPos(it)
                        }
                    )
                }
            }
        }
    }

    private fun applyBatchFilter(filterId: Int) {
        saveCurrentCropPoints()
        filterTypeState.value = filterId
        loadPage(currentIndexState.value)
    }

    private fun deleteBatchPages(indices: Set<Int>) {
        val sortedIndices = indices.sortedDescending()
        sortedIndices.forEach { index ->
            if (cropStates.size > 1) {
                cropStates.removeAt(index)
            } else {
                finish()
                return
            }
        }
        if (currentIndexState.value >= cropStates.size) {
            currentIndexState.value = cropStates.size - 1
        }
        loadPage(currentIndexState.value)
    }

    private fun reorderStates(from: Int, to: Int) {
        if (from == to || from !in cropStates.indices || to !in cropStates.indices) return
        saveCurrentCropPoints()
        val item = cropStates.removeAt(from)
        cropStates.add(to, item)
        // Adjust current index if it was moved
        if (currentIndexState.value == from) {
            currentIndexState.value = to
        } else if (currentIndexState.value in to until from) {
            currentIndexState.value++
        } else if (currentIndexState.value in (from + 1)..to) {
            currentIndexState.value--
        }
        loadPage(currentIndexState.value)
    }

    private fun loadInitialEditPage() {
        lifecycleScope.launch {
            val pageData = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).dao().getPageById(pageIdToUpdate) }
            val parsed = stringToCropPoints(pageData.cropData)
            val state = cropStates[0]
            state.rotationAngle = parsed.rotation
            if (parsed.points != null) state.cropPoints = parsed.points
            filterTypeState.value = parsed.filterId
            loadPage(0)
        }
    }

    private fun loadPage(index: Int) {
        if (index !in cropStates.indices) return
        val state = cropStates[index]
        val iv = imageViewRef ?: return
        val poly = polygonViewRef ?: return

        if (temporaryCropCache.containsKey(state.originalPath) && state.cropPoints == null) {
            val cached = temporaryCropCache[state.originalPath]!!
            state.cropPoints = cached.points
            state.rotationAngle = cached.rotation
        }

        Glide.with(this).asBitmap().load(state.originalPath).diskCacheStrategy(DiskCacheStrategy.NONE).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                var bmp = resource.copy(Bitmap.Config.ARGB_8888, true)
                if (state.rotationAngle != 0f) {
                    val m = Matrix().apply { postRotate(state.rotationAngle) }
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
                currentBitmap = bmp
                iv.setImageBitmap(bmp)
                if (state.pointSourceWidth == 0 || state.pointSourceWidth != bmp.width) {
                    state.pointSourceWidth = bmp.width; state.pointSourceHeight = bmp.height
                }
                if (state.cropPoints == null) state.cropPoints = ScanUtils.detectCorners(bmp)
                
                iv.post {
                    if (currentBitmap != null && state.cropPoints != null) {
                        val screenPoints = mapImageToScreenCoords(state.cropPoints!!, iv, currentBitmap!!)
                        poly.setPoints(screenPoints)
                    }
                }
            }
            override fun onLoadCleared(p: Drawable?) {}
        })
    }

    private fun handleSave() {
        saveCurrentCropPoints()
        when {
            isEditMode -> saveSingleEdit()
            existingDocId != -1L -> saveBatchToDb(null)
            isIdCardMode || isOcrMode || isSignatureMode || isFormulaMode || isTranslateMode -> {
                val prefix = when {
                    isIdCardMode -> "ID"
                    isOcrMode -> "OCR"
                    isFormulaMode -> "MATH"
                    isTranslateMode -> "TRNS"
                    else -> "SIG"
                }
                saveBatchToDb("TEMP_${prefix}_${System.currentTimeMillis()}")
            }
            else -> promptForDocNameOrSave()
        }
    }

    private fun rotateCurrent() {
        val state = cropStates.getOrNull(currentIndexState.value) ?: return
        saveCurrentCropPoints()
        state.rotationAngle = (state.rotationAngle + 90f) % 360f
        
        // Also rotate signature layer if it exists
        if (isEditMode && pageIdToUpdate != -1L) {
            com.swadeshiscanner.app.utils.SignatureUtils.rotateLayerFile(this, pageIdToUpdate)
        }
        
        if (state.cropPoints != null && state.pointSourceWidth > 0) {
            val oldW = state.pointSourceWidth.toFloat(); val oldH = state.pointSourceHeight.toFloat()
            val newPoints = mutableMapOf<Int, PointF>()
            state.cropPoints!!.forEach { (k, p) -> newPoints[(k + 1) % 4] = PointF(oldH - p.y, p.x) }
            state.cropPoints = newPoints
            state.pointSourceWidth = oldH.toInt(); state.pointSourceHeight = oldW.toInt()
        }
        loadPage(currentIndexState.value)
    }

    private fun saveCurrentCropPoints() {
        val state = cropStates.getOrNull(currentIndexState.value) ?: return
        val poly = polygonViewRef ?: return
        val iv = imageViewRef ?: return
        if (currentBitmap != null && !currentBitmap!!.isRecycled) {
            state.cropPoints = mapScreenToImageCoords(poly.getPoints(), iv, currentBitmap!!)
            state.pointSourceWidth = currentBitmap!!.width; state.pointSourceHeight = currentBitmap!!.height
        }
        temporaryCropCache[state.originalPath] = CachedPageData(state.cropPoints, state.rotationAngle)
    }

    private fun saveBatchToDb(docName: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(applicationContext)
                var finalDocId = existingDocId
                
                if (docName != null && existingDocId == -1L) {
                    finalDocId = db.dao().insertDoc(DocumentEntity(name = docName, pageCount = 0, thumbnailPath = ""))
                }

                if (finalDocId == -1L) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CropActivity, "Error saving document", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                cropStates.forEachIndexed { i, state ->
                    val fileName = "Orig_${System.currentTimeMillis()}_$i.jpg"
                    val origFile = File(filesDir, fileName)
                    
                    var success = false
                    try {
                        if (state.originalPath.startsWith("content://")) {
                            val uri = android.net.Uri.parse(state.originalPath)
                            contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(origFile).use { output ->
                                    input.copyTo(output)
                                }
                                success = true
                            }
                        } else {
                            val sourceFile = File(state.originalPath)
                            if (sourceFile.exists()) {
                                sourceFile.copyTo(origFile, overwrite = true)
                                success = true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    if (success && origFile.exists()) {
                        val ptsStr = cropPointsToString(state.cropPoints, state.rotationAngle, filterTypeState.value)
                        db.dao().insertPage(PageEntity(
                            docId = finalDocId,
                            originalPath = origFile.absolutePath,
                            processedPath = null,
                            orderIndex = if (existingDocId != -1L) nextOrderIndex + i else i,
                            cropData = ptsStr
                        ))
                    }
                }

                val allPages = db.dao().getPagesList(finalDocId)
                db.dao().updateDocMeta(finalDocId, allPages.size, allPages.firstOrNull()?.processedPath ?: allPages.firstOrNull()?.originalPath)

                // Clear session cache
                com.swadeshiscanner.app.ScanRepository.clearSession(applicationContext)

                withContext(Dispatchers.Main) {
                    if (isIdCardMode || isOcrMode || isSignatureMode || isFormulaMode || isTranslateMode) {
                        val resultIntent = Intent().apply {
                            putExtra("saved_doc_id", finalDocId)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else if (isBookMode) {
                        val intent = Intent(this@CropActivity, com.swadeshiscanner.app.activities.BookResultActivity::class.java).apply {
                            putExtra("doc_id", finalDocId)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // Return result to CameraActivity so it can finish too
                        val resultIntent = Intent().apply {
                            putExtra("saved_doc_id", finalDocId)
                            putExtra("doc_name", docName)
                        }
                        setResult(RESULT_OK, resultIntent)
                        
                        val intent = Intent(this@CropActivity, DocDetailActivity::class.java).apply {
                            putExtra("doc_id", finalDocId)
                            putExtra("doc_name", docName)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveSingleEdit() {
        lifecycleScope.launch(Dispatchers.IO) {
            val state = cropStates[0]
            val ptsStr = cropPointsToString(state.cropPoints, state.rotationAngle, filterTypeState.value)
            val dao = AppDatabase.get(applicationContext).dao()
            val page = dao.getPageById(pageIdToUpdate)
            
            // Delete old processed file if it exists
            page.processedPath?.let { path ->
                if (path != page.originalPath) {
                    try { File(path).delete() } catch (e: Exception) {}
                }
            }
            
            dao.updatePage(page.copy(cropData = ptsStr, processedPath = null))
            withContext(Dispatchers.Main) { setResult(RESULT_OK); finish() }
        }
    }

    private fun promptForDocNameOrSave() {
        val name = "Doc ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(System.currentTimeMillis())}"
        showRenameDialogState.value = name to { saveBatchToDb(it) }
    }

    private fun deleteCurrentPage() {
        if (cropStates.size > 1) {
            cropStates.removeAt(currentIndexState.value)
            if (currentIndexState.value >= cropStates.size) currentIndexState.value = cropStates.size - 1
            loadPage(currentIndexState.value)
        } else finish()
    }

    private fun returnResultToCamera() {
        setResult(RESULT_OK, Intent().putStringArrayListExtra("updated_batch_paths", ArrayList(cropStates.map { it.originalPath })))
        finish()
    }

    private fun parseIntentData(): Boolean {
        val raw = intent.getStringArrayListExtra("batch_images")
        val single = intent.getStringExtra("image_path")
        isOcrMode = intent.getBooleanExtra("is_ocr_mode", false)
        isIdCardMode = intent.getBooleanExtra("is_id_card_mode", false)
        isSignatureMode = intent.getBooleanExtra("is_signature_mode", false)
        isBookMode = intent.getBooleanExtra("is_book_mode", false)
        isFormulaMode = intent.getBooleanExtra("is_formula_mode", false)
        isTranslateMode = intent.getBooleanExtra("is_translate_mode", false)
        if (intent.hasExtra("edit_page_id")) {
            isEditMode = true; pageIdToUpdate = intent.getLongExtra("edit_page_id", -1)
            cropStates.add(CropState(intent.getStringExtra("original_image_path") ?: return false))
        } else {
            existingDocId = intent.getLongExtra("existing_doc_id", -1); nextOrderIndex = intent.getIntExtra("next_order_index", 0)
            (raw ?: (if(single!=null) arrayListOf(single) else null))?.forEach { cropStates.add(CropState(it)) } ?: run { finish(); return false }
        }
        return true
    }

    private fun getFilterLabel(t: Int) = when(t) { 1 -> "Magic"; 2 -> "Gray"; else -> "Original" }
    private fun getFilterIcon(t: Int) = when(t) { 1 -> R.drawable.ic_auto_fix; 2 -> R.drawable.ic_contrast; else -> R.drawable.ic_image }

    private fun showBatchFilterDialog() {
        saveCurrentCropPoints()
        // Toggle through filters for now
        filterTypeState.value = (filterTypeState.value + 1) % 3
        loadPage(currentIndexState.value)
    }

    private fun mapScreenToImageCoords(screenPoints: Map<Int, PointF>, view: ImageView, image: Bitmap): Map<Int, PointF> {
        val mapped = mutableMapOf<Int, PointF>(); val rect = getBitmapRect(view, image)
        if (rect.width() == 0f) return screenPoints
        val sx = image.width.toFloat() / rect.width(); val sy = image.height.toFloat() / rect.height()
        screenPoints.forEach { (k, p) -> mapped[k] = PointF(((p.x - rect.left) * sx).coerceIn(0f, image.width.toFloat()), ((p.y - rect.top) * sy).coerceIn(0f, image.height.toFloat())) }
        return mapped
    }

    private fun mapImageToScreenCoords(imagePoints: Map<Int, PointF>, view: ImageView, image: Bitmap): Map<Int, PointF> {
        val mapped = mutableMapOf<Int, PointF>(); val rect = getBitmapRect(view, image)
        val sx = rect.width() / image.width.toFloat(); val sy = rect.height() / image.height.toFloat()
        imagePoints.forEach { (k, p) -> mapped[k] = PointF(rect.left + (p.x * sx), rect.top + (p.y * sy)) }
        return mapped
    }

    private fun getBitmapRect(view: ImageView, bitmap: Bitmap): RectF {
        val vw = view.width.toFloat() - view.paddingLeft - view.paddingRight; val vh = view.height.toFloat() - view.paddingTop - view.paddingBottom
        val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
        if (iw == 0f || ih == 0f || vw == 0f) return RectF()
        val scale = if (iw / ih > vw / vh) vw / iw else vh / ih
        val dx = view.paddingLeft + (vw - iw * scale) * 0.5f; val dy = view.paddingTop + (vh - ih * scale) * 0.5f
        return RectF(dx, dy, dx + iw * scale, dy + ih * scale)
    }

    private fun cropPointsToString(points: Map<Int, PointF>?, rot: Float, fid: Int): String {
        val sb = StringBuilder()
        if (points != null) { for (i in 0 until 4) { val p = points[i] ?: PointF(0f, 0f); sb.append("${p.x},${p.y}"); if (i < 3) sb.append(";") } }
        sb.append("#$rot#$fid"); return sb.toString()
    }

    private fun stringToCropPoints(data: String?): ParsedCropData {
        if (data.isNullOrEmpty()) return ParsedCropData(null, 0f, 1)
        val parts = data.split("#"); val map = mutableMapOf<Int, PointF>()
        if (parts[0].isNotEmpty()) { parts[0].split(";").forEachIndexed { i, s -> val c = s.split(","); if (c.size == 2) map[i] = PointF(c[0].toFloat(), c[1].toFloat()) } }
        return ParsedCropData(if(map.size==4) map else null, if (parts.size > 1) parts[1].toFloatOrNull() ?: 0f else 0f, if (parts.size > 2) parts[2].toIntOrNull() ?: 1 else 1)
    }
}
