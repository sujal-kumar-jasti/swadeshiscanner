package com.swadeshiscanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.theme.CSGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocBatchEditorScreen(
    pages: List<PageEntity>,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onFilterClick: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Batch Editor (${pages.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = onDone) {
                            Text("DONE", color = CSGreen, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatchActionButton(
                        icon = R.drawable.ic_rotate_left,
                        label = "Left",
                        onClick = onRotateLeft
                    )
                    BatchActionButton(
                        icon = R.drawable.ic_rotate_right,
                        label = "Right",
                        onClick = onRotateRight
                    )
                    BatchActionButton(
                        icon = R.drawable.ic_folder_move,
                        label = "Move",
                        onClick = onMove
                    )
                    BatchActionButton(
                        icon = R.drawable.ic_content_copy,
                        label = "Copy",
                        onClick = onCopy
                    )
                    BatchActionButton(
                        icon = R.drawable.ic_tune_filter,
                        label = "Filter",
                        onClick = onFilterClick
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pages) { index, page ->
                    PageItem(
                        page = page,
                        index = index + 1,
                        isSelected = false,
                        onClick = { },
                        onLongClick = { }
                    )
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
                                Text("Wait", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = Color(0xFF555555),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF555555))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFilterBottomSheet(
    onFilterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                "Select Batch Filter",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            FilterOptionItem(label = "Magic Color", icon = R.drawable.ic_auto_fix, onClick = { onFilterSelected(1) })
            FilterOptionItem(label = "Grayscale", icon = R.drawable.ic_contrast, onClick = { onFilterSelected(2) })
            FilterOptionItem(label = "Original", icon = R.drawable.ic_image, onClick = { onFilterSelected(0) })
        }
    }
}

@Composable
fun FilterOptionItem(label: String, icon: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(id = icon), contentDescription = null, tint = CSGreen)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}
