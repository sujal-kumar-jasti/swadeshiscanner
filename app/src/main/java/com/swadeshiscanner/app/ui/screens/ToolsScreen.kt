package com.swadeshiscanner.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.CameraActivity
import com.swadeshiscanner.app.IdCardActivity
import com.swadeshiscanner.app.activities.*

data class ToolData(val name: String, val icon: Int, val color: Color, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    var pendingConversionType by remember { mutableStateOf<String?>(null) }
    
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val intent = Intent(context, ConverterActivity::class.java).apply {
                putExtra("file_uri", it.toString())
                putExtra("conversion_type", pendingConversionType)
            }
            context.startActivity(intent)
        }
        pendingConversionType = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item { ToolSection("Scan & Extract") }
            item {
                ToolGrid(listOf(
                    ToolData("ID Cards", R.drawable.ic_id_card, Color(0xFF00BCD4)) { 
                        context.startActivity(Intent(context, IdCardActivity::class.java)) 
                    },
                    ToolData("Extract Text", R.drawable.ic_text, Color(0xFFFFAB40)) { 
                        val intent = Intent(context, CameraActivity::class.java).apply {
                            putExtra("is_ocr_mode", true)
                        }
                        context.startActivity(intent)
                    },
                    ToolData("ID Photo", R.drawable.ic_person, Color(0xFF448AFF)) { 
                        context.startActivity(Intent(context, PassportPhotoActivity::class.java)) 
                    },
                    ToolData("Formula", R.drawable.ic_formula, Color(0xFF00C895)) { 
                        val intent = Intent(context, CameraActivity::class.java).apply {
                            putExtra("is_formula_mode", true)
                        }
                        context.startActivity(intent)
                    },
                    ToolData("Translate", R.drawable.ic_translate, Color(0xFF7C4DFF)) { 
                        val intent = Intent(context, CameraActivity::class.java).apply {
                            putExtra("is_translate_mode", true)
                        }
                        context.startActivity(intent)
                    },
                    ToolData("Book", R.drawable.ic_book, Color(0xFF4CAF50)) { 
                        val intent = Intent(context, CameraActivity::class.java).apply {
                            putExtra("is_book_mode", true)
                        }
                        context.startActivity(intent)
                    }
                ))
            }

            item { ToolSection("PDF Conversion") }
            item {
                ToolGrid(listOf(
                    ToolData("To Word", R.drawable.ic_word, Color(0xFF42A5F5)) { 
                        pendingConversionType = "WORD"
                        filePickerLauncher.launch("application/pdf")
                    },
                    ToolData("To Excel", R.drawable.ic_excel, Color(0xFF66BB6A)) { 
                        pendingConversionType = "EXCEL"
                        filePickerLauncher.launch("application/pdf")
                    },
                    ToolData("To PPT", R.drawable.ic_ppt, Color(0xFFFFA726)) { 
                        pendingConversionType = "PPT"
                        filePickerLauncher.launch("application/pdf")
                    },
                    ToolData("Word to PDF", R.drawable.ic_word, Color(0xFF1E88E5)) { 
                        pendingConversionType = "WORD_TO_PDF"
                        filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    },
                    ToolData("Excel to PDF", R.drawable.ic_excel, Color(0xFF43A047)) { 
                        pendingConversionType = "EXCEL_TO_PDF"
                        filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    },
                    ToolData("PPT to PDF", R.drawable.ic_ppt, Color(0xFFF4511E)) { 
                        pendingConversionType = "PPT_TO_PDF"
                        filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                    }
                ))
            }
        }
    }
}

@Composable
fun ToolSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        letterSpacing = 1.sp
    )
}

@Composable
fun ToolGrid(tools: List<ToolData>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        tools.chunked(3).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowTools.forEach { tool ->
                    ToolItemCard(tool, Modifier.weight(1f))
                }
                repeat(3 - rowTools.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolItemCard(tool: ToolData, modifier: Modifier = Modifier) {
    Card(
        onClick = tool.onClick,
        modifier = modifier.height(125.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = tool.color.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, tool.color.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val useOriginalColor = tool.name.contains("Word", ignoreCase = true) || 
                                         tool.name.contains("Excel", ignoreCase = true) || 
                                         tool.name.contains("PPT", ignoreCase = true) || 
                                         tool.name.contains("PDF", ignoreCase = true)
                    
                    Icon(
                        painter = painterResource(id = tool.icon),
                        contentDescription = null,
                        tint = if (useOriginalColor) Color.Unspecified else tool.color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = tool.name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
        }
    }
}
