package com.swadeshiscanner.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.ui.screens.BookResultScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.viewmodels.BookResultViewModel

class BookResultActivity : AppCompatActivity() {

    private val viewModel: BookResultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val docId = intent.getLongExtra("doc_id", -1L)
        if (docId == -1L) {
            Toast.makeText(this, "Error: No data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            SwadeshiScannerTheme {
                BookResultScreen(
                    statusText = viewModel.statusText,
                    progress = viewModel.progress
                )

                LaunchedEffect(viewModel.isDone) {
                    if (viewModel.isDone) {
                        val intent = Intent(this@BookResultActivity, DocDetailActivity::class.java).apply {
                            putExtra("doc_id", docId)
                            putExtra("doc_name", viewModel.docName)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                }

                LaunchedEffect(viewModel.error) {
                    viewModel.error?.let {
                        Toast.makeText(this@BookResultActivity, "Error: $it", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }

        viewModel.startBatchSplitting(docId)
    }
}
