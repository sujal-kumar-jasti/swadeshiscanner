package com.swadeshiscanner.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.swadeshiscanner.app.ui.components.RenameDialog
import com.swadeshiscanner.app.ui.components.ShareMenuSheet
import com.swadeshiscanner.app.ui.screens.SinglePageScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import com.swadeshiscanner.app.ui.viewmodels.SinglePageViewModel

class SinglePageActivity : AppCompatActivity() {

    private val viewModel: SinglePageViewModel by viewModels()
    private var showNoteDialog by mutableStateOf(false)
    private var showShareSheet by mutableStateOf(false)

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val pageId = intent.getLongExtra("page_id", -1)
            viewModel.loadData(pageId, intent.getStringExtra("doc_name") ?: "Document")
            setResult(RESULT_OK)
        }
    }

    private val signAdjustLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("saved_path")
            if (path != null) {
                viewModel.currentPage?.let { page ->
                    viewModel.updatePage(page.copy(processedPath = path))
                    setResult(RESULT_OK)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val pageId = intent.getLongExtra("page_id", -1)
        val docName = intent.getStringExtra("doc_name") ?: "Document"

        if (pageId == -1L) {
            finish()
            return
        }

        viewModel.loadData(pageId, docName)

        setContent {
            SwadeshiScannerTheme {
                val sheetState = rememberModalBottomSheetState()
                
                SinglePageScreen(
                    currentPage = viewModel.currentPage,
                    currentIndex = viewModel.currentIndex,
                    isLoading = viewModel.isLoading,
                    onBack = { finish() },
                    onShare = { showShareSheet = true },
                    onRotate = { viewModel.performRotation() },
                    onCrop = {
                        viewModel.currentPage?.let { page ->
                            val intent = Intent(this, CropActivity::class.java).apply {
                                putExtra("edit_page_id", page.id)
                                putExtra("doc_id", page.docId)
                                putExtra("original_image_path", page.originalPath)
                            }
                            editLauncher.launch(intent)
                        }
                    },
                    onExtract = {
                        val path = viewModel.currentPage?.processedPath ?: viewModel.currentPage?.originalPath ?: return@SinglePageScreen
                        val intent = Intent(this, OcrResultActivity::class.java).apply {
                            putExtra("image_path", path)
                        }
                        startActivity(intent)
                    },
                    onFilter = {
                        viewModel.currentPage?.let { page ->
                            val intent = Intent(this, FilterActivity::class.java).apply {
                                putExtra("page_id", page.id)
                            }
                            editLauncher.launch(intent)
                        }
                    },
                    onSign = { openSignatureAdjustment(null) },
                    onNote = { showNoteDialog = true }
                )

                if (showNoteDialog) {
                    RenameDialog(
                        title = "Notes: Page ${viewModel.currentIndex + 1}",
                        prefill = viewModel.currentPage?.notes ?: "",
                        onDismiss = { showNoteDialog = false },
                        onPositive = {
                            viewModel.saveNotes(it)
                            showNoteDialog = false
                        }
                    )
                }

                if (showShareSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showShareSheet = false },
                        sheetState = sheetState
                    ) {
                        viewModel.currentPage?.let { page ->
                            ShareMenuSheet(
                                pages = listOf(page),
                                docName = docName,
                                onDismiss = { showShareSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openSignatureAdjustment(uri: Uri?) {
        val intent = Intent(this, SignatureAdjustActivity::class.java).apply {
            putExtra("page_path", viewModel.currentPage?.processedPath ?: viewModel.currentPage?.originalPath)
            putExtra("page_id", viewModel.currentPage?.id ?: -1L)
            if (uri != null) {
                putExtra("signature_uri", uri.toString())
            }
        }
        signAdjustLauncher.launch(intent)
    }
}
