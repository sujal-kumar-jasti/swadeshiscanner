package com.swadeshiscanner.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.components.DocumentSelectDialog
import com.swadeshiscanner.app.ui.screens.BatchFilterBottomSheet
import com.swadeshiscanner.app.ui.screens.DocBatchEditorScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.utils.SignatureUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class BatchEditorActivity : AppCompatActivity() {
    private var pageIds: LongArray? = null
    private val workingPages = mutableStateListOf<PageEntity>()
    private val isProcessing = mutableStateOf(false)
    private val showFilterSheet = mutableStateOf(false)
    private val showMoveCopyDialog = mutableStateOf<Boolean?>(null) // null: hidden, false: move, true: copy
    private val availableDocs = mutableStateListOf<DocumentEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pageIds = intent.getLongArrayExtra("target_page_ids")
        if (pageIds == null || pageIds!!.isEmpty()) {
            Toast.makeText(this, "No pages selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadData()

        setContent {
            SwadeshiScannerTheme {
                DocBatchEditorScreen(
                    pages = workingPages,
                    isProcessing = isProcessing.value,
                    onBack = { finish() },
                    onDone = { finish() },
                    onRotateLeft = { performBatchRotate(-90f) },
                    onRotateRight = { performBatchRotate(90f) },
                    onMove = { prepareMoveCopy(false) },
                    onCopy = { prepareMoveCopy(true) },
                    onFilterClick = { showFilterSheet.value = true }
                )

                if (showFilterSheet.value) {
                    BatchFilterBottomSheet(
                        onFilterSelected = { filterId ->
                            showFilterSheet.value = false
                            performBatchFilter(filterId)
                        },
                        onDismiss = { showFilterSheet.value = false }
                    )
                }

                showMoveCopyDialog.value?.let { isCopy ->
                    DocumentSelectDialog(
                        documents = availableDocs,
                        onDismiss = { showMoveCopyDialog.value = null },
                        onDocumentSelected = { doc ->
                            showMoveCopyDialog.value = null
                            if (isCopy) performCopy(doc.id) else performMove(doc.id)
                        }
                    )
                }
            }
        }
    }

    private fun loadData() {
        isProcessing.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            val loaded = ArrayList<PageEntity>()
            pageIds?.forEach { id ->
                val page = db.getPageById(id)
                if (page.id != 0L) loaded.add(page)
            }

            withContext(Dispatchers.Main) {
                workingPages.clear()
                workingPages.addAll(loaded)
                isProcessing.value = false
            }
        }
    }

    private fun performBatchFilter(filterId: Int) {
        isProcessing.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            val updatedList = mutableListOf<PageEntity>()

            for (page in workingPages) {
                try {
                    val oldData = page.cropData ?: ""
                    val parts = oldData.split("#")
                    val pointsStr = if (parts.isNotEmpty()) parts[0] else ""
                    val rot = if (parts.size > 1) parts[1] else "0"
                    val newCropData = "$pointsStr#$rot#$filterId"

                    val tempPage = page.copy(cropData = newCropData)
                    val newProcessedPath = DocDetailActivity.processPageImage(applicationContext, tempPage)

                    reapplySignatureToPage(page.id, newProcessedPath)

                    val newPage = page.copy(processedPath = newProcessedPath, cropData = newCropData)
                    db.updatePage(newPage)
                    updatedList.add(newPage)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                workingPages.clear()
                workingPages.addAll(updatedList)
                isProcessing.value = false
                Toast.makeText(this@BatchEditorActivity, "Filter Applied", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            }
        }
    }

    private fun performBatchRotate(angleToAdd: Float) {
        isProcessing.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            val updatedList = mutableListOf<PageEntity>()

            for (page in workingPages) {
                var originalBmp: Bitmap? = null
                var rotatedOriginal: Bitmap? = null

                try {
                    val steps = (angleToAdd / 90f).roundToInt()
                    if (steps != 0) {
                        repeat(Math.abs(steps)) {
                            SignatureUtils.rotateLayerFile(applicationContext, page.id)
                        }
                    }

                    val parsed = DocDetailActivity.parseCropData(page.cropData)
                    val currentRot = parsed.rotation
                    val filterId = parsed.filterId
                    var currentPoints = parsed.points

                    val newRot = (currentRot + angleToAdd) % 360f
                    var newPointsStr = ""

                    if (currentPoints != null) {
                        originalBmp = BitmapFactory.decodeFile(page.originalPath)
                        if (originalBmp != null) {
                            rotatedOriginal = originalBmp
                            if (currentRot != 0f) {
                                val m = Matrix().apply { postRotate(currentRot) }
                                rotatedOriginal = Bitmap.createBitmap(originalBmp, 0, 0, originalBmp.width, originalBmp.height, m, true)
                            }

                            val oldW = rotatedOriginal.width.toFloat()
                            val oldH = rotatedOriginal.height.toFloat()
                            var newPoints = mutableMapOf<Int, PointF>()

                            if (angleToAdd == 90f || angleToAdd == -270f) {
                                currentPoints.forEach { (k, p) ->
                                    val newP = PointF(oldH - p.y, p.x)
                                    val newIndex = (k + 1) % 4
                                    newPoints[newIndex] = newP
                                }
                            } else if (angleToAdd == -90f || angleToAdd == 270f) {
                                currentPoints.forEach { (k, p) ->
                                    val newP = PointF(p.y, oldW - p.x)
                                    val newIndex = (k - 1 + 4) % 4
                                    newPoints[newIndex] = newP
                                }
                            } else {
                                newPoints = currentPoints.toMutableMap()
                            }

                            newPointsStr = buildString {
                                for(i in 0..3) {
                                    val p = newPoints[i] ?: PointF(0f,0f)
                                    append("${p.x},${p.y}")
                                    if(i<3) append(";")
                                }
                            }
                        }
                    }

                    val newCropData = "$newPointsStr#$newRot#$filterId"

                    val tempPage = page.copy(cropData = newCropData)
                    val newProcessedPath = DocDetailActivity.processPageImage(applicationContext, tempPage)

                    reapplySignatureToPage(page.id, newProcessedPath)

                    val updatedPage = page.copy(cropData = newCropData, processedPath = newProcessedPath)
                    db.updatePage(updatedPage)
                    updatedList.add(updatedPage)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (rotatedOriginal != originalBmp && rotatedOriginal != null) rotatedOriginal.recycle()
                    if (originalBmp != null) originalBmp.recycle()
                }
            }

            withContext(Dispatchers.Main) {
                workingPages.clear()
                workingPages.addAll(updatedList)
                isProcessing.value = false
                Toast.makeText(this@BatchEditorActivity, "Rotation Applied", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            }
        }
    }

    private fun reapplySignatureToPage(pageId: Long, imagePath: String) {
        val layerData = SignatureUtils.getSignatureLayer(applicationContext, pageId) ?: return

        try {
            val baseBmp = BitmapFactory.decodeFile(imagePath) ?: return

            val mergedBmp = SignatureUtils.applySignatureToImage(
                baseBmp,
                layerData.first,
                layerData.second
            )

            FileOutputStream(File(imagePath)).use { out ->
                mergedBmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            if (baseBmp != mergedBmp) baseBmp.recycle()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun prepareMoveCopy(isCopy: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            val allDocs = db.getAllDocs().first()

            val currentPagesDocId = workingPages.firstOrNull()?.docId ?: -1L
            val targetDocs = allDocs.filter { it.id != currentPagesDocId && it.id != -1L }

            withContext(Dispatchers.Main) {
                if (targetDocs.isEmpty()) {
                    Toast.makeText(this@BatchEditorActivity, "No other documents available.", Toast.LENGTH_SHORT).show()
                } else {
                    availableDocs.clear()
                    availableDocs.addAll(targetDocs)
                    showMoveCopyDialog.value = isCopy
                }
            }
        }
    }

    private fun performMove(targetDocId: Long) {
        isProcessing.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            val sourceDocId = workingPages.firstOrNull()?.docId ?: return@launch

            val targetDocPages = db.getPagesList(targetDocId)
            var nextIndex = targetDocPages.size

            workingPages.forEachIndexed { i, page ->
                db.updatePage(page.copy(docId = targetDocId, orderIndex = nextIndex + i))
            }

            val remainingSourcePages = db.getPagesList(sourceDocId)
            if (remainingSourcePages.isEmpty()) {
                db.deleteDoc(DocumentEntity(id = sourceDocId, name = "", createdTime = 0))
            } else {
                val sourceThumbnail = remainingSourcePages.firstOrNull()?.processedPath
                db.updateDocMeta(sourceDocId, remainingSourcePages.size, sourceThumbnail)
            }

            val allTargetPages = db.getPagesList(targetDocId)
            val targetThumbnail = allTargetPages.firstOrNull()?.processedPath
            db.updateDocMeta(targetDocId, allTargetPages.size, targetThumbnail)

            withContext(Dispatchers.Main) {
                isProcessing.value = false
                Toast.makeText(this@BatchEditorActivity, "Pages Moved", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun performCopy(targetDocId: Long) {
        isProcessing.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            val existingPages = db.getPagesList(targetDocId)
            var nextIndex = existingPages.size

            workingPages.forEachIndexed { i, page ->
                val newOrig = copyFile(page.originalPath)
                val newProc = if (page.processedPath != null) copyFile(page.processedPath!!) else null

                val newPageId = db.insertPage(page.copy(
                    id = 0,
                    docId = targetDocId,
                    originalPath = newOrig,
                    processedPath = newProc,
                    orderIndex = nextIndex + i,
                    notes = page.notes
                ))

                copySignatureLayer(page.id, newPageId)
            }

            val allTargetPages = db.getPagesList(targetDocId)
            val targetThumbnail = allTargetPages.firstOrNull()?.processedPath
            db.updateDocMeta(targetDocId, allTargetPages.size, targetThumbnail)

            withContext(Dispatchers.Main) {
                isProcessing.value = false
                Toast.makeText(this@BatchEditorActivity, "Pages Copied", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun copyFile(path: String): String {
        return try {
            val source = File(path)
            if (!source.exists()) return path
            val dest = File(filesDir, "Copy_${System.currentTimeMillis()}_${source.name}")
            source.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            path
        }
    }

    private fun copySignatureLayer(oldPageId: Long, newPageId: Long) {
        val oldSig = File(filesDir, "sig_layer_$oldPageId.png")
        val oldMeta = File(filesDir, "sig_meta_$oldPageId.txt")

        if (oldSig.exists()) {
            val newSig = File(filesDir, "sig_layer_$newPageId.png")
            oldSig.copyTo(newSig, overwrite = true)
        }
        if (oldMeta.exists()) {
            val newMeta = File(filesDir, "sig_meta_$newPageId.txt")
            oldMeta.copyTo(newMeta, overwrite = true)
        }
    }
}
