package com.swadeshiscanner.app.ui.screens

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.ui.theme.CSGreen

@Composable
fun PassportPhotoScreen(
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onRotate: () -> Unit,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onPreviewViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay
        PassportOverlay(aspectRatio = 7f / 9f)

        // Top Controls
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .size(40.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Bottom Controls
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRotate,
                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_rotate_right), contentDescription = "Rotate", tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onCapture() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .border(2.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun PassportOverlay(aspectRatio: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val boxWidth = w * 0.7f
        val boxHeight = boxWidth / aspectRatio
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
