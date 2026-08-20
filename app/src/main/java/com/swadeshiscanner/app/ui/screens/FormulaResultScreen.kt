package com.swadeshiscanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.swadeshiscanner.app.ui.theme.CSGreen
import com.swadeshiscanner.app.ui.viewmodels.FormulaResultViewModel
import com.swadeshiscanner.app.utils.MathView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaResultScreen(
    viewModel: FormulaResultViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.equation)) }

    LaunchedEffect(uiState.equation) {
        if (textFieldValue.text != uiState.equation) {
            textFieldValue = TextFieldValue(uiState.equation, selection = TextRange(uiState.equation.length))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Formula Solver", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    AsyncImage(
                        model = uiState.imagePath,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = null // Ensure original colors
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF252525) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CSGreen.copy(alpha = 0.3f)),
                    shadowElevation = 8.dp
                ) {
                    AndroidView(
                        factory = { ctx -> 
                            MathView(ctx).apply { 
                                setFormula(uiState.formula) 
                            } 
                        },
                        update = { 
                            it.setFormula(uiState.formula) 
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CSGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Result:", color = CSGreen, fontWeight = FontWeight.Bold)
                    Text(
                        text = uiState.solution,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = uiState.solutionColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    )
                }

                Text(
                    text = "Edit Equation",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CSGreen,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    shadowElevation = 2.dp
                ) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.onEquationChanged(it.text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace, 
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = CSGreen
                        ),
                        placeholder = { Text("Scanning...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                    )
                }
            }

            // Enhanced Math Keyboard
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                    val rows = listOf(
                        listOf("(", ")", "x²", "^", "√"),
                        listOf("+", "-", "×", "÷", "π")
                    )
                    
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                KeyButton(key, Modifier.weight(1f)) {
                                    val insert = when(key) { "x²" -> "^2"; "π" -> "pi"; else -> key }
                                    val pos = viewModel.insertTextAtCursor(insert, textFieldValue.selection.start, textFieldValue.selection.end)
                                    textFieldValue = textFieldValue.copy(selection = TextRange(pos))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pos = viewModel.backspace(textFieldValue.selection.start, textFieldValue.selection.end)
                                textFieldValue = textFieldValue.copy(selection = TextRange(pos))
                            },
                            modifier = Modifier.weight(0.4f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Text("DEL")
                        }
                        Button(
                            onClick = viewModel::copyToClipboard,
                            modifier = Modifier.weight(0.6f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CSGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("COPY RESULT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (uiState.isLoading) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.3f)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CSGreen)
            }
        }
    }
}

@Composable
fun KeyButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}
