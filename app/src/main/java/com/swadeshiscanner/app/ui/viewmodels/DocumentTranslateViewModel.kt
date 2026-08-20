package com.swadeshiscanner.app.ui.viewmodels

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.utils.OcrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class TranslateUiState(
    val imagePath: String? = null,
    val originalText: String = "",
    val translatedText: String = "",
    val selectedTabText: String = "",
    val currentTab: Int = 0, // 0: Original, 1: Translation
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val sourceLanguageIndex: Int = 0,
    val targetLanguageIndex: Int = 0,
    val languageNames: List<String> = emptyList(),
    val languageCodes: List<String> = emptyList(),
    val showDownloadDialog: Boolean = false,
    val downloadLangName: String = ""
)

class DocumentTranslateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TranslateUiState())
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    private var currentDocId: Long = -1L
    private var isTempDoc = false

    init {
        setupLanguages()
    }

    private fun setupLanguages() {
        val codes = TranslateLanguage.getAllLanguages()
        val names = codes.map { Locale(it).displayLanguage }
        
        val enIndex = codes.indexOf(TranslateLanguage.ENGLISH).coerceAtLeast(0)
        val hiIndex = codes.indexOf(TranslateLanguage.HINDI).coerceAtLeast(0)
        
        _uiState.update { 
            it.copy(
                languageCodes = codes,
                languageNames = names,
                sourceLanguageIndex = enIndex,
                targetLanguageIndex = hiIndex
            )
        }
    }

    fun init(docId: Long) {
        currentDocId = docId
        processAndExtract(docId)
    }

    private fun processAndExtract(docId: Long) {
        _uiState.update { it.copy(isLoading = true, loadingMessage = "Processing Image...") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(getApplication()).dao()
                val doc = db.getDoc(docId)

                if (doc != null && doc.name.startsWith("TEMP")) {
                    isTempDoc = true
                }

                val pages = db.getPagesList(docId)
                if (pages.isEmpty()) throw Exception("Page not found")
                val page = pages[0]

                var finalPath = page.processedPath
                val fileExists = if (!finalPath.isNullOrEmpty()) File(finalPath).exists() else false

                if (!fileExists) {
                    finalPath = DocDetailActivity.processPageImage(getApplication(), page)
                    if (finalPath != null) {
                        db.updatePage(page.copy(processedPath = finalPath))
                    }
                }

                if (finalPath == null || !File(finalPath).exists()) {
                    finalPath = page.originalPath
                }

                val ocrText = OcrUtils.extractText(getApplication(), finalPath!!)

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            imagePath = finalPath,
                            originalText = ocrText,
                            selectedTabText = ocrText,
                            currentTab = 0
                        )
                    }
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

    fun onTabSelected(index: Int) {
        _uiState.update { 
            it.copy(
                currentTab = index,
                selectedTabText = if (index == 0) it.originalText else it.translatedText
            )
        }
    }

    fun onSourceLangChanged(index: Int) {
        _uiState.update { it.copy(sourceLanguageIndex = index) }
    }

    fun onTargetLangChanged(index: Int) {
        _uiState.update { it.copy(targetLanguageIndex = index) }
    }

    fun onTextChanged(text: String) {
        _uiState.update { 
            if (it.currentTab == 0) {
                it.copy(originalText = text, selectedTabText = text)
            } else {
                it.copy(translatedText = text, selectedTabText = text)
            }
        }
    }

    fun checkAndTranslate() {
        val state = _uiState.value
        if (state.originalText.isEmpty()) {
            Toast.makeText(getApplication(), "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val targetCode = state.languageCodes[state.targetLanguageIndex]
        val targetName = state.languageNames[state.targetLanguageIndex]

        val modelManager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(targetCode).build()

        _uiState.update { it.copy(isLoading = true, loadingMessage = "Checking languages...") }

        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    performTranslation()
                } else {
                    _uiState.update { it.copy(isLoading = false, showDownloadDialog = true, downloadLangName = targetName) }
                }
            }
            .addOnFailureListener {
                // If check fails, just try to download anyway
                _uiState.update { it.copy(isLoading = false) }
                performTranslation()
            }
    }

    fun performTranslation() {
        val state = _uiState.value
        val targetCode = state.languageCodes[state.targetLanguageIndex]

        _uiState.update { it.copy(showDownloadDialog = false, isLoading = true, loadingMessage = "Preparing translation...") }
        
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(state.languageCodes[state.sourceLanguageIndex])
            .setTargetLanguage(targetCode)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build() 

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                _uiState.update { it.copy(loadingMessage = "Translating text...") }
                translator.translate(state.originalText)
                    .addOnSuccessListener { result ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                translatedText = result,
                                selectedTabText = result,
                                currentTab = 1
                            )
                        }
                        translator.close()
                    }
                    .addOnFailureListener { e ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                translatedText = "Translation Failed: ${e.localizedMessage}",
                                selectedTabText = "Translation Failed: ${e.localizedMessage}",
                                currentTab = 1
                            )
                        }
                        translator.close()
                        Toast.makeText(getApplication(), "Translation Error", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                _uiState.update { it.copy(isLoading = false) }
                translator.close()
                Toast.makeText(getApplication(), "Language Download Failed. Check connection.", Toast.LENGTH_LONG).show()
            }
    }

    fun dismissDownloadDialog() {
        _uiState.update { it.copy(showDownloadDialog = false) }
    }

    fun copyToClipboard() {
        val text = _uiState.value.selectedTabText
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "Copied", Toast.LENGTH_SHORT).show()
    }

    fun shareText() {
        val text = _uiState.value.selectedTabText
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                
                // Clear specific cache files
                getApplication<Application>().cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("cam_temp") || file.name.startsWith("upload_tmp")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
