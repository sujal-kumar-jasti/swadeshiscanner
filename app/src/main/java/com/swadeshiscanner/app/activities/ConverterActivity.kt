package com.swadeshiscanner.app.activities

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.swadeshiscanner.app.ui.screens.ConverterScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.viewmodels.ConverterViewModel

class ConverterActivity : ComponentActivity() {

    private val viewModel: ConverterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra("file_uri")
        val conversionType = intent.getStringExtra("conversion_type") ?: "WORD"

        if (uriString != null) {
            viewModel.init(Uri.parse(uriString), conversionType, contentResolver)
        } else {
            finish()
        }

        setContent {
            SwadeshiScannerTheme {
                ConverterScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup(this)
    }
}