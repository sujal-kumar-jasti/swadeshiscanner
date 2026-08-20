package com.swadeshiscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.swadeshiscanner.app.ui.screens.OcrResultScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.viewmodels.OcrResultViewModel

class OcrResultActivity : ComponentActivity() {

    private val viewModel: OcrResultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val docId = intent.getLongExtra("doc_id", -1L)
        if (docId != -1L) {
            viewModel.init(docId)
        } else {
            finish()
        }

        setContent {
            SwadeshiScannerTheme {
                OcrResultScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
}