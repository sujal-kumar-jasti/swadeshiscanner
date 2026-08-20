package com.swadeshiscanner.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.ui.screens.CameraScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CameraActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val batchPathsState = mutableStateListOf<String>()
    private val flashModeState = mutableStateOf(ImageCapture.FLASH_MODE_AUTO)
    private val lastThumbPathState = mutableStateOf<String?>(null)
    private val isCameraReady = mutableStateOf(false)
    
    private var existingDocId: Long = -1
    private var nextOrderIndex: Int = 0
    private var isCapturing = false
    private var isSingleCaptureMode = false
    private var isBookMode = false
    private var isOcrMode = false
    private var isFormulaMode = false
    private var isTranslateMode = false

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        setupLaunchers()

        isSingleCaptureMode = intent.getBooleanExtra("is_single_capture", false)
        isBookMode = intent.getBooleanExtra("is_book_mode", false)
        isOcrMode = intent.getBooleanExtra("is_ocr_mode", false)
        isFormulaMode = intent.getBooleanExtra("is_formula_mode", false)
        isTranslateMode = intent.getBooleanExtra("is_translate_mode", false)
        
        // OCR/Formula/Translate need CROP, so they act like batch mode (not immediate finish)
        if (isOcrMode || isFormulaMode || isTranslateMode) {
            isSingleCaptureMode = false 
        }

        existingDocId = intent.getLongExtra("existing_doc_id", -1)
        nextOrderIndex = intent.getIntExtra("next_order_index", 0)

        if (checkPermissions()) startCamera() else requestPermissions()

        setContent {
            SwadeshiScannerTheme {
                CameraScreen(
                    onBack = { finish() },
                    onCapture = { takePhoto() },
                    onFlashToggle = { toggleFlashMode() },
                    onGalleryClick = { openGallery() },
                    onDoneClick = { proceedToCrop() },
                    flashMode = flashModeState.value,
                    batchCount = batchPathsState.size,
                    lastThumbPath = lastThumbPathState.value,
                    isSingleCapture = isSingleCaptureMode,
                    isCameraReady = isCameraReady.value,
                    onPreviewViewCreated = { previewView ->
                        setupCameraWithPreview(previewView)
                    }
                )
            }
        }
    }

    private fun setupLaunchers() {
        cropActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val docId = result.data?.getLongExtra("saved_doc_id", -1L) ?: -1L
                if (docId != -1L) {
                    when {
                        isOcrMode -> {
                            val intent = Intent(this, OcrResultActivity::class.java).apply {
                                putExtra("doc_id", docId)
                            }
                            startActivity(intent)
                            finish()
                        }
                        isFormulaMode -> {
                            val intent = Intent(this, com.swadeshiscanner.app.activities.FormulaResultActivity::class.java).apply {
                                putExtra("doc_id", docId)
                            }
                            startActivity(intent)
                            finish()
                        }
                        isTranslateMode -> {
                            val intent = Intent(this, com.swadeshiscanner.app.activities.DocumentTranslateActivity::class.java).apply {
                                putExtra("doc_id", docId)
                            }
                            startActivity(intent)
                            finish()
                        }
                        else -> {
                            val intent = Intent()
                            intent.putExtra("saved_doc_id", docId)
                            setResult(RESULT_OK, intent)
                            finish() 
                        }
                    }
                    return@registerForActivityResult
                }
                val updated = result.data?.getStringArrayListExtra("updated_batch_paths")
                if (updated != null) {
                    batchPathsState.clear()
                    batchPathsState.addAll(updated)
                    lastThumbPathState.value = updated.lastOrNull()
                }
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uris = mutableListOf<Uri>()
                if (data?.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) uris.add(data.clipData!!.getItemAt(i).uri)
                } else if (data?.data != null) {
                    uris.add(data.data!!)
                }
                if (uris.isNotEmpty()) processGalleryUris(uris)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            isCameraReady.value = true
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupCameraWithPreview(previewView: PreviewView) {
        val provider = cameraProvider ?: return
        
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(4032, 3024), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()

        preview.surfaceProvider = previewView.surfaceProvider

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .setJpegQuality(90)
            .setFlashMode(flashModeState.value)
            .build()

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            cameraControl = camera.cameraControl
            applyFlashLogic()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun takePhoto() {
        if (isCapturing) return
        isCapturing = true
        val capture = imageCapture ?: run { isCapturing = false; return }

        val tempFile = File.createTempFile("cam_temp", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                isCapturing = false
                Toast.makeText(this@CameraActivity, "Capture failed", Toast.LENGTH_SHORT).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val correctedFile = bakeRotationIntoFile(tempFile)
                        if (isSingleCaptureMode) {
                            withContext(Dispatchers.Main) {
                                val intent = Intent()
                                intent.putExtra("saved_file_path", correctedFile.absolutePath)
                                setResult(RESULT_OK, intent)
                                finish()
                            }
                        } else {
                            val persistentPath = ScanRepository.copyFileToSession(this@CameraActivity, correctedFile)
                            val bitmap = BitmapFactory.decodeFile(persistentPath)
                            if (bitmap != null) {
                                val points = ScanUtils.detectCorners(bitmap)
                                CropActivity.temporaryCropCache[persistentPath] = CachedPageData(points, 0f)
                                bitmap.recycle()
                            }
                            withContext(Dispatchers.Main) {
                                batchPathsState.add(persistentPath)
                                lastThumbPathState.value = persistentPath
                                isCapturing = false
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace(); isCapturing = false }
                }
            }
        })
    }

    private fun toggleFlashMode() {
        flashModeState.value = when (flashModeState.value) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        applyFlashLogic()
    }

    private fun applyFlashLogic() {
        val control = cameraControl ?: return
        val capture = imageCapture ?: return
        when (flashModeState.value) {
            ImageCapture.FLASH_MODE_ON -> { control.enableTorch(true); capture.flashMode = ImageCapture.FLASH_MODE_OFF }
            ImageCapture.FLASH_MODE_AUTO -> { control.enableTorch(false); capture.flashMode = ImageCapture.FLASH_MODE_AUTO }
            else -> { control.enableTorch(false); capture.flashMode = ImageCapture.FLASH_MODE_OFF }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
        galleryLauncher.launch(Intent.createChooser(intent, "Select Pictures"))
    }

    private fun processGalleryUris(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val paths = mutableListOf<String>()
            uris.forEach { uri ->
                try {
                    val input = contentResolver.openInputStream(uri)
                    var bitmap = BitmapFactory.decodeStream(input)
                    input?.close()
                    if (bitmap != null) {
                        bitmap = fixGalleryRotation(uri, bitmap)
                        val path = ScanRepository.saveImageToSession(this@CameraActivity, bitmap)
                        val points = ScanUtils.detectCorners(bitmap)
                        CropActivity.temporaryCropCache[path] = CachedPageData(points, 0f)
                        paths.add(path)
                        bitmap.recycle()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            withContext(Dispatchers.Main) {
                batchPathsState.addAll(paths)
                lastThumbPathState.value = paths.lastOrNull()
            }
        }
    }

    private fun proceedToCrop() {
        val intent = Intent(this, CropActivity::class.java)
        intent.putStringArrayListExtra("batch_images", ArrayList(batchPathsState))
        intent.putExtra("existing_doc_id", existingDocId)
        intent.putExtra("next_order_index", nextOrderIndex)
        intent.putExtra("is_book_mode", isBookMode)
        intent.putExtra("is_ocr_mode", isOcrMode)
        intent.putExtra("is_formula_mode", isFormulaMode)
        intent.putExtra("is_translate_mode", isTranslateMode)
        cropActivityLauncher.launch(intent)
    }

    private fun bakeRotationIntoFile(source: File): File {
        try {
            val exif = androidx.exifinterface.media.ExifInterface(source.absolutePath)
            val orient = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
            val rot = when (orient) { androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90; androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180; androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0 }
            if (rot == 0) return source
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return source
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            val newFile = File.createTempFile("fixed", ".jpg", cacheDir)
            FileOutputStream(newFile).use { out -> rotated.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            rotated.recycle(); bitmap.recycle()
            return newFile
        } catch (e: Exception) { return source }
    }

    private fun fixGalleryRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val input = contentResolver.openInputStream(uri) ?: return bitmap
            val exif = androidx.exifinterface.media.ExifInterface(input)
            val orient = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
            input.close()
            val rot = when (orient) { androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90; androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180; androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0 }
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                bitmap.recycle()
                return rotated
            }
        } catch (e: Exception) {}
        return bitmap
    }

    private fun checkPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestPermissions() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera() else finish()
    }
}
