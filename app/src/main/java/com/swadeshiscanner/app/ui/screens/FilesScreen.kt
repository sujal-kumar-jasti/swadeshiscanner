package com.swadeshiscanner.app.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.rememberAsyncImagePainter
import java.io.File
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.swadeshiscanner.app.DocDetailActivity
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.database.AppDatabase
import com.swadeshiscanner.app.database.DocumentEntity
import com.swadeshiscanner.app.ui.components.GlowSearchBar
import com.swadeshiscanner.app.ui.theme.CSGreen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val dao = AppDatabase.get(context).dao()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All Docs") }
    var documents by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(searchQuery, selectedFilter) {
        val flow = if (searchQuery.isEmpty()) dao.getAllDocs() else dao.searchDocs(searchQuery)
        flow.collectLatest { docs ->
            val validDocs = docs.filter { !it.name.contains("TEMP", ignoreCase = true) }
            documents = when (selectedFilter) {
                "Recent" -> validDocs.sortedByDescending { it.createdTime }
                "PDFs" -> validDocs.filter { it.name.endsWith(".pdf", ignoreCase = true) }
                else -> validDocs
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                TopAppBar(
                    title = { Text("My Files", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { /* Sort */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionItem(Icons.Default.Share, "Share", Color.Gray) { /* Share */ }
                        ActionItem(Icons.Default.Edit, "Rename", Color.Gray) { /* Rename */ }
                        ActionItem(Icons.Default.Delete, "Delete", Color.Red) { /* Delete */ }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            GlowSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(16.dp)
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("All Docs", "Recent", "PDFs").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CSGreen.copy(alpha = 0.1f),
                            selectedLabelColor = CSGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            selectedBorderColor = CSGreen
                        )
                    )
                }
            }

            if (documents.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_folder_open),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            modifier = Modifier.size(120.dp)
                        )
                        Text(
                            "No files found", 
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(documents) { doc ->
                        DocumentRow(
                            doc = doc,
                            isSelected = selectedIds.contains(doc.id),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (selectedIds.contains(doc.id)) {
                                        selectedIds - doc.id
                                    } else {
                                        selectedIds + doc.id
                                    }
                                } else {
                                    val intent = Intent(context, DocDetailActivity::class.java)
                                    intent.putExtra("doc_id", doc.id)
                                    intent.putExtra("doc_name", doc.name)
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val displayColor = if (color == Color.Gray) MaterialTheme.colorScheme.onSurface else color
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = displayColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = displayColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DocumentRow(doc: DocumentEntity, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CSGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (doc.thumbnailPath != null) {
                Image(
                    painter = rememberAsyncImagePainter(doc.thumbnailPath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(id = if (doc.name.endsWith(".pdf", true)) R.drawable.ic_file_pdf else R.drawable.ic_image),
                    contentDescription = null,
                    tint = if (doc.name.endsWith(".pdf", true)) Color.Red else CSGreen,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = doc.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(
                    text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(doc.createdTime)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CSGreen)
            }
        }
    }
}
