package com.swadeshiscanner.app.activities

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.components.RenameDialog
import com.swadeshiscanner.app.ui.screens.PassportPhotoScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class PassportPhotoActivity : ComponentActivity() {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val db by lazy { AppDatabase.get(this) }
    private var isSignatureMode = false
    private var showCountDialogState by mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        isSignatureMode = intent.getBooleanExtra("is_signature", false)

        startCamera()

        setContent {
            SwadeshiScannerTheme {
                PassportPhotoScreen(
                    onBack = { finish() },
                    onCapture = { takePhoto() },
                    onRotate = { /* Future: front/back camera switch */ },
                    onPreviewViewCreated = { previewView ->
                        setupCameraWithPreview(previewView)
                    }
                )

                showCountDialogState?.let { bitmap ->
                    RenameDialog(
                        title = "Passport Photos",
                        message = "How many copies?",
                        positiveText = "Next",
                        prefill = "8",
                        onDismiss = { showCountDialogState = null },
                        onPositive = { countStr: String ->
                            showCountDialogState = null
                            val count = countStr.toIntOrNull() ?: 8
                            lifecycleScope.launch(Dispatchers.Default) {
                                performCropAndSave(bitmap, count)
                            }
                        }
                    )
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
        val tempFile = File.createTempFile("passport_temp", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(this@PassportPhotoActivity, "Capture failed", Toast.LENGTH_SHORT).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                lifecycleScope.launch(Dispatchers.Default) {
                    val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                    if (bitmap != null) {
                        if (isSignatureMode) performCropAndSave(bitmap, 1)
                        else showCountDialogState = bitmap
                    }
                }
            }
        })
    }

    private fun performCropAndSave(sourceBitmap: Bitmap, count: Int) {
        val w = sourceBitmap.width.toFloat()
        val h = sourceBitmap.height.toFloat()
        val boxWidth = w * 0.7f
        val boxHeight = boxWidth / (7f / 9f)
        val left = (w - boxWidth) / 2
        val top = (h - boxHeight) / 2

        val safeX = max(0, left.toInt())
        val safeY = max(0, top.toInt())
        val safeWidth = min(sourceBitmap.width - safeX, boxWidth.toInt())
        val safeHeight = min(sourceBitmap.height - safeY, boxHeight.toInt())

        if (safeWidth <= 0 || safeHeight <= 0) return

        val highResCrop = Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeWidth, safeHeight)

        val result = if (isSignatureMode) highResCrop else create4ColGrid(highResCrop, count)
        val name = if(isSignatureMode) "Signature" else "Passport_Photo"
        lifecycleScope.launch(Dispatchers.IO) {
            saveToDatabaseAndOpen(result, "${name}_${System.currentTimeMillis()}")
        }
    }

    private fun create4ColGrid(photo: Bitmap, count: Int): Bitmap {
        val a4Width = 2480
        val gap = 40
        val targetW = 413
        val targetH = 531
        val scaledPhoto = getHighQualityScaledBitmap(photo, targetW, targetH)

        val cols = 4
        val rows = (count + cols - 1) / cols
        val totalH = (rows * (targetH + gap)) + gap
        val totalRowWidth = (cols * targetW) + ((cols - 1) * gap)
        val startX = (a4Width - totalRowWidth) / 2

        val sheet = Bitmap.createBitmap(a4Width, max(totalH, 100), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }

        var x = startX
        var y = gap
        var itemsInRow = 0

        for (i in 0 until count) {
            canvas.drawBitmap(scaledPhoto, x.toFloat(), y.toFloat(), paint)
            itemsInRow++
            x += targetW + gap
            if (itemsInRow >= cols) {
                itemsInRow = 0
                x = startX
                y += targetH + gap
            }
        }
        return sheet
    }

    private fun getHighQualityScaledBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        var w = src.width
        var h = src.height
        var current = src

        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2
            h /= 2
            val scaled = Bitmap.createScaledBitmap(current, w, h, true)
            if (current != src) current.recycle()
            current = scaled
        }

        if (w != targetW || h != targetH) {
            val final = Bitmap.createScaledBitmap(current, targetW, targetH, true)
            if (current != src) current.recycle()
            current = final
        }
        return current
    }

    private suspend fun saveToDatabaseAndOpen(bitmap: Bitmap, docName: String) {
        val fileName = "$docName.jpg"
        val file = File(filesDir, fileName)
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.close()

        val newDoc = DocumentEntity(name = docName, createdTime = System.currentTimeMillis(), pageCount = 1, thumbnailPath = file.absolutePath)
        val docId = db.dao().insertDoc(newDoc)

        val newPage = PageEntity(
            docId = docId,
            originalPath = file.absolutePath,
            processedPath = file.absolutePath,
            orderIndex = 0,
            cropData = "#0.0#0"
        )
        db.dao().insertPage(newPage)

        withContext(Dispatchers.Main) {
            val intent = Intent(this@PassportPhotoActivity, DocDetailActivity::class.java)
            intent.putExtra("doc_id", docId)
            intent.putExtra("doc_name", docName)
            startActivity(intent)
            finish()
        }
    }
}
