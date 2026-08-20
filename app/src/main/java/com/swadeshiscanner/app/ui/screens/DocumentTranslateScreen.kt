package com.swadeshiscanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.ui.theme.CSGreen
import com.swadeshiscanner.app.ui.viewmodels.DocumentTranslateViewModel
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTranslateScreen(
    viewModel: DocumentTranslateViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Translate", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    AsyncImage(
                        model = uiState.imagePath,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = null // Removed tint
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selection Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        LanguageSpinner(
                            label = uiState.languageNames.getOrNull(uiState.sourceLanguageIndex) ?: "Source",
                            options = uiState.languageNames,
                            onSelection = viewModel::onSourceLangChanged,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync),
                            contentDescription = null,
                            tint = CSGreen,
                            modifier = Modifier.size(32.dp).padding(horizontal = 8.dp)
                        )
                        LanguageSpinner(
                            label = uiState.languageNames.getOrNull(uiState.targetLanguageIndex) ?: "Target",
                            options = uiState.languageNames,
                            onSelection = viewModel::onTargetLangChanged,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TabRow(
                    selectedTabIndex = uiState.currentTab,
                    containerColor = Color.Transparent,
                    contentColor = CSGreen,
                    divider = {},
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[uiState.currentTab]),
                            color = CSGreen,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = uiState.currentTab == 0,
                        onClick = { viewModel.onTabSelected(0) },
                        text = { 
                            Text(
                                "Original", 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (uiState.currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.currentTab == 0) CSGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    )
                    Tab(
                        selected = uiState.currentTab == 1,
                        onClick = { viewModel.onTabSelected(1) },
                        text = { 
                            Text(
                                "Translation", 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (uiState.currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.currentTab == 1) CSGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        TextField(
                            value = uiState.selectedTabText,
                            onValueChange = viewModel::onTextChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 250.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = CSGreen
                            ),
                            placeholder = { Text("Text will appear here...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = viewModel::copyToClipboard) {
                                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = viewModel::shareText) {
                                Icon(painterResource(R.drawable.ic_share_white), contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::checkAndTranslate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CSGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Translate Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (uiState.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CSGreen, strokeWidth = 4.dp)
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    uiState.loadingMessage,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    if (uiState.showDownloadDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDownloadDialog,
            title = { Text("Download Language?") },
            text = { Text("Translation for ${uiState.downloadLangName} requires a download (approx. 30MB).") },
            confirmButton = {
                TextButton(onClick = viewModel::performTranslation) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDownloadDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LanguageSpinner(
    label: String,
    options: List<String>,
    onSelection: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.6f).heightIn(max = 300.dp).background(MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            options.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelection(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
