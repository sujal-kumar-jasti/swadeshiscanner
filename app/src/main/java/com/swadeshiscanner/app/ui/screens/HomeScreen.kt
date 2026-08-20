package com.swadeshiscanner.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.CameraActivity
import com.swadeshiscanner.app.CropActivity
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.IdCardActivity
import com.swadeshiscanner.app.OcrResultActivity
import com.swadeshiscanner.app.activities.ConverterActivity
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.ui.components.GlowSearchBar
import com.swadeshiscanner.app.ui.components.MenuBottomSheet
import com.swadeshiscanner.app.ui.components.MenuOption
import com.swadeshiscanner.app.ScanRepository
import com.swadeshiscanner.app.ui.theme.CSGreen
import com.swadeshiscanner.app.ui.theme.CSGreenDark
import com.swadeshiscanner.app.utils.DocImportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

data class ToolItemData(val name: String, val icon: Int, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTools: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.get(context)
    val dao = db.dao()

    var searchQuery by remember { mutableStateOf("") }
    var documents by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }
    var isGridMode by remember { mutableStateOf(true) }
    var isSortByDate by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showOcrOptions by remember { mutableStateOf(false) }
    var selectedDocIds by remember { mutableStateOf(setOf<Long>()) }

    val sheetState = rememberModalBottomSheetState()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val uriStrings = ArrayList(uris.map { it.toString() })
            val intent = Intent(context, CropActivity::class.java)
            intent.putStringArrayListExtra("batch_images", uriStrings)
            intent.putExtra("existing_doc_id", -1L)
            context.startActivity(intent)
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "Importing ${uris.size} files...", Toast.LENGTH_SHORT).show()
            scope.launch {
                DocImportHelper.performFileImport(context, this, uris)
                Toast.makeText(context, "Import Successful", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val ocrGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val intent = Intent(context, CropActivity::class.java)
            intent.putStringArrayListExtra("batch_images", arrayListOf(it.toString()))
            intent.putExtra("is_ocr_mode", true)
            context.startActivity(intent)
        }
    }

    LaunchedEffect(searchQuery, isSortByDate) {
        val flow = if (searchQuery.isEmpty()) dao.getAllDocs() else dao.searchDocs(searchQuery)
        flow.collectLatest { docs ->
            val validDocs = docs.filter { !it.name.contains("TEMP", ignoreCase = true) }
            documents = if (isSortByDate) {
                validDocs.sortedByDescending { it.createdTime }
            } else {
                validDocs.sortedBy { it.name.lowercase() }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (selectedDocIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedDocIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedDocIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // Show delete confirmation
                            scope.launch(Dispatchers.IO) {
                                selectedDocIds.forEach { docId ->
                                    val allPages = dao.getPagesList(docId)
                                    allPages.forEach { page ->
                                        ScanRepository.cleanupPageFiles(context, page.id, page.originalPath, page.processedPath)
                                        db.dao().deletePage(page)
                                    }
                                    dao.deleteDocById(docId)
                                }
                                ScanRepository.performAggressiveCleanup(context)
                                withContext(Dispatchers.Main) {
                                    selectedDocIds = emptySet()
                                    Toast.makeText(context, "Documents deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Glass Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                CSGreen.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.15f else 0.2f),
                                CSGreen.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Spacer(modifier = Modifier.height(56.dp)) // Extra space to let gradient breathe
                GlowSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.padding(16.dp)
                )

                ToolsGrid(
                    onToolClick = { tool ->
                        when (tool) {
                            "Smart Scan" -> context.startActivity(Intent(context, CameraActivity::class.java))
                            "PDF Tools" -> onNavigateToTools()
                            "Import Pic" -> galleryLauncher.launch("image/*")
                            "Import Files" -> fileImportLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "application/vnd.ms-powerpoint"
                                )
                            )
                            "ID Cards" -> context.startActivity(Intent(context, IdCardActivity::class.java))
                            "Img to Text" -> showOcrOptions = true
                            "PDF to Word" -> {
                                val intent = Intent(context, ConverterActivity::class.java)
                                intent.putExtra("conversion_type", "WORD")
                                context.startActivity(intent)
                            }
                            "More" -> { showMenu = true }
                        }
                    }
                )
            }

            // Recent Docs Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Documents",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                TextButton(onClick = onNavigateToFiles) {
                    Text("View All", color = CSGreen, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_image),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            modifier = Modifier.size(100.dp)
                        )
                        Text(
                            "No documents yet",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isGridMode) 2 else 1),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(documents) { doc ->
                        DocumentItem(
                            doc = doc,
                            isGrid = isGridMode,
                            isSelected = selectedDocIds.contains(doc.id),
                            onClick = {
                                if (selectedDocIds.isNotEmpty()) {
                                    selectedDocIds = if (selectedDocIds.contains(doc.id)) selectedDocIds - doc.id else selectedDocIds + doc.id
                                } else {
                                    val intent = Intent(context, DocDetailActivity::class.java)
                                    intent.putExtra("doc_id", doc.id)
                                    intent.putExtra("doc_name", doc.name)
                                    context.startActivity(intent)
                                }
                            },
                            onLongClick = {
                                selectedDocIds = selectedDocIds + doc.id
                            }
                        )
                    }
                }
            }
        }

        if (showMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMenu = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                MenuBottomSheet(
                    isGridMode = isGridMode,
                    showSort = true,
                    isSortByDate = isSortByDate,
                    onOptionSelected = { option ->
                        showMenu = false
                        when (option) {
                            MenuOption.IMPORT -> galleryLauncher.launch("image/*")
                            MenuOption.TOGGLE_VIEW -> isGridMode = !isGridMode
                            MenuOption.TOGGLE_SORT -> isSortByDate = !isSortByDate
                            MenuOption.SETTINGS -> { /* Navigate to settings */ }
                        }
                    }
                )
            }
        }

        if (showOcrOptions) {
            AlertDialog(
                onDismissRequest = { showOcrOptions = false },
                title = { Text("Img to Text") },
                text = { Text("Choose source for OCR") },
                confirmButton = {
                    TextButton(onClick = {
                        showOcrOptions = false
                        val intent = Intent(context, CameraActivity::class.java)
                        intent.putExtra("is_single_capture", true)
                        // Note: I'll need a way to pass result back or handle in CameraActivity
                        context.startActivity(intent)
                    }) { Text("Camera") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showOcrOptions = false
                        ocrGalleryLauncher.launch("image/*")
                    }) { Text("Gallery") }
                }
            )
        }
    }
}

