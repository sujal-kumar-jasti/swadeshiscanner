package com.swadeshiscanner.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.database.SignatureEntity
import com.swadeshiscanner.app.ui.theme.CSGreen
import kotlin.math.roundToInt
import com.swadeshiscanner.app.ui.components.SignatureDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureAdjustScreen(
    pagePath: String?,
    signatures: List<SignatureEntity>,
    onCancel: () -> Unit,
    onRotate: () -> Unit,
    onSave: (SignatureEntity?, Float, Float, androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize, androidx.compose.ui.unit.IntSize) -> Unit,
    onSaveDrawnSignature: (android.graphics.Bitmap) -> Unit,
    onExtractFromImage: (android.net.Uri) -> Unit,
    onAddDate: () -> Unit,
    onDeleteSignature: (SignatureEntity) -> Unit
) {
    var signatureScale by remember { mutableFloatStateOf(1f) }
    var signatureRotation by remember { mutableFloatStateOf(0f) }
    var signatureOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset(0f, 0f)) }
    var selectedSignature by remember { mutableStateOf<SignatureEntity?>(null) }
    var showDrawDialog by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var sigBoxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val extractLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onExtractFromImage(it) }
    }

    if (showDrawDialog) {
        SignatureDialog(
            onDismiss = { showDrawDialog = false },
            onSave = {
                onSaveDrawnSignature(it)
                showDrawDialog = false
            }
        )
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shadowElevation = 8.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Add Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Cancel",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRotate) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_rotate_right),
                                contentDescription = "Rotate",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { 
                            if (selectedSignature != null) {
                                onSave(selectedSignature, signatureScale, signatureRotation, signatureOffset, imageSize, sigBoxSize)
                            } else {
                                onCancel()
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Save",
                                tint = CSGreen
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
                color = Color.Black.copy(alpha = 0.9f),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Your Signatures",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(signatures) { sig ->
                            SignatureItem(
                                signature = sig,
                                onClick = { selectedSignature = sig },
                                onDelete = { onDeleteSignature(sig) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SignatureBottomButton(
                            icon = R.drawable.ic_image,
                            text = "Import",
                            onClick = { extractLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )
                        SignatureBottomButton(
                            icon = Icons.Default.Add,
                            text = "Draw",
                            onClick = { showDrawDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                        SignatureBottomButton(
                            icon = R.drawable.ic_calendar,
                            text = "Date",
                            onClick = onAddDate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { imageSize = it.size }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        if (selectedSignature != null) {
                            signatureScale = (signatureScale * zoom).coerceIn(0.2f, 5f)
                            signatureRotation += rotation
                            signatureOffset += pan
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Image
            if (pagePath != null) {
                val bitmap = remember(pagePath) { BitmapFactory.decodeFile(pagePath) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Signature Overlay
            if (selectedSignature != null) {
                val sigBitmap = remember(selectedSignature) { 
                    val f = File(selectedSignature!!.path)
                    if (f.exists()) BitmapFactory.decodeFile(selectedSignature!!.path) else null
                }
                if (sigBitmap != null) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer(
                                translationX = signatureOffset.x,
                                translationY = signatureOffset.y,
                                scaleX = signatureScale,
                                scaleY = signatureScale,
                                rotationZ = signatureRotation
                            )
                            .border(1.dp, CSGreen, RoundedCornerShape(4.dp))
                            .onGloballyPositioned { sigBoxSize = it.size }
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = sigBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(150.dp, 75.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Remove Button (Top Left) - Compensate for scale/rotation
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-12).dp, y = (-12).dp)
                                .graphicsLayer {
                                    rotationZ = -signatureRotation
                                    scaleX = 1f / signatureScale.coerceAtLeast(0.1f)
                                    scaleY = 1f / signatureScale.coerceAtLeast(0.1f)
                                }
                                .size(24.dp)
                                .background(Color.Red, CircleShape)
                                .clickable { selectedSignature = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                        }

                        // Resize Handle (Bottom Right)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 12.dp, y = 12.dp)
                                .graphicsLayer {
                                    rotationZ = -signatureRotation
                                    scaleX = 1f / signatureScale.coerceAtLeast(0.1f)
                                    scaleY = 1f / signatureScale.coerceAtLeast(0.1f)
                                }
                                .size(24.dp)
                                .background(CSGreen, CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_resize),
                                contentDescription = "Resize",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Drag to move • Pinch to resize/rotate",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .alpha(0.7f)
            )
        }
    }
}

@Composable
fun SignatureBottomButton(
    icon: Any,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() },
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (icon) {
                is Int -> Icon(painter = painterResource(id = icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                is androidx.compose.ui.graphics.vector.ImageVector -> Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun SignatureItem(
    signature: SignatureEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color(0xFF222222), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        val bitmap = remember(signature.path) { 
            val f = File(signature.path)
            if (f.exists()) BitmapFactory.decodeFile(signature.path) else null
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(20.dp)
                .background(Color.Red, CircleShape)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
