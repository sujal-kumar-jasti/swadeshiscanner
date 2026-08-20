package com.swadeshiscanner.app.ui.components

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.theme.CSGreen
import com.swadeshiscanner.app.utils.ExportUtils
import kotlinx.coroutines.*
import java.io.File

@Composable
fun ShareMenuSheet(
    pages: List<PageEntity>,
    docName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jpgSize by remember { mutableStateOf("...") }
    var pdfSize by remember { mutableStateOf("...") }
    var wordSize by remember { mutableStateOf("...") }
    var pptSize by remember { mutableStateOf("...") }

    val generatedFiles = remember { mutableStateListOf<File>() }
    var cachedJpgs by remember { mutableStateOf<List<File>?>(null) }

    LaunchedEffect(pages) {
        withContext(Dispatchers.IO) {
            val jpgs = pages.mapNotNull { p ->
                val f = File(p.processedPath ?: p.originalPath)
                if (f.exists()) f else null
            }
            cachedJpgs = jpgs
            val jpgTotalSize = Formatter.formatShortFileSize(context, jpgs.sumOf { it.length() })

            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val docId = pages.firstOrNull()?.docId ?: -1L
            val password = if (docId != -1L) prefs.getString("pdf_password_$docId", null) else null

            val pdfDeferred = async { 
                if (password.isNullOrEmpty()) ExportUtils.generatePdf(context, pages, docName)
                else ExportUtils.generateProtectedPdf(context, pages, docName, password) ?: ExportUtils.generatePdf(context, pages, docName)
            }
            val wordDeferred = async { ExportUtils.generateWordFromImages(context, pages, docName) }
            val pptDeferred = async { ExportUtils.generatePpt(context, pages, docName) }

            val pdf = pdfDeferred.await()
            val word = wordDeferred.await()
            val ppt = pptDeferred.await()

            generatedFiles.add(pdf)
            word?.let { generatedFiles.add(it) }
            generatedFiles.add(ppt)

            withContext(Dispatchers.Main) {
                jpgSize = "($jpgTotalSize)"
                pdfSize = "(${Formatter.formatShortFileSize(context, pdf.length())})"
                wordSize = "(${Formatter.formatShortFileSize(context, word?.length() ?: 0)})"
                pptSize = "(${Formatter.formatShortFileSize(context, ppt.length())})"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                generatedFiles.forEach { file ->
                    try { if (file.exists()) file.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp)
    ) {
        Text(
            text = "Share Document",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        BottomSheetItem(
            icon = R.drawable.ic_file_jpg,
            title = "Image (JPG)",
            subtitle = jpgSize,
            onClick = {
                cachedJpgs?.let { files ->
                    if (files.isNotEmpty()) {
                        if (files.size == 1) ExportUtils.shareFile(context, files[0], "image/jpeg")
                        else ExportUtils.shareMultipleFiles(context, files, "image/jpeg")
                    }
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFF0F0F0))

        BottomSheetItem(
            icon = R.drawable.ic_file_pdf,
            title = "Document (PDF)",
            subtitle = pdfSize,
            onClick = {
                val file = generatedFiles.find { it.name.endsWith(".pdf") && !it.name.contains("_Slides") }
                file?.let { ExportUtils.shareFile(context, it, "application/pdf") }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFF0F0F0))

        BottomSheetItem(
            icon = R.drawable.ic_word,
            title = "Word Document",
            subtitle = wordSize,
            onClick = {
                val file = generatedFiles.find { it.name.endsWith(".docx") }
                file?.let { ExportUtils.shareFile(context, it, "application/msword") }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFF0F0F0))

        BottomSheetItem(
            icon = R.drawable.ic_ppt,
            title = "Presentation Slides",
            subtitle = pptSize,
            onClick = {
                // ExportUtils says Slides are PDF in ShareMenuFragment.kt
                val file = generatedFiles.find { it.name.contains("_Slides") || it.name.endsWith(".pdf") }
                file?.let { ExportUtils.shareFile(context, it, "application/pdf") }
            }
        )
    }
}

@Composable
fun SaveMenuSheet(
    pages: List<PageEntity>,
    docName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jpgSize by remember { mutableStateOf("...") }
    var pdfSize by remember { mutableStateOf("...") }
    var wordSize by remember { mutableStateOf("...") }
    var pptSize by remember { mutableStateOf("...") }

    val generatedFiles = remember { mutableStateListOf<File>() }
    var cachedJpgs by remember { mutableStateOf<List<File>?>(null) }

    LaunchedEffect(pages) {
        withContext(Dispatchers.IO) {
            val jpgs = pages.mapNotNull { p ->
                val f = File(p.processedPath ?: p.originalPath)
                if (f.exists()) f else null
            }
            cachedJpgs = jpgs
            val jpgTotalSize = Formatter.formatShortFileSize(context, jpgs.sumOf { it.length() })

            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val docId = pages.firstOrNull()?.docId ?: -1L
            val password = if (docId != -1L) prefs.getString("pdf_password_$docId", null) else null

            val pdfDeferred = async { 
                if (password.isNullOrEmpty()) ExportUtils.generatePdf(context, pages, docName)
                else ExportUtils.generateProtectedPdf(context, pages, docName, password) ?: ExportUtils.generatePdf(context, pages, docName)
            }
            val wordDeferred = async { ExportUtils.generateWordFromImages(context, pages, docName) }
            val pptDeferred = async { ExportUtils.generatePpt(context, pages, docName) }

            val pdf = pdfDeferred.await()
            val word = wordDeferred.await()
            val ppt = pptDeferred.await()

            generatedFiles.add(pdf)
            word?.let { generatedFiles.add(it) }
            generatedFiles.add(ppt)

            withContext(Dispatchers.Main) {
                jpgSize = "($jpgTotalSize)"
                pdfSize = "(${Formatter.formatShortFileSize(context, pdf.length())})"
                wordSize = "(${Formatter.formatShortFileSize(context, word?.length() ?: 0)})"
                pptSize = "(${Formatter.formatShortFileSize(context, ppt.length())})"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                generatedFiles.forEach { file ->
                    try { if (file.exists()) file.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp)
    ) {
        Text(
            text = "Save to Device",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        BottomSheetItem(
            icon = R.drawable.ic_file_jpg,
            title = "Image (JPG)",
            subtitle = jpgSize,
            onClick = {
                cachedJpgs?.let { files ->
                    Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
                    scope.launch(Dispatchers.IO) {
                        var count = 0
                        files.forEach { file ->
                            if (ExportUtils.saveToDevice(context, file, ExportUtils.MimeType.IMAGE, "$docName - ${file.name}")) {
                                count++
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Saved $count images", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFF0F0F0))

        BottomSheetItem(
            icon = R.drawable.ic_file_pdf,
            title = "Document (PDF)",
            subtitle = pdfSize,
            onClick = {
                val file = generatedFiles.find { it.name.endsWith(".pdf") && !it.name.contains("_Slides") }
                saveAndClean(context, scope, file, ExportUtils.MimeType.PDF, docName, onDismiss)
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFF0F0F0))

        BottomSheetItem(
            icon = R.drawable.ic_word,
            title = "Word Document",
            subtitle = wordSize,
            onClick = {
                val file = generatedFiles.find { it.name.endsWith(".docx") }
                saveAndClean(context, scope, file, ExportUtils.MimeType.WORD, docName, onDismiss)
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFF0F0F0))

        BottomSheetItem(
            icon = R.drawable.ic_ppt,
            title = "Presentation Slides",
            subtitle = pptSize,
            onClick = {
                val file = generatedFiles.find { it.name.contains("_Slides") || (it.name.endsWith(".pdf") && it.length() > 0) }
                saveAndClean(context, scope, file, ExportUtils.MimeType.PDF, docName + "_Slides", onDismiss)
            }
        )
    }
}

private fun saveAndClean(
    context: Context,
    scope: CoroutineScope,
    file: File?,
    type: ExportUtils.MimeType,
    name: String,
    onDismiss: () -> Unit
) {
    if (file == null) {
        Toast.makeText(context, "Processing...", Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()

    scope.launch(Dispatchers.IO) {
        val success = ExportUtils.saveToDevice(context, file, type, name)
        if (file.exists()) file.delete()
        withContext(Dispatchers.Main) {
            if (success) Toast.makeText(context, "Saved to Documents", Toast.LENGTH_LONG).show()
            else Toast.makeText(context, "Save Failed", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
}

@Composable
fun MenuBottomSheet(
    isGridMode: Boolean,
    showSort: Boolean,
    isSortByDate: Boolean,
    onOptionSelected: (MenuOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        BottomSheetMenuItem(
            icon = R.drawable.ic_image,
            title = "Import from Gallery",
            iconTint = CSGreen,
            onClick = { onOptionSelected(MenuOption.IMPORT) }
        )

        HorizontalDivider(color = Color(0xFFF5F5F5))

        BottomSheetMenuItem(
            icon = if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid,
            title = if (isGridMode) "Switch to List View" else "Switch to Grid View",
            onClick = { onOptionSelected(MenuOption.TOGGLE_VIEW) }
        )

        if (showSort) {
            BottomSheetMenuItem(
                icon = R.drawable.ic_sort,
                title = if (isSortByDate) "Sort by Name" else "Sort by Date",
                onClick = { onOptionSelected(MenuOption.TOGGLE_SORT) }
            )
        }

        HorizontalDivider(color = Color(0xFFF5F5F5))

        BottomSheetMenuItem(
            icon = R.drawable.ic_settings,
            title = "Settings",
            onClick = { onOptionSelected(MenuOption.SETTINGS) }
        )
    }
}

@Composable
fun BottomSheetItem(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BottomSheetMenuItem(
    icon: Int,
    title: String,
    iconTint: Color = Color(0xFF555555),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
    }
}

enum class MenuOption { IMPORT, TOGGLE_VIEW, TOGGLE_SORT, SETTINGS }

enum class DocMenuOption { RENAME, DELETE, BATCH_EDIT, ENCRYPT, IMPORT_PDF, SETTINGS }

@Composable
fun DocMenuBottomSheet(
    onOptionSelected: (DocMenuOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        BottomSheetMenuItem(
            icon = R.drawable.ic_edit,
            title = "Rename Document",
            onClick = { onOptionSelected(DocMenuOption.RENAME) }
        )

        BottomSheetMenuItem(
            icon = R.drawable.ic_grid,
            title = "Batch Edit Pages",
            onClick = { onOptionSelected(DocMenuOption.BATCH_EDIT) }
        )

        BottomSheetMenuItem(
            icon = R.drawable.ic_lock,
            title = "Set PDF Password",
            onClick = { onOptionSelected(DocMenuOption.ENCRYPT) }
        )

        BottomSheetMenuItem(
            icon = R.drawable.ic_file_pdf,
            title = "Import from PDF",
            onClick = { onOptionSelected(DocMenuOption.IMPORT_PDF) }
        )

        HorizontalDivider(color = Color(0xFFF5F5F5).copy(alpha = 0.1f))

        BottomSheetMenuItem(
            icon = R.drawable.ic_delete,
            title = "Delete Document",
            iconTint = Color.Red,
            onClick = { onOptionSelected(DocMenuOption.DELETE) }
        )
    }
}
