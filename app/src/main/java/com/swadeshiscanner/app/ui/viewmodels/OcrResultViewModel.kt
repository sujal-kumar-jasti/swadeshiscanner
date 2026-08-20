package com.swadeshiscanner.app.ui.viewmodels

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.utils.ExportUtils
import com.swadeshiscanner.app.utils.OcrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OcrUiState(
    val imagePath: String? = null,
    val extractedText: String = "",
    val isLoading: Boolean = false
)

class OcrResultViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private var currentDocId: Long = -1L
    private var isTempDoc = false

    fun init(docId: Long) {
        currentDocId = docId
        processAndExtract(docId)
    }

    private fun processAndExtract(docId: Long) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(getApplication()).dao()
                val doc = db.getDoc(docId)
                if (doc != null && (doc.name.startsWith("TEMP_OCR") || doc.name.startsWith("TEMP_ID"))) {
                    isTempDoc = true
                }

                val pages = db.getPagesList(docId)
                if (pages.isEmpty()) throw Exception("Page not found")
                val page = pages[0]

                var finalPath = page.processedPath
                if (finalPath.isNullOrEmpty() || !File(finalPath).exists()) {
                    finalPath = DocDetailActivity.processPageImage(getApplication(), page)
                    db.updatePage(page.copy(processedPath = finalPath))
                }

                val extractedText = OcrUtils.extractText(getApplication(), finalPath!!)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, imagePath = finalPath, extractedText = extractedText) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                    Toast.makeText(getApplication(), "Processing Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onTextChanged(text: String) {
        _uiState.update { it.copy(extractedText = text) }
    }

    fun copyToClipboard() {
        ExportUtils.copyToClipboard(getApplication(), _uiState.value.extractedText)
    }

    fun exportToWord() {
        val text = _uiState.value.extractedText
        if (text.isBlank()) return
        
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val file = ExportUtils.generateWordFromText(getApplication(), text)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false) }
                if (file != null) ExportUtils.shareFile(getApplication(), file, ExportUtils.MimeType.WORD.mime)
            }
        }
    }

    fun exportToPdf() {
        val text = _uiState.value.extractedText
        if (text.isBlank()) return
        
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ExportUtils.exportTextToPdf(getApplication(), text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun cleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isTempDoc && currentDocId != -1L) {
                    val db = AppDatabase.get(getApplication()).dao()
                    val pages = db.getPagesList(currentDocId)
                    pages.forEach { page ->
                        try { if (!page.originalPath.isNullOrEmpty()) File(page.originalPath).delete() } catch (e: Exception){}
                        try { if (!page.processedPath.isNullOrEmpty()) File(page.processedPath).delete() } catch (e: Exception){}
                    }
                    db.deleteDocById(currentDocId)
                }
                
                // Only clear SPECIFIC temp files, not entire cacheDir
                getApplication<Application>().cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("cam_temp") || file.name.startsWith("upload_tmp")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
