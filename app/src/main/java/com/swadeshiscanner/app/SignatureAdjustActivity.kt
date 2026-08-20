package com.swadeshiscanner.app

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.swadeshiscanner.app.ui.screens.SignatureAdjustScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.viewmodels.SignatureAdjustViewModel

class SignatureAdjustActivity : ComponentActivity() {

    private val viewModel: SignatureAdjustViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val pagePath = intent.getStringExtra("page_path")
        val pageId = intent.getLongExtra("page_id", -1L)

        viewModel.init(pagePath, pageId)

        setContent {
            SwadeshiScannerTheme {
                SignatureAdjustScreen(
                    pagePath = viewModel.pagePath,
                    signatures = viewModel.signatures,
                    onCancel = { finish() },
                    onRotate = { /* Implement rotation if needed */ },
                    onSave = { sig, scale, rot, offset, size, boxSize -> 
                        viewModel.saveMerged(sig, scale, rot, offset, size, boxSize) 
                    },
                    onSaveDrawnSignature = { viewModel.saveDrawnSignature(it) },
                    onExtractFromImage = { viewModel.extractSignatureFromImage(it) },
                    onAddDate = { viewModel.addDateAsSignature() },
                    onDeleteSignature = { viewModel.deleteSignature(it) }
                )

                LaunchedEffect(viewModel.savedPath.value) {
                    viewModel.savedPath.value?.let {
                        val intent = Intent()
                        intent.putExtra("saved_path", it)
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }
            }
        }
    }
}
