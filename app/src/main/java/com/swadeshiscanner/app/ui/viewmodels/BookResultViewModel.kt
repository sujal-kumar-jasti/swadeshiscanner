package com.swadeshiscanner.app.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookResultViewModel(application: Application) : AndroidViewModel(application) {
    var statusText by mutableStateOf("Initializing...")
    var progress by mutableFloatStateOf(0f)
    var isDone by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var docName by mutableStateOf("")

    fun startBatchSplitting(docId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(getApplication()).dao()
                val spreadPages = db.getPagesList(docId)

                if (spreadPages.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        error = "No pages found"
                    }
                    return@launch
                }

                val newPagesList = ArrayList<PageEntity>()
                var newPageIndex = 0

                spreadPages.forEachIndexed { index, spreadPage ->
                    withContext(Dispatchers.Main) {
                        statusText = "Processing Spread ${index + 1}/${spreadPages.size}..."
                        progress = (index.toFloat() / spreadPages.size)
                    }

                    val tempSpreadPath = DocDetailActivity.processPageImage(getApplication(), spreadPage)
                    val fullBitmap = BitmapFactory.decodeFile(tempSpreadPath)

                    if (fullBitmap != null) {
                        val w = fullBitmap.width
                        val h = fullBitmap.height

                        val leftBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, w / 2, h)
                        val rightBitmap = Bitmap.createBitmap(fullBitmap, w / 2, 0, w / 2, h)

                        val leftFile = File(getApplication<Application>().filesDir, "Book_${System.currentTimeMillis()}_${index}_L.jpg")
                        FileOutputStream(leftFile).use { out -> leftBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }

                        val rightFile = File(getApplication<Application>().filesDir, "Book_${System.currentTimeMillis()}_${index}_R.jpg")
                        FileOutputStream(rightFile).use { out -> rightBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }

                        val leftCropData = "0.0,0.0;${w/2.0},0.0;${w/2.0},${h};0.0,${h}#0.0#1"
                        val rightCropData = "0.0,0.0;${w/2.0},0.0;${w/2.0},${h};0.0,${h}#0.0#1"

                        newPagesList.add(PageEntity(
                            docId = docId,
                            originalPath = leftFile.absolutePath,
                            processedPath = null,
                            orderIndex = newPageIndex++,
                            cropData = leftCropData
                        ))

                        newPagesList.add(PageEntity(
                            docId = docId,
                            originalPath = rightFile.absolutePath,
                            processedPath = null,
                            orderIndex = newPageIndex++,
                            cropData = rightCropData
                        ))

                        fullBitmap.recycle()
                        leftBitmap.recycle()
                        rightBitmap.recycle()

                        if (tempSpreadPath != spreadPage.originalPath) File(tempSpreadPath).delete()
                    }

                    try { File(spreadPage.originalPath).delete() } catch (e: Exception) {}
                    try { if(spreadPage.processedPath != null) File(spreadPage.processedPath).delete() } catch (e: Exception) {}
                    db.deletePage(spreadPage)
                }

                newPagesList.forEach { db.insertPage(it) }

                val newName = "Book Scan ${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}"
                db.updateDocName(docId, newName)

                val thumb = if (newPagesList.isNotEmpty()) newPagesList[0].originalPath else null
                db.updateDocMeta(docId, newPagesList.size, thumb)

                withContext(Dispatchers.Main) {
                    docName = newName
                    isDone = true
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    error = e.message
                }
            }
        }
    }
}
