package com.swadeshiscanner.app.ui.viewmodels

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadeshiscanner.app.network.ConverterApi
import com.swadeshiscanner.app.network.ProgressRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

data class ConverterUiState(
    val fileName: String = "Selected Document",
    val conversionType: String = "WORD",
    val isLoading: Boolean = false,
    val loadingTitle: String = "Uploading File...",
    val loadingSubtitle: String = "Starting...",
    val progress: Int = 0,
    val isIndeterminate: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val errorMessage: String? = null
)

class ConverterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var inputUri: Uri? = null
    private var cachedConvertedFile: File? = null
    private var tempInputFile: File? = null
    private var targetExtension: String = ".docx"

    fun init(uri: Uri, type: String, contentResolver: ContentResolver) {
        inputUri = uri
        val fileName = getFileName(uri, contentResolver) ?: "Document Ready"
        _uiState.update { it.copy(fileName = fileName, conversionType = type) }
    }

    fun startConversion(context: Context) {
        val uri = inputUri ?: return
        val type = _uiState.value.conversionType

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingTitle = "Uploading File...",
                loadingSubtitle = "Starting...",
                isIndeterminate = false,
                progress = 0
            )
        }

        viewModelScope.launch {
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    createTempFileFromUri(uri, context)
                }

                if (tempFile == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to create temp file") }
                    return@launch
                }

                val progressBody = ProgressRequestBody(tempFile) { bytesWritten, totalBytes ->
                    val progress = (100 * bytesWritten / totalBytes).toInt()
                    val currentMb = String.format("%.1f", bytesWritten / 1024f / 1024f)
                    val totalMb = String.format("%.1f", totalBytes / 1024f / 1024f)

                    if (progress < 100) {
                        _uiState.update { 
                            it.copy(
                                isIndeterminate = false,
                                progress = progress,
                                loadingSubtitle = "$currentMb MB / $totalMb MB"
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                loadingTitle = "Converting...",
                                loadingSubtitle = "Processing on server...",
                                isIndeterminate = true
                            )
                        }
                    }
                }

                val body = MultipartBody.Part.createFormData("file", tempFile.name, progressBody)
                val api = ConverterApi.create()

                val response: Response<ResponseBody> = withContext(Dispatchers.IO) {
                    when (type) {
                        "WORD" -> api.convertPdfToWord(body)
                        "EXCEL" -> api.convertPdfToExcel(body)
                        "PPT" -> api.convertPdfToPpt(body)
                        "WORD_TO_PDF" -> api.convertWordToPdf(body)
                        "EXCEL_TO_PDF" -> api.convertExcelToPdf(body)
                        "PPT_TO_PDF" -> api.convertPptToPdf(body)
                        else -> api.convertPdfToWord(body)
                    }
                }

                if (response.isSuccessful && response.body() != null) {
                    _uiState.update { 
                        it.copy(
                            loadingTitle = "Downloading...",
                            isIndeterminate = false,
                            progress = 100
                        )
                    }
                    downloadToCache(response.body()!!, context)
                    // Cleanup temp input
                    tempFile.delete()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed: ${response.code()}") }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.message}") }
            }
        }
    }

    private suspend fun downloadToCache(body: ResponseBody, context: Context) = withContext(Dispatchers.IO) {
        try {
            val originalName = _uiState.value.fileName
            val nameNoExt = originalName.substringBeforeLast(".")

            targetExtension = when (_uiState.value.conversionType) {
                "WORD" -> ".docx"
                "EXCEL" -> ".xlsx"
                "PPT" -> ".pptx"
                "WORD_TO_PDF", "EXCEL_TO_PDF", "PPT_TO_PDF" -> ".pdf"
                else -> ".docx"
            }

            val fileName = "${nameNoExt}_converted$targetExtension"
            val file = File(context.cacheDir, fileName)

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            cachedConvertedFile = file

            _uiState.update { it.copy(isLoading = false, showSuccessDialog = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Save Error") }
        }
    }

    fun saveToPublicStorage(context: Context) {
        val srcFile = cachedConvertedFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, srcFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(targetExtension))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/SwadeshiScanner")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val uri = resolver.insert(collection, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri).use { output ->
                        srcFile.inputStream().use { input ->
                            input.copyTo(output!!)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    // Success handled in UI via toast or dialog dismissal
                } else {
                    throw Exception("Could not create MediaStore entry")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Failed to save to storage") }
            }
        }
    }

    fun getMimeType(ext: String): String {
        return when (ext) {
            ".pdf" -> "application/pdf"
            ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(showSuccessDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getCachedFile(): File? = cachedConvertedFile

    private fun createTempFileFromUri(uri: Uri, context: Context): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            tempInputFile = File.createTempFile("upload_tmp", null, context.cacheDir)

            inputStream.use { input ->
                FileOutputStream(tempInputFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempInputFile
        } catch (e: Exception) { null }
    }

    private fun getFileName(uri: Uri, contentResolver: ContentResolver): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    fun cleanup(context: Context) {
        try {
            tempInputFile?.let { if (it.exists()) it.delete() }
            cachedConvertedFile?.let { if (it.exists()) it.delete() }
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("upload_tmp") || file.name.contains("_converted")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
