package com.swadeshiscanner.app.ui.screens

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.swadeshiscanner.app.PolygonView
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.ui.theme.CSGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CropScreen(
    currentBitmap: Bitmap?,
    thumbnails: List<String>,
    currentIndex: Int,
    filterName: String,
    filterIcon: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRotate: () -> Unit,
    onFilterClick: () -> Unit,
    onDelete: () -> Unit,
    onThumbClick: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onBatchFilter: (Int) -> Unit,
    onBatchDelete: (Set<Int>) -> Unit,
    onViewCreated: (ImageView, PolygonView) -> Unit
) {
    var draggedItemPath by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedThumbnails by remember { mutableStateOf(setOf<Int>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val reelState = rememberLazyListState()

    BackHandler(enabled = isSelectionMode || draggedItemPath != null) {
        if (isSelectionMode) isSelectionMode = false
        draggedItemPath = null
        dragOffset = Offset.Zero
    }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) selectedThumbnails = emptySet()
    }
    
    LaunchedEffect(thumbnails.size) {
        draggedItemPath = null
        dragOffset = Offset.Zero
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when {
                            isSelectionMode -> "${selectedThumbnails.size} Selected"
                            draggedItemPath != null -> "Moving..."
                            else -> "Crop & Rotate"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isSelectionMode) isSelectionMode = false
                        else if (draggedItemPath != null) draggedItemPath = null
                        else onBack() 
                    }) {
                        Icon(
                            if (draggedItemPath != null || isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { 
                            onBatchDelete(selectedThumbnails)
                            isSelectionMode = false 
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    } else {
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Select", tint = Color.White)
                        }
                        TextButton(onClick = onDone) {
                            Text("DONE", color = CSGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.8f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column {
                    // Thumbnails
                    LazyRow(
                        state = reelState,
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(thumbnails) { index, path ->
                            val isSelected = index == currentIndex
                            val isDragging = path == draggedItemPath
                            val isMultiSelected = selectedThumbnails.contains(index)

                            ThumbnailItem(
                                path = path,
                                isSelected = isSelected,
                                isDragging = isDragging,
                                isMultiSelected = isMultiSelected,
                                index = index + 1,
                                onClick = { 
                                    if (isSelectionMode) {
                                        selectedThumbnails = if (isMultiSelected) selectedThumbnails - index else selectedThumbnails + index
                                    } else {
                                        onThumbClick(index) 
                                    }
                                },
                                onDragStart = {
                                    if (!isSelectionMode) {
                                        draggedItemPath = path
                                        dragOffset = Offset.Zero
                                    }
                                },
                                onDrag = { delta ->
                                    if (draggedItemPath != null && !isSelectionMode) {
                                        dragOffset += delta
                                        val fromIndex = thumbnails.indexOf(draggedItemPath)
                                        if (fromIndex != -1) {
                                            if (Math.abs(dragOffset.x) > 80f) {
                                                val toIndex = if (dragOffset.x > 0) fromIndex + 1 else fromIndex - 1
                                                if (toIndex in thumbnails.indices) {
                                                    onMovePage(fromIndex, toIndex)
                                                    dragOffset = Offset.Zero
                                                }
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragOffset.getDistance() < 20f && draggedItemPath == path) {
                                        // Long press with no movement -> Selection Mode
                                        isSelectionMode = true
                                        selectedThumbnails = setOf(index)
                                    }
                                    draggedItemPath = null
                                    dragOffset = Offset.Zero
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedThumbnails = setOf(index)
                                    }
                                }
                            )
                        }
                    }

                    // Bottom Actions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            ActionIcon(R.drawable.ic_auto_fix, "Magic All", { onBatchFilter(1); isSelectionMode = false })
                            ActionIcon(R.drawable.ic_contrast, "Gray All", { onBatchFilter(2); isSelectionMode = false })
                            ActionIcon(R.drawable.ic_image, "Orig All", { onBatchFilter(0); isSelectionMode = false })
                        } else {
                            ActionIcon(R.drawable.ic_rotate_right, "Rotate", onRotate)
                           ActionIcon(filterIcon, filterName, onFilterClick)
                            ActionIcon(R.drawable.ic_delete, "Delete", onDelete)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val frame = FrameLayout(ctx)
                    val iv = ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        val p = (16 * ctx.resources.displayMetrics.density).toInt()
                        setPadding(p, p, p, p)
                    }
                    val poly = PolygonView(ctx).apply {
                        val p = (16 * ctx.resources.displayMetrics.density).toInt()
                        setPadding(p, p, p, p)
                    }
                    frame.addView(iv)
                    frame.addView(poly)
                    onViewCreated(iv, poly)
                    frame
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ActionIcon(iconRes: Int, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThumbnailItem(
    path: String,
    isSelected: Boolean,
    isDragging: Boolean,
    isMultiSelected: Boolean,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isMultiSelected -> CSGreen
                    isSelected -> CSGreen.copy(alpha = 0.4f)
                    isDragging -> Color.Yellow
                    else -> Color.Transparent
                }
            )
            .padding(if (isSelected || isDragging || isMultiSelected) 2.dp else 0.dp)
            .clip(RoundedCornerShape(6.dp))
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
            }
            .graphicsLayer {
                alpha = if (isDragging) 0.6f else 1.0f
                scaleX = if (isDragging) 1.1f else 1f
                scaleY = if (isDragging) 1.1f else 1f
            }
            .zIndex(if (isDragging) 1f else 0f)
    ) {
        AsyncImage(
            model = path,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isMultiSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CSGreen.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(index.toString(), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
