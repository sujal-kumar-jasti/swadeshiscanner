package com.swadeshiscanner.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.database.PageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DocImportHelper {

    // Shared "Clear Cache" logic
    fun clearImportCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles() ?: return
                for (file in files) {
                    if (file.isFile) {
                        // Clean up temp PDFs, Word docs, and render artifacts
                        if (file.name.startsWith("upload_raw") ||
                            file.name.startsWith("server_converted") ||
                            file.name.startsWith("temp_render") ||
                            file.extension.equals("pdf", true)) {
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Shared "Import Files" logic
    suspend fun performFileImport(
        context: Context,
        scope: CoroutineScope,
        uris: List<Uri>
    ) {
        // We run this on IO Dispatcher
        withContext(Dispatchers.IO) {
            val db = AppDatabase.get(context).dao()

            for (uri in uris) {
                try {
                    // 1. Get Filename & Type
                    val fileName = getFileName(context, uri) ?: "Imported_Doc_${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: ""

                    // 2. Identify Type & Call Server or Local Logic
                    val tempImagePaths = when {
                        // WORD
                        mimeType.contains("word") || fileName.endsWith(".docx") || fileName.endsWith(".doc") -> {
                            ImportUtils.importWordToImages(context, uri)
                        }
                        // EXCEL
                        mimeType.contains("spreadsheet") || mimeType.contains("excel") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls") -> {
                            ImportUtils.importExcelToImages(context, uri)
                        }
                        // PPT
                        mimeType.contains("presentation") || mimeType.contains("powerpoint") || fileName.endsWith(".pptx") || fileName.endsWith(".ppt") -> {
                            ImportUtils.importPptToImages(context, uri)
                        }
                        // PDF
                        mimeType.contains("pdf") || fileName.endsWith(".pdf") -> {
                            ImportUtils.importPdfToImages(context, uri)
                        }
                        else -> emptyList()
                    }

                    // 3. Process the images if import was successful
                    if (tempImagePaths.isNotEmpty()) {

                        // Clean up filename (remove extension)
                        val docName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName

                        // Create Document Entry
                        val newDocId = db.insertDoc(
                            DocumentEntity(name = docName, pageCount = 0, thumbnailPath = "")
                        )

                        var validPagesCount = 0
                        var firstThumbnail: String? = null

                        // 4. Save Images Permanently & Insert Pages
                        tempImagePaths.forEachIndexed { index, tempPath ->
                            val tempFile = File(tempPath)
                            // Create a permanent file in app's internal storage
                            val permFile = File(context.filesDir, "Orig_${System.currentTimeMillis()}_$index.jpg")
                            var finalPath = tempPath

                            try {
                                // We re-save the bitmap to ensure it's a standard JPEG in our filesDir
                                val options = BitmapFactory.Options()
                                val bitmap = BitmapFactory.decodeFile(tempPath, options)

                                if (bitmap != null) {
                                    FileOutputStream(permFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    bitmap.recycle()
                                    // Delete the temp cache file
                                    if (tempFile.exists()) tempFile.delete()
                                    finalPath = permFile.absolutePath
                                } else {
                                    // Fallback: just copy if decode fails
                                    if (tempFile.exists()) {
                                        tempFile.copyTo(permFile, overwrite = true)
                                        tempFile.delete()
                                        finalPath = permFile.absolutePath
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }

                            // Calculate dimensions for Crop Data
                            val boundOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(finalPath, boundOpt)
                            val w = boundOpt.outWidth.toFloat()
                            val h = boundOpt.outHeight.toFloat()

                            if (w > 0 && h > 0) {
                                // Default crop is full image
                                val fullCropString = "0.0,0.0;${w},0.0;${w},${h};0.0,${h}#0.0#0"
                                db.insertPage(
                                    PageEntity(
                                        docId = newDocId,
                                        originalPath = finalPath,
                                        processedPath = null, // No filters applied yet
                                        orderIndex = validPagesCount,
                                        cropData = fullCropString
                                    )
                                )
                                if (firstThumbnail == null) firstThumbnail = finalPath
                                validPagesCount++
                            }
                        }

                        // 5. Update Document Metadata or Delete if failed
                        if (validPagesCount > 0) {
                            db.updateDocMeta(newDocId, validPagesCount, firstThumbnail)
                        } else {
                            db.deleteDoc(DocumentEntity(id = newDocId, name = "", createdTime = 0))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Clear Glide cache so thumbnails update immediately
            withContext(Dispatchers.Main) {
                Glide.get(context).clearMemory()
            }
        }
    }

    // Helper to get real filename from URI
    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}



