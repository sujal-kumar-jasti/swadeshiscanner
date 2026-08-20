package com.swadeshiscanner.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.components.DocMenuBottomSheet
import com.swadeshiscanner.app.ui.components.DocMenuOption
import com.swadeshiscanner.app.ui.components.SaveMenuSheet
import com.swadeshiscanner.app.ui.components.ShareMenuSheet
import com.swadeshiscanner.app.ui.components.RenameDialog
import com.swadeshiscanner.app.ui.screens.DocDetailScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.utils.ExportUtils
import com.swadeshiscanner.app.utils.ImportUtils
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
class DocDetailActivity : AppCompatActivity() {

    enum class SheetType { NONE, SHARE, SAVE, MENU }

    // Data & State
    private var docId: Long = -1
    private val isProcessingState = mutableStateOf(false)
    private val pagesState = mutableStateListOf<PageEntity>()
    private val selectedIdsState = mutableStateSetOf<Long>()
    private val docNameState = mutableStateOf("Document")
    private val showAddButtonState = mutableStateOf(true)
    private val activeSheet = mutableStateOf(SheetType.NONE)
    private val showRenameDialogState = mutableStateOf<RenameDialogData?>(null)
    private val showPasswordDialogState = mutableStateOf(false)

    private var processingJob: Job? = null
    private val isProcessingAtomic = AtomicBoolean(false)
    private var tempGalleryImportPath: String? = null

    data class RenameDialogData(val title: String, val message: String?, val posText: String, val isInput: Boolean, val prefill: String, val onPos: (String) -> Unit)

