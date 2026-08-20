package com.swadeshiscanner.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.theme.CSGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocDetailScreen(
    docName: String,
    pages: List<PageEntity>,
    selectedIds: Set<Long>,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMenuClick: () -> Unit,
    onPageClick: (PageEntity) -> Unit,
    onPageLongClick: (PageEntity) -> Unit,
    onAddPage: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onMovePage: (Int, Int) -> Unit,
    showAddButton: Boolean
) {
    var draggedItemId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val gridState = rememberLazyGridState()
    
    BackHandler(enabled = selectedIds.isNotEmpty() || draggedItemId != null) {
        if (selectedIds.isNotEmpty()) {
            onBack()
        }
        draggedItemId = null
        dragOffset = Offset.Zero
    }

    LaunchedEffect(pages.size) {
        draggedItemId = null
        dragOffset = Offset.Zero
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier.clickable(enabled = selectedIds.isEmpty()) { onRename() }
                        ) {
                            Text(
                                text = if (selectedIds.isEmpty()) docName else "${selectedIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (selectedIds.isEmpty()) {
                                Text(
                                    text = "Tap to rename",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CSGreen
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (selectedIds.isNotEmpty()) onBack() else onBack() }) {
                            Icon(
                                imageVector = if (selectedIds.isEmpty()) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (selectedIds.isNotEmpty() || pages.isNotEmpty()) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CSGreen),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, CSGreen)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_save), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save As", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CSGreen)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_share_white), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share As", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = pages,
                    key = { it.id }
                ) { page ->
                    val index = pages.indexOf(page)
                    val isDragging = draggedItemId == page.id
                    
                    PageItem(
                        page = page,
                        index = index + 1,
                        isSelected = selectedIds.contains(page.id),
                        onClick = { 
                            if (selectedIds.isNotEmpty()) {
                                onPageClick(page)
                            } else {
                                onPageClick(page)
                            }
                        },
                        onLongClick = { 
                            if (selectedIds.isEmpty()) {
                                onPageLongClick(page)
                            }
                        },
                        onDragStart = {
                            if (selectedIds.isEmpty()) {
                                draggedItemId = page.id
                                dragOffset = Offset.Zero
                            }
                        },
                        onDrag = { delta ->
                            if (selectedIds.isEmpty()) {
                                dragOffset += delta
                                val fromIndex = pages.indexOfFirst { it.id == draggedItemId }
                                if (fromIndex != -1) {
                                    val threshold = 120f
                                    if (Math.abs(dragOffset.y) > threshold || Math.abs(dragOffset.x) > threshold) {
                                        val rowOffset = if (dragOffset.y > threshold) 2 else if (dragOffset.y < -threshold) -2 else 0
                                        val colOffset = if (dragOffset.x > threshold) 1 else if (dragOffset.x < -threshold) -1 else 0
                                        val toIndex = fromIndex + rowOffset + colOffset
                                        if (toIndex in pages.indices && toIndex != fromIndex) {
                                            onMovePage(fromIndex, toIndex)
                                            dragOffset = Offset.Zero
                                        }
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragOffset.getDistance() < 20f && draggedItemId == page.id) {
                                // Long press with almost no movement -> Selection mode
                                onPageLongClick(page)
                            }
                            draggedItemId = null
                            dragOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = if (isDragging) 1.05f else 1f
                                scaleX = scale
                                scaleY = scale
                                alpha = if (isDragging) 0.7f else 1f
                                shadowElevation = if (isDragging) 12f else 0f
                                if (isDragging) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                }
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                    )
                }
                if (showAddButton) {
                    item {
                        AddPageItem(onClick = onAddPage)
                    }
                }
            }

            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(100.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = CSGreen, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Processing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PageItem(
    page: PageEntity,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount -> 
                        change.consume()
                        onDrag(amount) 
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, CSGreen) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imagePath = page.processedPath ?: page.originalPath
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isSelected) 0.6f else 1f),
                contentScale = ContentScale.Crop
            )

            // Page Number Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                color = if (isSelected) CSGreen else Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = index.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = CSGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            if (page.processedPath == null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun AddPageItem(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = CSGreen.copy(alpha = 0.1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = CSGreen,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Add Page",
                    color = CSGreen,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