@Composable
fun ToolsGrid(onToolClick: (String) -> Unit) {
    val tools = listOf(
        ToolItemData("Smart Scan", R.drawable.ic_camera, Color(0xFF00C895)),
        ToolItemData("ID Cards", R.drawable.ic_id_card, Color(0xFF00BCD4)),
        ToolItemData("PDF Tools", R.drawable.ic_file_pdf, Color(0xFFFF5252)),
        ToolItemData("Img to Text", R.drawable.ic_text, Color(0xFFFFAB40)),
        ToolItemData("Import Pic", R.drawable.ic_image, Color(0xFF448AFF)),
        ToolItemData("PDF to Word", R.drawable.ic_word, Color(0xFFD0D6F6)),
        ToolItemData("Import Files", R.drawable.ic_folder, Color(0xFF7649F8)),
        ToolItemData("More", R.drawable.ic_settings, Color(0xFF9E9E9E))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 16.dp)
    ) {
        tools.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { tool ->
                    Box(modifier = Modifier.weight(1f)) {
                        ToolItem(tool, onToolClick)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolItem(tool: ToolItemData, onToolClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToolClick(tool.name) }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            color = tool.color.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, tool.color.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                val useOriginalColor = tool.name.contains("PDF Tools", ignoreCase = true) || 
                                     tool.name.contains("PDF to Word", ignoreCase = true)
                                     
                Icon(
                    painter = painterResource(id = tool.icon),
                    contentDescription = tool.name,
                    tint = if (useOriginalColor) Color.Unspecified else tool.color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = tool.name,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
            maxLines = 1
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocumentItem(doc: DocumentEntity, isGrid: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) CSGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, CSGreen) else null,
        shadowElevation = 2.dp
    ) {
        if (isGrid) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    if (doc.thumbnailPath != null && File(doc.thumbnailPath!!).exists()) {
                        AsyncImage(
                            model = File(doc.thumbnailPath!!),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_image),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            modifier = Modifier.align(Alignment.Center).size(60.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = doc.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(doc.createdTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                ) {
                    if (doc.thumbnailPath != null && File(doc.thumbnailPath!!).exists()) {
                        AsyncImage(
                            model = File(doc.thumbnailPath!!),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(doc.createdTime)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
