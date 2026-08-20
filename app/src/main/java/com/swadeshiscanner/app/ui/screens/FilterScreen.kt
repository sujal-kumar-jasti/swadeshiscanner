package com.swadeshiscanner.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.ui.theme.CSGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    previewBitmap: Bitmap?,
    currentFilterId: Int,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onFilterSelected: (Int) -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Apply Filter",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = CSGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    val filters = listOf(
                        FilterItemData(0, "Original", R.drawable.ic_image),
                        FilterItemData(1, "Magic", R.drawable.ic_auto_fix),
                        FilterItemData(2, "Gray", R.drawable.ic_contrast)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        items(filters) { filter ->
                            FilterItem(
                                data = filter,
                                isSelected = currentFilterId == filter.id,
                                onClick = { onFilterSelected(filter.id) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(color = CSGreen)
            }

            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CSGreen)
                    }
                }
            }
        }
    }
}

data class FilterItemData(val id: Int, val name: String, val icon: Int)

@Composable
fun FilterItem(
    data: FilterItemData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.Transparent else Color(0xFF333333))
                .border(
                    width = 2.dp,
                    color = if (isSelected) CSGreen else Color.Transparent,
                    shape = CircleShape
                )
                .padding(if (isSelected) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(if (isSelected) CSGreen.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = data.icon),
                contentDescription = data.name,
                tint = if (isSelected) CSGreen else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.name,
            color = if (isSelected) CSGreen else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
