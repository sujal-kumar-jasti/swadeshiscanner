package com.swadeshiscanner.app.utils

import android.content.Context
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object OcrUtils {
    suspend fun extractText(context: Context, imagePath: String): String {
        return withContext(Dispatchers.IO) {
            val tesseract = TessBaseAPI()
            var bitmap: android.graphics.Bitmap? = null

            try {
                // 1. Setup Tesseract Data Folder
                val dataPath = File(context.filesDir, "tesseract")
                if (!dataPath.exists()) dataPath.mkdirs()

                val tessDataFolder = File(dataPath, "tessdata")
                if (!tessDataFolder.exists()) tessDataFolder.mkdirs()

                // 2. Ensure eng.traineddata exists
                val trainedDataFile = File(tessDataFolder, "eng.traineddata")
                if (!trainedDataFile.exists()) {
                    try {
                        context.assets.open("tessdata/eng.traineddata").use { inputStream ->
                            FileOutputStream(trainedDataFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@withContext "Error: eng.traineddata not found in assets."
                    }
                }

                // 3. Initialize Tesseract
                val initSuccess = tesseract.init(dataPath.absolutePath, "eng")
                if (!initSuccess) {
                    return@withContext "Error: Tesseract initialization failed."
                }

                // 4. Load Image
                bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap == null) {
                    return@withContext "Error: Could not load image."
                }

                // 5. Recognize Text
                // For general text, we use the default Page Segmentation Mode (PSM_AUTO)
                // and do NOT set a whitelist (so it reads all words/sentences).
                tesseract.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tesseract.setImage(bitmap)

                val result = tesseract.utF8Text

                if (result.isBlank()) "No text detected." else result

            } catch (e: Exception) {
                e.printStackTrace()
                "Error extracting text: ${e.message}"
            } finally {
                // 6. Cleanup to prevent memory leaks
                bitmap?.recycle()
                tesseract.stop()
                tesseract.recycle()
            }
        }
    }
}