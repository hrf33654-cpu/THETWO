package com.thetwo.app.summon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thetwo.app.R
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.media.BitmapSaver
import com.thetwo.app.session.AppSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SummonScreen(
    viewModel: SummonViewModel,
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val uiState by remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }
    val sessionState by remember { androidx.compose.runtime.derivedStateOf { sessionViewModel.uiState } }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val companionName = sessionState.companionProfile?.nickname ?: "飞樱"
    val hasCameraPermission = rememberCameraPermission()
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var scale by rememberSaveable { mutableStateOf(1f) }
    var rotation by rememberSaveable { mutableStateOf(0f) }
    var captureCount by rememberSaveable { mutableIntStateOf(0) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.showCameraPreviewFallback()
        } else {
            viewModel.showScreenOnlyFallback()
            viewModel.setStatusMessage("未授予相机权限，已回退到纯屏模式。")
        }
    }

    val arServicesInstalled = remember(context) { isPackageInstalled(context.packageManager, "com.google.ar.core") }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            cameraController.unbind()
        }
    }

    androidx.compose.runtime.LaunchedEffect(sessionState.arPrivacyAccepted, hasCameraPermission, arServicesInstalled) {
        viewModel.setArServicesInstalled(arServicesInstalled)
        when {
            !sessionState.arPrivacyAccepted -> viewModel.requirePrivacyAck()
            hasCameraPermission -> viewModel.showCameraPreviewFallback()
            else -> viewModel.showCameraPermissionRequired()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "召唤页",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前先走 fallback 主路径，把相机预览、纯屏降级、截图保存和作品回流打通。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Google Play Services for AR：${if (uiState.arServicesInstalled) "已检测到安装包" else "未检测到安装包"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!uiState.arServicesInstalled) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { openArServicesPage(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开 Google Play Services for AR 安装页")
                }
            }
            Text(
                text = "锚点图：${uiState.markerAsset.name} · ${uiState.markerAsset.resourceName}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF121629), Color(0xFF25304F), Color(0xFF4D759B)),
                        ),
                    )
                    .onSizeChanged { stageSize = it },
            ) {
                when (uiState.entryState) {
                    SummonEntryState.CAMERA_PREVIEW_FALLBACK -> {
                        AndroidView(
                            factory = { viewContext ->
                                PreviewView(viewContext).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    controller = cameraController
                                }.also { previewView = it }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.controller = cameraController
                                previewView = view
                            },
                        )
                    }

                    SummonEntryState.CAMERA_PERMISSION_REQUIRED,
                    SummonEntryState.SCREEN_ONLY_FALLBACK,
                    SummonEntryState.PRIVACY_REQUIRED,
                    -> Unit
                }

                if (uiState.entryState != SummonEntryState.CAMERA_PREVIEW_FALLBACK) {
                    CompanionFallbackBackdrop()
                }

                Image(
                    painter = painterResource(id = R.drawable.ar_marker_fantasy_v1),
                    contentDescription = "官方锚点图",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(88.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )

                CompanionOverlayCard(
                    companionName = companionName,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            rotationZ = rotation,
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotationChange ->
                                offsetX += pan.x
                                offsetY += pan.y
                                scale = (scale * zoom).coerceIn(0.75f, 2.2f)
                                rotation += rotationChange
                            }
                        },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        text = when (uiState.entryState) {
                            SummonEntryState.PRIVACY_REQUIRED -> "等待隐私确认"
                            SummonEntryState.CAMERA_PERMISSION_REQUIRED -> "等待相机权限"
                            SummonEntryState.CAMERA_PREVIEW_FALLBACK -> "相机预览 fallback"
                            SummonEntryState.SCREEN_ONLY_FALLBACK -> "纯屏 fallback"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "拖动、缩放、旋转角色卡片，然后保存当前召唤画面。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            uiState.statusMessage?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.entryState == SummonEntryState.CAMERA_PERMISSION_REQUIRED || uiState.entryState == SummonEntryState.SCREEN_ONLY_FALLBACK) {
                    OutlinedButton(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("请求相机")
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::showScreenOnlyFallback,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("纯屏模式")
                    }
                    OutlinedButton(
                        onClick = {
                            if (hasCameraPermission) {
                                viewModel.showCameraPreviewFallback()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("相机预览")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.setSavingCapture(true)
                            viewModel.setStatusMessage(null)
                            val bitmap = createCaptureBitmap(
                                previewBitmap = previewView?.bitmap,
                                stageSize = stageSize,
                                companionName = companionName,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scale = scale,
                                rotation = rotation,
                            )
                            val saveResult = withContext(Dispatchers.IO) {
                                BitmapSaver.saveCapture(context, bitmap)
                            }
                            saveResult
                                .onSuccess { location ->
                                    captureCount += 1
                                    val reference = RecentCaptureReference(
                                        title = "最近一次召唤 #$captureCount",
                                        summary = "你把 $companionName 放进了现实画面里，并保存了当前召唤截图。",
                                        storageLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            "已保存到系统相册：$location"
                                        } else {
                                            "已保存到应用图片目录：$location"
                                        },
                                    )
                                    viewModel.syncRecentCapture(
                                        authSession = sessionState.authSession,
                                        reference = reference,
                                        onSuccess = { savedReference ->
                                            sessionViewModel.setRecentCapture(savedReference)
                                            chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                            onBack()
                                        },
                                        onUnauthorized = onUnauthorized,
                                    )
                                }
                                .onFailure { error ->
                                    viewModel.setSavingCapture(false)
                                    viewModel.setStatusMessage(error.message ?: "截图保存失败")
                                }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSavingCapture && sessionState.arPrivacyAccepted,
                ) {
                    Text(if (uiState.isSavingCapture) "保存中..." else "保存召唤截图")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("返回聊天")
                }
            }

            uiState.pendingRecentCapture?.let { pendingReference ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.syncRecentCapture(
                            authSession = sessionState.authSession,
                            reference = pendingReference,
                            onSuccess = { savedReference ->
                                sessionViewModel.setRecentCapture(savedReference)
                                chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                onBack()
                            },
                            onUnauthorized = onUnauthorized,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSavingCapture,
                ) {
                    Text("重试作品回流同步")
                }
            }
        }
    }

    if (!sessionState.arPrivacyAccepted) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(
                    onClick = {
                        sessionViewModel.acceptArPrivacy()
                        if (hasCameraPermission) {
                            viewModel.showCameraPreviewFallback()
                        } else {
                            viewModel.showCameraPermissionRequired()
                        }
                    },
                ) {
                    Text("我知道了")
                }
            },
            title = { Text("相机与 AR 说明") },
            text = {
                Text(
                    "当前召唤页会使用相机画面生成角色召唤截图，不做房间建模。你保存的截图会写入系统相册或应用图片目录，后续可在设置里清除 App 内最近作品回流记录。",
                )
            },
        )
    }
}

