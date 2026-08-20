package com.swadeshiscanner.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.shockwave.pdfium.PdfiumCore
import com.swadeshiscanner.app.ScanRepository
import com.swadeshiscanner.app.network.ConverterApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response // Import this
import okhttp3.ResponseBody // Import this
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

object ImportUtils {

    // --- SERVER BASED IMPORTS (High Quality) ---

    suspend fun importWordToImages(context: Context, uri: Uri): List<String> {
        return convertAndImport(context, uri, "WORD")
    }

    suspend fun importExcelToImages(context: Context, uri: Uri): List<String> {
        return convertAndImport(context, uri, "EXCEL")
    }

    suspend fun importPptToImages(context: Context, uri: Uri): List<String> {
        return convertAndImport(context, uri, "PPT")
    }

    /**
     * Common logic:
     * 1. Upload File -> 2. Server converts to PDF -> 3. Download PDF -> 4. Render Images
     */
    private suspend fun convertAndImport(context: Context, uri: Uri, type: String): List<String> = withContext(Dispatchers.IO) {
        // 1. Copy URI to a temp file so we can upload it
        val inputExt = when(type) {
            "WORD" -> ".docx"
            "EXCEL" -> ".xlsx"
            "PPT" -> ".pptx"
            else -> ".docx"
        }
        val tempInputFile = File(context.cacheDir, "upload_raw_$type$inputExt")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempInputFile).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList<String>()
        }

        // 2. Prepare Network Request
        // application/octet-stream covers all doc types safely
        val requestFile = tempInputFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", tempInputFile.name, requestFile)

        val api = ConverterApi.create()

        try {
            // 3. Call Server Endpoint (Direct Suspend Call)
            val response: Response<ResponseBody> = when (type) {
                "WORD" -> api.convertWordToPdf(body)
                "EXCEL" -> api.convertExcelToPdf(body)
                "PPT" -> api.convertPptToPdf(body)
                else -> api.convertWordToPdf(body)
            }

            // Cleanup input file immediately after upload
            if (tempInputFile.exists()) tempInputFile.delete()

            if (response.isSuccessful && response.body() != null) {
                // 4. Save the Resulting PDF temporarily
                val tempPdf = File(context.cacheDir, "server_converted_${System.currentTimeMillis()}.pdf")

                val inputStream: InputStream = response.body()!!.byteStream()
                val outputStream = FileOutputStream(tempPdf)

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                // 5. Convert that PDF to Images (High Quality Render)
                val images = importPdfToImages(context, Uri.fromFile(tempPdf))

                // Cleanup the downloaded PDF
                if (tempPdf.exists()) tempPdf.delete()

                return@withContext images
            } else {
                Log.e("ImportUtils", "Server Import Failed: ${response.code()}")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Cleanup on error
            if (tempInputFile.exists()) tempInputFile.delete()
            return@withContext emptyList()
        }
    }

    // --- CORE PDF ENGINE (Renders PDF to Images locally) ---
    // This is used by the server logic above, AND by direct PDF imports

    fun importPdfToImages(context: Context, uri: Uri): List<String> {
        val importedPaths = ArrayList<String>()
        val tempFile = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")

        try {
            // Copy URI to local file for Pdfium
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            val pdfiumCore = PdfiumCore(context)
            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfDocument = pdfiumCore.newDocument(fd)
            val pageCount = pdfiumCore.getPageCount(pdfDocument)

            for (i in 0 until pageCount) {
                pdfiumCore.openPage(pdfDocument, i)

                val rawW = pdfiumCore.getPageWidthPoint(pdfDocument, i)
                val rawH = pdfiumCore.getPageHeightPoint(pdfDocument, i)

                // Smart Scaling: Keeps max dimension 2048px for quality + performance
                val scale = calculateDynamicScale(rawW, rawH)
                val width = (rawW * scale).toInt()
                val height = (rawH * scale).toInt()

                try {
                    // RGB_565 saves 50% RAM compared to ARGB_8888, with negligible visual difference for docs
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    pdfiumCore.renderPageBitmap(pdfDocument, bitmap, i, 0, 0, width, height, true)

                    val path = ScanRepository.saveImageToSession(context, bitmap)
                    importedPaths.add(path)

                    bitmap.recycle()
                } catch (oom: OutOfMemoryError) {
                    Log.e("ImportUtils", "OOM on page $i, retrying lower res")
                    try {
                        // Fallback: Use raw size if scaled size crashed
                        val safeBitmap = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.RGB_565)
                        pdfiumCore.renderPageBitmap(pdfDocument, safeBitmap, i, 0, 0, rawW, rawH, true)
                        importedPaths.add(ScanRepository.saveImageToSession(context, safeBitmap))
                        safeBitmap.recycle()
                    } catch (e: Exception) {
                        Log.e("ImportUtils", "Failed to render page $i even on retry")
                    }
                }
            }
            pdfiumCore.closeDocument(pdfDocument)
            fd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
        return importedPaths
    }

    private fun calculateDynamicScale(w: Int, h: Int): Float {
        val maxDimension = 2048f
        val currentMax = w.coerceAtLeast(h).toFloat()

        return if (currentMax > maxDimension) {
            maxDimension / currentMax
        } else {
            // Upscale small docs (like business cards) to be readable, max 3x
            min(maxDimension / currentMax, 3.0f)
        }
    }
}