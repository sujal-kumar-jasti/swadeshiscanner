package com.swadeshiscanner.app.ui.viewmodels

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.googlecode.tesseract.android.TessBaseAPI
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class FormulaUiState(
    val imagePath: String? = null,
    val equation: String = "",
    val formula: String = "",
    val solution: String = "...",
    val solutionColor: Color = Color.Gray,
    val isLoading: Boolean = false
)

class FormulaResultViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FormulaUiState())
    val uiState: StateFlow<FormulaUiState> = _uiState.asStateFlow()

    private lateinit var tesseract: TessBaseAPI
    private val isTessInit = AtomicBoolean(false)
    private var currentDocId: Long = -1L
    private var isTempDoc = false

    fun init(docId: Long) {
        currentDocId = docId
        viewModelScope.launch(Dispatchers.IO) {
            val initSuccess = initTesseract()
            if (initSuccess) {
                processAndSolve(docId)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to init Tesseract", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initTesseract(): Boolean {
        return try {
            tesseract = TessBaseAPI()
            val dataPath = File(getApplication<Application>().filesDir, "tesseract")
            if (!dataPath.exists()) dataPath.mkdirs()

            val tessDataFolder = File(dataPath, "tessdata")
            if (!tessDataFolder.exists()) tessDataFolder.mkdirs()

            val outFile = File(tessDataFolder, "eng.traineddata")
            if (!outFile.exists()) {
                getApplication<Application>().assets.open("tessdata/eng.traineddata").use { inputStream ->
                    FileOutputStream(outFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            val success = tesseract.init(dataPath.absolutePath, "eng")
            if (success) {
                isTessInit.set(true)
                // IMPROVED: whitelist for math symbols to reduce '243' style errors
                tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789+-*/=().xyzasqrt^")
                tesseract.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun processAndSolve(docId: Long) {
        _uiState.update { it.copy(isLoading = true, equation = "Processing...") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(getApplication()).dao()
                val doc = db.getDoc(docId)
                if (doc != null && doc.name.startsWith("TEMP")) isTempDoc = true

                val pages = db.getPagesList(docId)
                if (pages.isEmpty()) throw Exception("Page not found")
                val page = pages[0]

                var finalPath = page.processedPath
                if (finalPath.isNullOrEmpty() || !File(finalPath).exists()) {
                    finalPath = DocDetailActivity.processPageImage(getApplication(), page)
                    db.updatePage(page.copy(processedPath = finalPath))
                }
                if (finalPath == null) throw Exception("Failed to process image")

                val bitmap = BitmapFactory.decodeFile(finalPath)
                if (bitmap != null) {
                    tesseract.setImage(bitmap)
                    val rawResult = tesseract.utF8Text
                    
                    // Reverted: No more guessing operators like 4 -> +
                    val cleanedMath = basicOcrClean(rawResult)
                    
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isLoading = false, equation = cleanedMath, imagePath = finalPath) }
                        solveMath(cleanedMath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, equation = "Error") }
                    Toast.makeText(getApplication(), "Processing Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun basicOcrClean(raw: String): String {
        return raw.replace("\n", " ").trim()
    }

    fun onEquationChanged(newEquation: String) {
        _uiState.update { it.copy(equation = newEquation) }
        solveMath(newEquation)
    }

    fun insertTextAtCursor(text: String, selectionStart: Int, selectionEnd: Int): Int {
        val current = _uiState.value.equation
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        
        val newText = StringBuilder(current)
            .replace(start.coerceAtMost(end), start.coerceAtLeast(end), text)
            .toString()
            
        _uiState.update { it.copy(equation = newText) }
        solveMath(newText)
        return start + text.length
    }

    fun backspace(selectionStart: Int, selectionEnd: Int): Int {
        val current = _uiState.value.equation
        if (current.isEmpty()) return 0
        
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        
        return if (start != end) {
            val newText = StringBuilder(current).delete(start.coerceAtMost(end), start.coerceAtLeast(end)).toString()
            _uiState.update { it.copy(equation = newText) }
            solveMath(newText)
            start.coerceAtMost(end)
        } else if (start > 0) {
            val newText = StringBuilder(current).deleteAt(start - 1).toString()
            _uiState.update { it.copy(equation = newText) }
            solveMath(newText)
            start - 1
        } else {
            0
        }
    }

    fun clearEquation() {
        _uiState.update { it.copy(equation = "", formula = "", solution = "...", solutionColor = Color.Gray) }
    }

    fun solveMath(equation: String) {
        if (equation.isBlank()) {
            _uiState.update { it.copy(formula = "", solution = "...", solutionColor = Color.Gray) }
            return
        }

        var calcString = equation.trim().replace("=", "").replace("÷", "/").replace("×", "*")
        calcString = calcString.replace("√", "sqrt").replace("V", "sqrt").replace("v", "sqrt")
        calcString = calcString.replace(Regex("(\\d)(\\()"), "$1*$2")
        calcString = calcString.replace(Regex("(\\))(\\d)"), "$1*$2")
        calcString = calcString.replace(Regex("(\\))(\\()"), "$1*$2")

        var latex = calcString.replace("*", " \\times ").replace("/", " \\div ").replace("sqrt", "\\sqrt").replace("pi", "\\pi")
        if (latex.contains("\\sqrt") && !latex.contains("\\sqrt{")) {
            latex = latex.replace(Regex("\\\\sqrt([0-9]+)"), "\\\\sqrt{$1}")
        }
        
        _uiState.update { it.copy(formula = latex) }

        try {
            val sqrtFunc = object : net.objecthunter.exp4j.function.Function("sqrt", 1) {
                override fun apply(vararg args: Double): Double = Math.sqrt(args[0])
            }
            val cbrtFunc = object : net.objecthunter.exp4j.function.Function("cbrt", 1) {
                override fun apply(vararg args: Double): Double = Math.cbrt(args[0])
            }
            val logFunc = object : net.objecthunter.exp4j.function.Function("log", 1) {
                override fun apply(vararg args: Double): Double = Math.log10(args[0])
            }
            val lnFunc = object : net.objecthunter.exp4j.function.Function("ln", 1) {
                override fun apply(vararg args: Double): Double = Math.log(args[0])
            }

            val expression = ExpressionBuilder(calcString.lowercase())
                .functions(sqrtFunc, cbrtFunc, logFunc, lnFunc)
                .build()

            val result = expression.evaluate()
            val sol = if (result == result.toLong().toDouble()) "= ${result.toLong()}" else String.format("= %.4f", result)
            
            _uiState.update { it.copy(solution = sol, solutionColor = Color(0xFF2E7D32)) }
        } catch (e: Exception) {
            _uiState.update { it.copy(solution = "?", solutionColor = Color.Gray) }
        }
    }

    fun copyToClipboard() {
        val text = _uiState.value.equation
        if (text.isNotEmpty()) {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Math Equation", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(getApplication(), "Copied!", Toast.LENGTH_SHORT).show()
        }
    }

    fun cleanup() {
        try {
            if (isTessInit.get()) {
                tesseract.stop()
                tesseract.recycle()
            }
        } catch (e: Exception) { e.printStackTrace() }

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