@Composable
private fun CompanionFallbackBackdrop() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF5E7CE2), Color(0xFF121629)),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(1.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "纯屏 fallback 已启用",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "没有相机权限或不使用相机时，仍然可以摆放角色并保存召唤截图。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CompanionOverlayCard(
    companionName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(width = 168.dp, height = 220.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF172038), Color(0xFF2F4D78), Color(0xFF8BD6FF)),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "THETWO",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            Column {
                Text(
                    text = companionName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = "已召唤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(
                text = "拖动 / 缩放 / 旋转",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun rememberCameraPermission(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun isPackageInstalled(
    packageManager: PackageManager,
    packageName: String,
): Boolean = runCatching {
    packageManager.getPackageInfo(packageName, 0)
}.isSuccess

private fun openArServicesPage(context: android.content.Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.ar.core"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(marketIntent) }
        .onFailure { context.startActivity(webIntent) }
}

private fun createCaptureBitmap(
    previewBitmap: Bitmap?,
    stageSize: IntSize,
    companionName: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotation: Float,
): Bitmap {
    val baseBitmap = previewBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        ?: Bitmap.createBitmap(
            stageSize.width.coerceAtLeast(1080),
            stageSize.height.coerceAtLeast(1440),
            Bitmap.Config.ARGB_8888,
        )

    val canvas = Canvas(baseBitmap)
    if (previewBitmap == null) {
        val backgroundPaint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                baseBitmap.width.toFloat(),
                baseBitmap.height.toFloat(),
                intArrayOf(
                    android.graphics.Color.parseColor("#14182B"),
                    android.graphics.Color.parseColor("#35557A"),
                    android.graphics.Color.parseColor("#6FCFFE"),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, baseBitmap.width.toFloat(), baseBitmap.height.toFloat(), backgroundPaint)
    }

    drawCompanionOverlay(
        canvas = canvas,
        bitmapWidth = baseBitmap.width,
        bitmapHeight = baseBitmap.height,
        stageSize = stageSize,
        companionName = companionName,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        rotation = rotation,
    )
    return baseBitmap
}

private fun drawCompanionOverlay(
    canvas: Canvas,
    bitmapWidth: Int,
    bitmapHeight: Int,
    stageSize: IntSize,
    companionName: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotation: Float,
) {
    val stageWidth = stageSize.width.coerceAtLeast(bitmapWidth)
    val stageHeight = stageSize.height.coerceAtLeast(bitmapHeight)
    val cardWidth = 320f * scale
    val cardHeight = 420f * scale
    val centerX = bitmapWidth / 2f + offsetX * bitmapWidth / stageWidth
    val centerY = bitmapHeight / 2f + offsetY * bitmapHeight / stageHeight
    val rect = RectF(
        centerX - cardWidth / 2f,
        centerY - cardHeight / 2f,
        centerX + cardWidth / 2f,
        centerY + cardHeight / 2f,
    )

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                android.graphics.Color.parseColor("#18233D"),
                android.graphics.Color.parseColor("#38628A"),
                android.graphics.Color.parseColor("#8ADFFF"),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 34f * scale
        isFakeBoldText = true
    }
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 54f * scale
        isFakeBoldText = true
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 255, 255, 255)
        textSize = 28f * scale
    }

    canvas.save()
    canvas.rotate(rotation, centerX, centerY)
    canvas.drawRoundRect(rect, 44f * scale, 44f * scale, cardPaint)
    canvas.drawRoundRect(rect, 44f * scale, 44f * scale, glowPaint)
    canvas.drawText("THETWO", rect.left + 26f * scale, rect.top + 52f * scale, titlePaint)
    canvas.drawText(companionName, rect.left + 26f * scale, rect.centerY(), namePaint)
    canvas.drawText("已召唤", rect.left + 26f * scale, rect.centerY() + 44f * scale, labelPaint)
    canvas.drawText("Captured in reality", rect.left + 26f * scale, rect.bottom - 32f * scale, labelPaint)
    canvas.restore()
}
