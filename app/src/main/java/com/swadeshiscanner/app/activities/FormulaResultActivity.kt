package com.swadeshiscanner.app.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.swadeshiscanner.app.ui.screens.FormulaResultScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.viewmodels.FormulaResultViewModel

class FormulaResultActivity : ComponentActivity() {

    private val viewModel: FormulaResultViewModel by viewModels()

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
                FormulaResultScreen(
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