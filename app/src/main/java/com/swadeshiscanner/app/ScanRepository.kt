package com.swadeshiscanner.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ScanRepository {
    private const val SESSION_DIR = "ActiveSession"

    // --- SESSION FILE MANAGEMENT ---

    private fun getSessionDir(context: Context): File {
        val dir = File(context.cacheDir, SESSION_DIR) // Use cacheDir so OS can clean it if needed
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Saves a bitmap (from Gallery) to the session folder.
     */
    fun saveImageToSession(context: Context, bitmap: Bitmap): String {
        val dir = getSessionDir(context)
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val file = File(dir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file.absolutePath
    }

    /**
     * Copies a temp file (from CameraX) to the session folder.
     */
    fun copyFileToSession(context: Context, sourceFile: File): String {
        val dir = getSessionDir(context)
        val destFile = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        sourceFile.copyTo(destFile, overwrite = true)
        return destFile.absolutePath
    }

    /**
     * Clears all session images. Call this when finishing the flow (saving to DB) or exiting camera.
     */
    fun clearSession(context: Context) {
        val dir = File(context.cacheDir, SESSION_DIR)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun cleanupPageFiles(context: Context, pageId: Long, originalPath: String, processedPath: String?) {
        try {
            File(originalPath).let { if (it.exists()) it.delete() }
            processedPath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            // Cleanup signature layers
            File(context.filesDir, "sig_layer_$pageId.png").let { if (it.exists()) it.delete() }
            File(context.filesDir, "sig_meta_$pageId.txt").let { if (it.exists()) it.delete() }
            File(context.cacheDir, "sig_layer_$pageId.png").let { if (it.exists()) it.delete() }
            File(context.cacheDir, "sig_meta_$pageId.txt").let { if (it.exists()) it.delete() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun performAggressiveCleanup(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val dao = com.swadeshiscanner.app.database.AppDatabase.get(context).dao()
                
                // 1. Collect all valid paths from DB
                val validPaths = mutableSetOf<String>()
                validPaths.addAll(dao.getAllOriginalPaths())
                validPaths.addAll(dao.getAllProcessedPaths().filterNotNull())
                validPaths.addAll(dao.getAllThumbnailPaths().filterNotNull())
                validPaths.addAll(dao.getAllSignaturePaths())
                
                // 2. Scan filesDir
                val filesDir = context.filesDir
                filesDir.listFiles()?.forEach { file ->
                    val name = file.name
                    // Only target our app's specific generated files
                    if (name.startsWith("Orig_") || 
                        name.startsWith("Proc_") || 
                        name.startsWith("Merged_") || 
                        name.startsWith("sig_") || 
                        name.startsWith("IDCard_") || 
                        name.startsWith("Passport_")) {
                        
                        if (!validPaths.contains(file.absolutePath)) {
                            file.delete()
                        }
                    }
                }
                
                // 3. Clear cache fully (safely)
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- STATE PERSISTENCE (JSON) ---

}