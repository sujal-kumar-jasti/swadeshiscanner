package com.swadeshiscanner.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.ui.components.LoadingDialog
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.screens.IdCardScreen
import com.swadeshiscanner.app.ui.screens.IdCardState
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class IdCardActivity : ComponentActivity() {

    private var frontPath by mutableStateOf<String?>(null)
    private var backPath by mutableStateOf<String?>(null)
    private var screenState by mutableStateOf(IdCardState.SUMMARY)
    private var currentSide = 0

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var loadingMessage by mutableStateOf<String?>(null)

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val docId = result.data?.getLongExtra("saved_doc_id", -1L) ?: -1L
            if (docId != -1L) {
                processFromTempDoc(docId)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) launchCropForId(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        startCamera()

        setContent {
            SwadeshiScannerTheme {
                IdCardScreen(
                    state = screenState,
                    frontPath = frontPath,
                    backPath = backPath,
                    onBack = { 
                        if (screenState == IdCardState.CAMERA) screenState = IdCardState.SUMMARY 
                        else finish() 
                    },
                    onCardClick = { 
                        currentSide = it
                        showSourceDialog(it) 
                    },
                    onDeleteFront = { frontPath = null },
                    onDeleteBack = { backPath = null },
                    onGenerate = {
                        if (frontPath != null && backPath != null) generateAndSaveIdCard()
                        else Toast.makeText(this, "Scan both sides", Toast.LENGTH_SHORT).show()
                    },
                    onCapture = { takePhoto() },
                    onPreviewViewCreated = { setupCameraWithPreview(it) }
                )

                loadingMessage?.let {
                    LoadingDialog(message = it)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupCameraWithPreview(previewView: androidx.camera.view.PreviewView) {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build()
        preview.surfaceProvider = previewView.surfaceProvider

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val tempFile = File.createTempFile("id_temp", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(this@IdCardActivity, "Capture failed", Toast.LENGTH_SHORT).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                lifecycleScope.launch(Dispatchers.Main) {
                    screenState = IdCardState.SUMMARY
                    launchCropForId(Uri.fromFile(tempFile).toString())
                }
            }
        })
    }

    private fun showSourceDialog(side: Int) {
        currentSide = side
        val sideName = if (side == 0) "Front" else "Back"
        AlertDialog.Builder(this).setTitle(sideName)
            .setItems(arrayOf("Camera", "Gallery")) { _, w ->
                if (w == 0) screenState = IdCardState.CAMERA else galleryLauncher.launch("image/*")
            }.show()
    }

    private fun showLoading(message: String) {
        loadingMessage = message
    }

    private fun hideLoading() {
        loadingMessage = null
    }

    private fun cleanupTempFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                frontPath?.let { File(it).delete() }
                backPath?.let { File(it).delete() }
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("id_preview")) file.delete()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun launchCropForId(pathOrUri: String) {
        val intent = Intent(this, CropActivity::class.java)
        intent.putStringArrayListExtra("batch_images", arrayListOf(pathOrUri))
        intent.putExtra("is_id_card_mode", true)
        // Ensure pathOrUri is handled correctly by CropActivity if it's a content URI
        cropLauncher.launch(intent)
    }

    private fun processFromTempDoc(docId: Long) {
        showLoading("Processing Image...")
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext).dao()
            var tempProcessedPath: String? = null
            try {
                val pages = db.getPagesList(docId)
                if (pages.isNotEmpty()) {
                    val page = pages[0]
                    // Force re-process to apply current side logic
                    tempProcessedPath = DocDetailActivity.processPageImage(applicationContext, page)
                    
                    if (tempProcessedPath != null && File(tempProcessedPath).exists()) {
                        val sideName = if (currentSide == 0) "front" else "back"
                        val previewFile = File(cacheDir, "id_preview_${sideName}_${System.currentTimeMillis()}.jpg")
                        File(tempProcessedPath).copyTo(previewFile, overwrite = true)
                        
                        withContext(Dispatchers.Main) {
                            if (currentSide == 0) frontPath = previewFile.absolutePath
                            else backPath = previewFile.absolutePath
                            hideLoading()
                        }
                    } else {
                        throw Exception("Processed file not found")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@IdCardActivity, "Error loading image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@IdCardActivity, "Processing Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Cleanup temp doc
                try {
                    val pages = db.getPagesList(docId)
                    pages.forEach { 
                        ScanRepository.cleanupPageFiles(applicationContext, it.id, it.originalPath, it.processedPath)
                        db.deletePage(it)
                    }
                    db.deleteDocById(docId)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun generateAndSaveIdCard() {
        showLoading("Generating ID Card...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val front = BitmapFactory.decodeFile(frontPath)
                val back = BitmapFactory.decodeFile(backPath)
                if (front == null || back == null) throw Exception("Images missing")

                val targetW = 1000
                val padding = 60
                val gap = 100
                val frontH = (front.height * targetW) / front.width
                val backH = (back.height * targetW) / back.width
                val totalH = padding + frontH + gap + backH + padding

                val result = Bitmap.createBitmap(targetW + (2 * padding), totalH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawColor(Color.WHITE)

                val fScaled = Bitmap.createScaledBitmap(front, targetW, frontH, true)
                val bScaled = Bitmap.createScaledBitmap(back, targetW, backH, true)

                canvas.drawBitmap(fScaled, padding.toFloat(), padding.toFloat(), null)
                canvas.drawBitmap(bScaled, padding.toFloat(), (padding + frontH + gap).toFloat(), null)

                fScaled.recycle(); bScaled.recycle(); front.recycle(); back.recycle()

                val file = File(filesDir, "IDCard_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out -> result.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                result.recycle()

                val db = AppDatabase.get(applicationContext)
                val name = "ID Card ${SimpleDateFormat("dd-MM HH:mm", Locale.US).format(System.currentTimeMillis())}"
                val did = db.dao().insertDoc(DocumentEntity(name = name, pageCount = 1, thumbnailPath = file.absolutePath))
                db.dao().insertPage(PageEntity(docId = did, originalPath = file.absolutePath, processedPath = file.absolutePath, orderIndex = 0))

                withContext(Dispatchers.Main) {
                    hideLoading()
                    cleanupTempFiles()
                    val i = Intent(this@IdCardActivity, DocDetailActivity::class.java)
                    i.putExtra("doc_id", did); i.putExtra("doc_name", name)
                    startActivity(i)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@IdCardActivity, "Merge Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
