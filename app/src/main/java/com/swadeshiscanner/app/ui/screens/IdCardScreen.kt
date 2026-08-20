package com.swadeshiscanner.app.ui.screens

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.ui.theme.CSGreen

enum class IdCardState { SUMMARY, CAMERA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardScreen(
    state: IdCardState,
    frontPath: String?,
    backPath: String?,
    onBack: () -> Unit,
    onCardClick: (Int) -> Unit,
    onDeleteFront: () -> Unit,
    onDeleteBack: () -> Unit,
    onGenerate: () -> Unit,
    onCapture: () -> Unit,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    if (state == IdCardState.CAMERA) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        onPreviewViewCreated(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IdCardOverlay()

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp)
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onCapture() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .border(2.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                        .background(Color.White, CircleShape)
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    TopAppBar(
                        title = { Text("ID Card Maker", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Button(
                        onClick = onGenerate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CSGreen),
                        enabled = frontPath != null && backPath != null
                    ) {
                        Text("Generate ID Card", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IdCardSection(
                    title = "Front Side",
                    imagePath = frontPath,
                    onCapture = { onCardClick(0) },
                    onDelete = onDeleteFront
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                IdCardSection(
                    title = "Back Side",
                    imagePath = backPath,
                    onCapture = { onCardClick(1) },
                    onDelete = onDeleteBack
                )
            }
        }
    }
}

@Composable
fun IdCardSection(
    title: String,
    imagePath: String?,
    onCapture: () -> Unit,
    onDelete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        
        Card(
            modifier = Modifier
                .width(300.dp)
                .height(190.dp)
                .clickable { if (imagePath == null) onCapture() },
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (imagePath != null) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE0E0E0)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = null,
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Tap to Scan $title",
                            color = Color(0xFF757575),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IdCardOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val boxWidth = w * 0.9f
        val boxHeight = boxWidth * 0.63f
        val left = (w - boxWidth) / 2
        val top = (h - boxHeight) / 2

        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                color = android.graphics.Color.parseColor("#99000000")
                style = android.graphics.Paint.Style.FILL
            }
            val holePaint = Paint().asFrameworkPaint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val borderPaint = Paint().asFrameworkPaint().apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 5f
            }

            canvas.nativeCanvas.saveLayer(0f, 0f, w, h, null)
            canvas.nativeCanvas.drawRect(0f, 0f, w, h, paint)
            canvas.nativeCanvas.drawRect(left, top, left + boxWidth, top + boxHeight, holePaint)
            canvas.nativeCanvas.drawRect(left, top, left + boxWidth, top + boxHeight, borderPaint)
            canvas.nativeCanvas.restore()
        }
    }
}