    // --- LAUNCHERS ---
    private val singlePageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) { loadData() }
    }

    private val addPageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Pages added", Toast.LENGTH_SHORT).show()
            loadData()
            tempGalleryImportPath?.let { path ->
                try { File(path).delete() } catch (e: Exception) {}
                tempGalleryImportPath = null
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { processGalleryUri(it) } }

    private val importPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isProcessingState.value = true
            lifecycleScope.launch(Dispatchers.IO) {
                val tempPaths = ImportUtils.importPdfToImages(this@DocDetailActivity, it)
                val db = AppDatabase.get(applicationContext).dao()
                val startIdx = pagesState.size

                tempPaths.forEachIndexed { index, tempPath ->
                    val tempFile = File(tempPath)
                    val permFile = File(filesDir, "Orig_${System.currentTimeMillis()}_$index.jpg")
                    try {
                        val bitmap = BitmapFactory.decodeFile(tempPath)
                        if (bitmap != null) {
                            FileOutputStream(permFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            bitmap.recycle()
                            tempFile.delete()
                        } else if (tempFile.exists()) {
                            tempFile.copyTo(permFile, overwrite = true)
                            tempFile.delete()
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    val finalPath = if (permFile.exists()) permFile.absolutePath else tempPath
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(finalPath, options)
                    val w = options.outWidth.toFloat()
                    val h = options.outHeight.toFloat()
                    val fullCropString = "0.0,0.0;${w},0.0;${w},${h};0.0,${h}#0.0#1"

                    db.insertPage(PageEntity(docId = docId, originalPath = finalPath, processedPath = null, orderIndex = startIdx + index, cropData = fullCropString))
                }
                db.updateDocMeta(docId, startIdx + tempPaths.size, null)
                withContext(Dispatchers.Main) {
                    isProcessingState.value = false
                    Toast.makeText(this@DocDetailActivity, "Imported ${tempPaths.size} pages", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        docId = intent.getLongExtra("doc_id", -1)
        if (docId == -1L) {
            Toast.makeText(this, "Error: Invalid Document ID", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        docNameState.value = intent.getStringExtra("doc_name") ?: "Document"

        loadData()

        setContent {
            SwadeshiScannerTheme {
                val sheetState = rememberModalBottomSheetState()
                var currentSheet by activeSheet

                val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    uri?.let { processGalleryUri(it) }
                }

                DocDetailScreen(
                    docName = docNameState.value,
                    pages = pagesState,
                    selectedIds = selectedIdsState,
                    isProcessing = isProcessingState.value,
                    onBack = { if (selectedIdsState.isNotEmpty()) selectedIdsState.clear() else finish() },
                    onRename = { showRenameDialog() },
                    onDelete = {
                        if (selectedIdsState.isNotEmpty()) {
                            showCustomDialog("Delete Pages?", "Delete ${selectedIdsState.size} page(s)?", "DELETE", android.R.color.holo_red_dark, false, "") {
                                deleteSelectedPages(pagesState.filter { selectedIdsState.contains(it.id) })
                            }
                        } else if (pagesState.isNotEmpty()) {
                            showCustomDialog("Delete Document?", "Delete entire document?", "DELETE", android.R.color.holo_red_dark, false, "") { deleteEntireDocument() }
                        }
                    },
                    onMenuClick = { currentSheet = SheetType.MENU },
                    onPageClick = { page ->
                        if (selectedIdsState.isNotEmpty()) {
                            if (selectedIdsState.contains(page.id)) selectedIdsState.remove(page.id) else selectedIdsState.add(page.id)
                        } else {
                            if (page.processedPath.isNullOrEmpty()) Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show() else openSinglePageView(page)
                        }
                    },
                    onPageLongClick = { page ->
                        if (selectedIdsState.contains(page.id)) selectedIdsState.remove(page.id) else selectedIdsState.add(page.id)
                    },
                    onAddPage = { showAddPageOptions() },
                    onShare = {
                        if (pagesState.any { it.processedPath.isNullOrEmpty() }) Toast.makeText(this, "Wait for processing...", Toast.LENGTH_SHORT).show()
                        else currentSheet = SheetType.SHARE
                    },
                    onSave = {
                        if (pagesState.any { it.processedPath.isNullOrEmpty() }) Toast.makeText(this, "Wait for processing...", Toast.LENGTH_SHORT).show()
                        else currentSheet = SheetType.SAVE
                    },
                    onMovePage = { from, to -> reorderPages(from, to) },
                    showAddButton = showAddButtonState.value
                )

                showRenameDialogState.value?.let { data ->
                    RenameDialog(
                        title = data.title,
                        message = data.message,
                        positiveText = data.posText,
                        isInput = data.isInput,
                        prefill = data.prefill,
                        onDismiss = { showRenameDialogState.value = null },
                        onPositive = {
                            showRenameDialogState.value = null
                            data.onPos(it)
                        }
                    )
                }

                if (currentSheet != SheetType.NONE) {
                    ModalBottomSheet(
                        onDismissRequest = { currentSheet = SheetType.NONE },
                        sheetState = sheetState
                    ) {
                        when (currentSheet) {
                            SheetType.SHARE -> ShareMenuSheet(
                                pages = getTargetPages(),
                                docName = docNameState.value,
                                onDismiss = { currentSheet = SheetType.NONE }
                            )
                            SheetType.SAVE -> SaveMenuSheet(
                                pages = getTargetPages(),
                                docName = docNameState.value,
                                onDismiss = { currentSheet = SheetType.NONE }
                            )
                            SheetType.MENU -> DocMenuBottomSheet(
                                onOptionSelected = { option ->
                                    currentSheet = SheetType.NONE
                                    when (option) {
                                        DocMenuOption.RENAME -> showRenameDialog()
                                        DocMenuOption.DELETE -> showCustomDialog("Delete Document?", "Delete entire document?", "DELETE", android.R.color.holo_red_dark, false, "") { deleteEntireDocument() }
                                        DocMenuOption.BATCH_EDIT -> startBatchEditor()
                                        DocMenuOption.ENCRYPT -> showPasswordDialog()
                                        DocMenuOption.IMPORT_PDF -> importPdfLauncher.launch("application/pdf")
                                        else -> {}
                                    }
                                }
                            )
                            else -> {}
                        }
                    }
                }

                if (showPasswordDialogState.value) {
                    com.swadeshiscanner.app.ui.components.PasswordDialog(
                        onDismiss = { showPasswordDialogState.value = false },
                        onConfirm = { password ->
                            showPasswordDialogState.value = false
                            lifecycleScope.launch(Dispatchers.IO) {
                                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                                prefs.edit().putString("pdf_password_$docId", password).apply()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@DocDetailActivity, "PDF Password Set", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun reorderPages(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in pagesState.indices || toIndex !in pagesState.indices) return
        
        val list = pagesState.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        
        // Update order indices
        val updated = list.mapIndexed { index, page -> page.copy(orderIndex = index) }
        
        // Update UI
        pagesState.clear()
        pagesState.addAll(updated)
        
        // Persist to DB
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            db.updatePages(updated)
            // Update thumbnail if needed (if first page changed)
            val firstPage = updated.firstOrNull()
            if (firstPage != null) {
                db.updateDocMeta(docId, updated.size, firstPage.processedPath ?: firstPage.originalPath)
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext).dao()
            val doc = withContext(Dispatchers.IO) { try { db.getDoc(docId) } catch (e: Exception) { null } }
            if (doc != null) {
                docNameState.value = doc.name
                val isRestricted = doc.name.contains("Passport", true) || doc.name.contains("ID Card", true)
                showAddButtonState.value = !isRestricted
            }

            db.getPages(docId).collect { pages ->
                pagesState.clear()
                pagesState.addAll(pages)
                checkAndStartProcessing(pages)
            }
        }
    }

    private fun checkAndStartProcessing(pages: List<PageEntity>) {
        if (isProcessingAtomic.get()) return
        val pending = pages.filter { it.processedPath.isNullOrEmpty() }
        if (pending.isNotEmpty()) startProcessingQueue(pending)
    }

    private fun startProcessingQueue(pendingPages: List<PageEntity>) {
        processingJob?.cancel()
        processingJob = lifecycleScope.launch(Dispatchers.IO) {
            isProcessingAtomic.set(true)
            isProcessingState.value = true
            val db = AppDatabase.get(applicationContext).dao()
            for (page in pendingPages) {
                if (!isActive) break
                try {
                    val procPath = processPageImage(applicationContext, page)
                    db.updatePage(page.copy(processedPath = procPath))
                    if (page.orderIndex == 0) db.updateDocMeta(docId, -1, procPath)
                    delay(50)
                } catch (e: Exception) {
                    e.printStackTrace()
                    try { db.updatePage(page.copy(processedPath = page.originalPath)) } catch (e2: Exception) {}
                }
            }
            isProcessingAtomic.set(false)
            withContext(Dispatchers.Main) { isProcessingState.value = false; loadData() }
        }
    }

    private fun openSinglePageView(page: PageEntity) {
        val intent = Intent(this, SinglePageActivity::class.java)
        intent.putExtra("page_id", page.id)
        intent.putExtra("doc_name", docNameState.value)
        singlePageLauncher.launch(intent)
    }

    private fun showPasswordDialog() {
        showPasswordDialogState.value = true
    }

    private fun startBatchEditor() {
        val targetPages = getTargetPages()
        if (targetPages.isEmpty()) {
            Toast.makeText(this, "No pages available", Toast.LENGTH_SHORT).show()
            return
        }
        val ids = targetPages.map { it.id }.toLongArray()
        val intent = Intent(this, BatchEditorActivity::class.java)
        intent.putExtra("target_page_ids", ids)
        addPageLauncher.launch(intent)
    }

    private fun showAddPageOptions() {
        AlertDialog.Builder(this).setTitle("Add Page").setItems(arrayOf("Take Photo", "Gallery")) { _, i ->
            if (i == 0) addPageLauncher.launch(Intent(this, CameraActivity::class.java).apply { putExtra("existing_doc_id", docId); putExtra("next_order_index", pagesState.size) })
            else galleryLauncher.launch("image/*")
        }.show()
    }

    private fun processGalleryUri(uri: Uri) {
        isProcessingState.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inp = contentResolver.openInputStream(uri)
                val exif = ExifInterface(inp!!)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                inp.close()
                val rot = when (orientation) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
                val inp2 = contentResolver.openInputStream(uri)
                var bmp = BitmapFactory.decodeStream(inp2)
                inp2?.close()
                if (bmp != null) {
                    if (rot != 0f) {
                        val m = Matrix().apply { postRotate(rot) }
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    val path = ScanRepository.saveImageToSession(this@DocDetailActivity, bmp)
                    tempGalleryImportPath = path
                    val pts = ScanUtils.detectCorners(bmp)
                    CropActivity.temporaryCropCache[path] = CachedPageData(pts, 0f)
                    withContext(Dispatchers.Main) {
                        isProcessingState.value = false
                        addPageLauncher.launch(Intent(this@DocDetailActivity, CropActivity::class.java).apply { putExtra("image_path", path); putExtra("existing_doc_id", docId); putExtra("next_order_index", pagesState.size) })
                    }
                }
            } catch (e: Exception) { e.printStackTrace(); withContext(Dispatchers.Main) { isProcessingState.value = false } }
        }
    }

    private fun getTargetPages() = if (selectedIdsState.isNotEmpty()) pagesState.filter { selectedIdsState.contains(it.id) } else pagesState.toList()

    private fun showRenameDialog() {
        showRenameDialogState.value = RenameDialogData("Rename Document", null, "RENAME", true, docNameState.value) { newName ->
            if (newName.isNotEmpty()) {
                docNameState.value = newName
                lifecycleScope.launch(Dispatchers.IO) { AppDatabase.get(applicationContext).dao().updateDocName(docId, newName) }
            }
        }
    }

    private fun deleteEntireDocument() {
        isProcessingState.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.get(applicationContext).dao()
                val allPages = dao.getPagesList(docId)
                
                allPages.forEach { page ->
                    cleanupPageFiles(page)
                    dao.deletePage(page)
                }
                
                dao.deleteDocById(docId)
                ScanRepository.performAggressiveCleanup(applicationContext)
                
                withContext(Dispatchers.Main) { 
                    isProcessingState.value = false
                    finish() 
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isProcessingState.value = false }
            }
        }
    }

    private fun deleteSelectedPages(pagesToDelete: List<PageEntity>) {
        if (pagesToDelete.size == pagesState.size) { deleteEntireDocument(); return }
        isProcessingState.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(applicationContext).dao()
                pagesToDelete.forEach { page ->
                    cleanupPageFiles(page)
                    db.deletePage(page)
                }
                
                // Re-order remaining pages
                val remaining = db.getPagesList(docId)
                val updated = remaining.mapIndexed { index, page -> page.copy(orderIndex = index) }
                db.updatePages(updated)
                
                val firstPage = updated.firstOrNull()
                db.updateDocMeta(docId, updated.size, firstPage?.processedPath ?: firstPage?.originalPath)

                withContext(Dispatchers.Main) { 
                    isProcessingState.value = false
                    selectedIdsState.clear()
                    loadData() 
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isProcessingState.value = false }
            }
        }
    }

    private fun cleanupPageFiles(page: PageEntity) {
        ScanRepository.cleanupPageFiles(applicationContext, page.id, page.originalPath, page.processedPath)
    }

    private fun showCustomDialog(title: String, message: String?, positiveText: String, colorRes: Int, isInput: Boolean, prefill: String, onPositive: (String) -> Unit) {
        showRenameDialogState.value = RenameDialogData(title, message, positiveText, isInput, prefill, onPositive)
    }

    companion object {
        fun processPageImage(context: Context, page: PageEntity): String {
            val originalFile = File(page.originalPath)
            if (!originalFile.exists()) return page.originalPath
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return page.originalPath
            var bitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: return page.originalPath
            bitmap = applyExifOrientation(originalFile.absolutePath, bitmap)
            val cropData = parseCropData(page.cropData)
            if (cropData.rotation != 0f) {
                val m = Matrix().apply { postRotate(cropData.rotation) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }
            if (cropData.points != null) {
                var pts = cropData.points
                if (pts.values.all { it.x <= 2.0f && it.y <= 2.0f }) {
                    val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
                    pts = pts.mapValues { (_, p) -> android.graphics.PointF(p.x * w, p.y * h) }
                }
                val warped = ScanUtils.warpImage(bitmap, pts)
                if (warped != bitmap && warped != null) { bitmap.recycle(); bitmap = warped }
            }
            val filtered = when (cropData.filterId) {
                2 -> SmartFilterUtils.applyGrayScale(bitmap)
                0 -> bitmap
                else -> SmartFilterUtils.applyMagicColor(bitmap)
            }
            if (bitmap != filtered) bitmap.recycle()
            
            // Apply signature layer if it exists
            val finalBitmap = applySignatureIfPresent(context, page.id, filtered)
            
            val outFile = File(context.filesDir, "Proc_${System.currentTimeMillis()}_${page.orderIndex}.jpg")
            FileOutputStream(outFile).use { out -> finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            
            if (finalBitmap != filtered) finalBitmap.recycle()
            
            return outFile.absolutePath
        }

        private fun applySignatureIfPresent(context: Context, pageId: Long, baseBitmap: Bitmap): Bitmap {
            return try {
                val layerData = com.swadeshiscanner.app.utils.SignatureUtils.getSignatureLayer(context, pageId)
                if (layerData != null) {
                    com.swadeshiscanner.app.utils.SignatureUtils.applySignatureToImage(baseBitmap, layerData.first, null)
                } else {
                    baseBitmap
                }
            } catch (e: Exception) {
                baseBitmap
            }
        }

        private fun applyExifOrientation(path: String, bitmap: Bitmap): Bitmap {
            return try {
                val exif = ExifInterface(path)
                val rot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) { ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0 }
                if (rot != 0) { val m = Matrix().apply { postRotate(rot.toFloat()) }; Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true) } else bitmap
            } catch (e: Exception) { bitmap }
        }

        fun parseCropData(data: String?): ParsedCropData {
            if (data.isNullOrEmpty()) return ParsedCropData(null, 0f, 1)
            val parts = data.split("#")
            val map = mutableMapOf<Int, android.graphics.PointF>()
            if (parts[0].isNotEmpty()) { parts[0].split(";").forEachIndexed { i, s -> val c = s.split(","); if (c.size == 2) map[i] = android.graphics.PointF(c[0].toFloat(), c[1].toFloat()) } }
            return ParsedCropData(if(map.size==4) map else null, if (parts.size > 1) parts[1].toFloatOrNull() ?: 0f else 0f, if (parts.size > 2) parts[2].toIntOrNull() ?: 1 else 1)
        }
        data class ParsedCropData(val points: Map<Int, android.graphics.PointF>?, val rotation: Float, val filterId: Int)
    }
}
