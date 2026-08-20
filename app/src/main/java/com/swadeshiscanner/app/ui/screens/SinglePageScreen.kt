package com.swadeshiscanner.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.chrisbanes.photoview.PhotoView
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.database.PageEntity
import com.swadeshiscanner.app.ui.theme.CSGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePageScreen(
    currentPage: PageEntity?,
    currentIndex: Int,
    isLoading: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onExtract: () -> Unit,
    onFilter: () -> Unit,
    onSign: () -> Unit,
    onNote: () -> Unit
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
                            text = "Page ${currentIndex + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_arrow),
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onShare) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share_white),
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
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
                        .height(72.dp)
                        .background(MaterialTheme.colorScheme.surface), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomActionItem(
                        iconRes = R.drawable.ic_rotate_right, // Fixed icon name
                        label = "Rotate",
                        onClick = onRotate,
                        modifier = Modifier.weight(1f)
                    )
                    BottomActionItem(
                        iconRes = R.drawable.ic_crop,
                        label = "Crop",
                        onClick = onCrop,
                        modifier = Modifier.weight(1f)
                    )
                    BottomActionItem(
                        iconRes = R.drawable.ic_text,
                        label = "Extract",
                        onClick = onExtract,
                        modifier = Modifier.weight(1f)
                    )
                    BottomActionItem(
                        iconRes = R.drawable.ic_auto_fix,
                        label = "Filter",
                        onClick = onFilter,
                        modifier = Modifier.weight(1f)
                    )
                    BottomActionItem(
                        iconRes = R.drawable.ic_signature,
                        label = "Sign",
                        onClick = onSign,
                        modifier = Modifier.weight(1f)
                    )
                    BottomActionItem(
                        iconRes = R.drawable.ic_note_add,
                        label = "Note",
                        onClick = onNote,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .fillMaxSize()
                .background(Color.Black), 
            contentAlignment = Alignment.Center
        ) {
            if (currentPage != null) {
                AndroidView(
                    factory = { context ->
                        PhotoView(context).apply {
                            val path = currentPage.processedPath ?: currentPage.originalPath
                            val bitmap = BitmapFactory.decodeFile(path)
                            setImageBitmap(bitmap)
                            // Remove any tint or filters applied to the view
                            colorFilter = null
                        }
                    },
                    update = { view ->
                        val path = currentPage.processedPath ?: currentPage.originalPath
                        val bitmap = BitmapFactory.decodeFile(path)
                        view.setImageBitmap(bitmap)
                        view.colorFilter = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isLoading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}


@Composable
fun BottomActionItem(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
